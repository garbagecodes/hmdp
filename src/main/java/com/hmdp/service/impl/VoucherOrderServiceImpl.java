package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.OrderPaymentDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.CommonVoucher;
import com.hmdp.entity.Event;
import com.hmdp.entity.LimitVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.event.KafkaOrderProducer;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ICommonVoucherService;
import com.hmdp.service.ILimitVoucherService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.KafkaConstants.TOPIC_CREATE_ORDER;
import static com.hmdp.utils.KafkaConstants.TOPIC_SAVE_ORDER_FAILED;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.SystemConstants.MAX_BUY_LIMIT;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private KafkaOrderProducer kafkaOrderProducer;
    @Resource
    private ICommonVoucherService commonVoucherService;
    @Resource
    private ILimitVoucherService limitVoucherService;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("获取用户ID锁失败！用户正在下单...");
            return;
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > MAX_BUY_LIMIT) {
                log.error("超过最大购买限制!");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - " + voucherOrder.getBuyNumber()) // set stock = stock - buynumber
                    .eq("voucher_id", voucherId)
                    .gt("stock", voucherOrder.getBuyNumber()) // where id = ? and stock > buynumber
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            voucherOrder.setCreateTime(LocalDateTime.now());
            voucherOrder.setUpdateTime(LocalDateTime.now());
            if (!save(voucherOrder)) {
                log.info("保存订单失败");
                throw new Exception("保存订单失败");
            }
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("voucherId", voucherId);
            data.put("buyNumber", voucherOrder.getBuyNumber());
            Event event = new Event()
                    .setTopic(TOPIC_SAVE_ORDER_FAILED)
                    .setUserId(userId)
                    .setEntityId(voucherOrder.getId())
                    .setData(data);
            kafkaOrderProducer.publishEvent(event);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId, int buyNumber) {
        Long userId = UserHolder.getUser().getId();
        long currentTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long orderId = redisIdWorker.nextId("order");
        try {
            // 1.执行lua脚本
            Long result = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString(),
                    String.valueOf(orderId),
                    String.valueOf(currentTime),
                    String.valueOf(buyNumber),
                    String.valueOf(MAX_BUY_LIMIT)
            );

            switch (result.intValue()) {
                case 0:
                    // 2.秒杀成功，发送消息到kafka
                    sendOrderMsgToKafka(orderId, voucherId, userId, buyNumber);
                    // 返回订单id
                    return Result.ok(orderId);
                case 1:
                    // TODO 获取锁，读取 mysql 数据存放到 Redis 中，然后递归调用本函数
                    return Result.fail("redis缺少数据");
                case 2:
                    return Result.fail("秒杀尚未开始");
                case 3:
                    return Result.fail("秒杀已经结束");
                case 4:
                    return Result.fail("库存不足");
                case 5:
                    return Result.fail("超过最大购买限制");
                default:
                    return Result.fail("未知错误");
            }
        } catch (Exception e) {
            log.error("处理订单异常", e);
            return Result.fail("未知错误");
        }
    }

    public void sendOrderMsgToKafka(long orderId, Long voucherId, Long userId, int buyNumber) {
        Map<String, Object> data = new HashMap<>();
        data.put("voucherId", voucherId);
        data.put("buyNumber", buyNumber);
        Event event = new Event()
                .setTopic(TOPIC_CREATE_ORDER)
                .setUserId(userId)
                .setEntityId(orderId)
                .setData(data);
        kafkaOrderProducer.publishEvent(event);
    }

    @Override
    public Result payment(OrderPaymentDTO orderPaymentDTO) {
        Long orderId = orderPaymentDTO.getOrderId();
        Integer payType = orderPaymentDTO.getPayType();
        // 1.查询redis中是否存在此订单
        boolean isRedisExist = redisTemplate.opsForSet().isMember(SECKILL_ORDER_KEY, orderId);
        // 2.查询mysql中是否有此订单
        Long userId = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = this.getOne(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getId, orderId));

        if (isRedisExist) {
            // 2. 秒杀订单业务流程
            if (voucherOrder == null) {
                //2.1 数据库不存在订单，等待后重试
                try {
                    Thread.sleep(1000);
                    return payment(orderPaymentDTO);
                } catch (Exception e) {
                    return Result.fail("未知错误");
                }
            }
        } else {
            // 3.普通、限购订单业务流程
            if (voucherOrder == null) {
                //3.1 数据库不存在订单，返回错误信息
                return Result.fail("订单不存在");
            }
        }
        // 此时已经在mysql查询到订单信息
        // TODO 3.进入付款流程
        return Result.ok();
    }

    @Override
    public Result commonVoucher(Long voucherId, int buyNumber) {
        // 1.查询优惠券
        CommonVoucher voucher = commonVoucherService.getById(voucherId);

        // 2.判断库存是否充足
        if (voucher.getStock() < buyNumber) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        //3. 乐观锁扣减库存
        boolean success = commonVoucherService.update()
                .setSql("stock= stock -" + buyNumber)
                .eq("voucher_id", voucherId)
                .ge("stock", buyNumber)
                .update(); //where id = ? and stock >= buyNumber
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }
        //4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 4.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 4.2.用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        // 4.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public Result limitVoucher(Long voucherId, int buyNumber) {
        return null;
    }

    @Override
    @Transactional
    public Result limitVoucher1(Long voucherId, int buyNumber) {

        Long userId = UserHolder.getUser().getId();
        // 1.查询优惠券
        LimitVoucher limitVoucher = limitVoucherService.getById(voucherId);
        Integer limitCount = limitVoucher.getLimitCount();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:voucher:" + voucherId + userId);

        try {

            // 2.判断库存是否充足
            if (limitVoucher.getStock() < buyNumber) {
                // 库存不足
                return Result.fail("库存不足！");
            }

            // 3.判断是否限购
            // 执行查询
            List<VoucherOrder> orderList = this.list(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getUserId, userId)
                    .eq(VoucherOrder::getVoucherId, voucherId));

            // 计算购买数量总和
            int totalBuyNumber = orderList.stream()
                    .mapToInt(VoucherOrder::getBuyNumber)
                    .sum();

            if (totalBuyNumber + buyNumber > limitCount) {
                return Result.fail("超过最大购买限制!");
            }

            // 4. 尝试获取锁，最多等待10s
            boolean isLock = false;
            isLock = redisLock.tryLock(10, TimeUnit.SECONDS);
            // 判断
            if (!isLock) {
                // 获取锁失败，直接返回失败或者重试
                log.error("获取锁失败！");
                return Result.fail("同一时间下单人数过多，请稍后重试");
            }

            //5. 乐观锁扣减库存
            boolean success = limitVoucherService.update()
                    .setSql("stock= stock -" + buyNumber)
                    .eq("voucher_id", voucherId)
                    .ge("stock", buyNumber)
                    .update(); //where id = ? and stock >= buyNumber
            if (!success) {
                //扣减库存
                return Result.fail("库存不足！");
            }
            //6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder().setId(redisIdWorker.nextId("order"))
                    .setVoucherId(voucherId)
                    .setUserId(userId)
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now())
                    .setStatus(1)
                    .setBuyNumber(buyNumber);
            save(voucherOrder);

            //7. 返回结果
            return Result.ok(voucherOrder);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            redisLock.unlock();
        }
    }

    @Override
    @Transactional
    public Result limitVoucher2(Long voucherId, int buyNumber) {
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + voucherId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不要重复下单");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > MAX_BUY_LIMIT) {
                return Result.fail("超过最大购买限制!");
            }

            // 6.扣减库存
            boolean success = limitVoucherService.update()
                    .setSql("stock = stock - " + buyNumber) // set stock = stock - buynumber
                    .eq("voucher_id", voucherId)
                    .gt("stock", buyNumber) // where id = ? and stock > buynumber
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足");
            }
            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder().setId(redisIdWorker.nextId("order"))
                    .setVoucherId(voucherId)
                    .setUserId(userId)
                    .setCreateTime(LocalDateTime.now())
                    .setUpdateTime(LocalDateTime.now())
                    .setStatus(1)
                    .setBuyNumber(buyNumber);
            save(voucherOrder);
            return Result.ok(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }
}
