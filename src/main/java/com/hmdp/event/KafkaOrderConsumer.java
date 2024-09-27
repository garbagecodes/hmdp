package com.hmdp.event;

import com.alibaba.fastjson.JSONObject;
import com.hmdp.entity.Event;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.KafkaConstants.TOPIC_CREATE_ORDER;
import static com.hmdp.utils.KafkaConstants.TOPIC_SAVE_ORDER_FAILED;

@Component
@Slf4j
public class KafkaOrderConsumer {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);


    // 消费下单事件
    @KafkaListener(topics = {TOPIC_CREATE_ORDER})
    public void handleCreateOrder(ConsumerRecord record) {
        if (record == null || record.value() == null) {
            log.error("消息的内容为空!");
            return;
        }

        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if (event == null) {
            log.error("消息格式错误!");
            return;
        }

        // 提交订单处理任务到线程池
        executorService.submit(() -> {
            Map<String, Object> data = event.getData();
            VoucherOrder voucherOrder = new VoucherOrder()
                    .setId(event.getEntityId())
                    .setUserId(event.getUserId())
                    .setVoucherId(Long.valueOf(data.get("voucherId").toString()))
                    .setBuyNumber(Integer.valueOf(data.get("buyNumber").toString()));

            voucherOrderService.createVoucherOrder(voucherOrder);
        });
    }

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @KafkaListener(topics = {TOPIC_SAVE_ORDER_FAILED})
    public void handleSaveOrderFailed(ConsumerRecord record) {
        if (record == null || record.value() == null) {
            log.error("消息的内容为空!");
            return;
        }

        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if (event == null) {
            log.error("消息格式错误!");
            return;
        }

        //此时订单保存到数据库失败，数据库中的数据不需要更改，只需要将库存等信息在Redis中还原即可
        //但是还原redis中的库存数据需要确保原子性，即借助lua脚本即可，这样就不会出现并发问题了
        //这里相当于加库存，其实是对库存的更新，本质上和减库存是一样的，借助Lua保证操作的原子性，同时也不会对下单业务造成影响
        Map<String, Object> data = event.getData();
        Long voucherId = Long.valueOf(data.get("voucherId").toString());
        Integer buyNumber = Integer.valueOf(data.get("buyNumber").toString());
        Long result = stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(event.getEntityId()),
                voucherId.toString(),
                String.valueOf(buyNumber),
                event.getUserId().toString()
        );

        if (result == 1){
            log.info("库存恢复成功");
        }else {
            log.info("redis中不存在此订单");
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}