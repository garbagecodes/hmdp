package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Event;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.like.dto.LikeBehaviorDTO;
import com.like.entity.LikeBehavior;
import com.like.event.KafkaLikeProducer;
import com.like.mapper.LikeBehaviorMapper;
import com.like.service.IArticleService;
import com.like.service.ILikeBehaviorService;
import com.like.utils.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.like.utils.KafkaConstants.TOPIC_LIKE_BEHAVIOR;
import static com.like.utils.RedisConstants.*;

@Service
@Slf4j
public class LikeBehaviorServiceImpl extends ServiceImpl<LikeBehaviorMapper, LikeBehavior> implements ILikeBehaviorService {
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private KafkaLikeProducer kafkaLikeProducer;
    @Resource
    private BloomFilterService bloomFilterService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private IArticleService articleService;
    @Resource
    private IUserService userService;
    @Resource
    private LoadingCache<String, String> cache;
    @PostConstruct
    public LoadingCache<String, String> init() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build(new CacheLoader() {
                    @Nullable
                    @Override
                    public Object load(@NonNull Object o) throws Exception {
                        // 如果 key 不存在，返回 null
                        return null;
                    }
                });
    }
    @Override
    public Result like(LikeBehaviorDTO likeBehaviorDTO) {

        Long articleId = likeBehaviorDTO.getArticleId();
        Long userId = UserHolder.getUser().getId();

        //1.判断本次是点赞还是取消赞
        Integer type = likeBehaviorDTO.getType();
        if (type == 1) {
            //1.1 如果本次操作是点赞
            if (isLike(articleId, userId)){
                // 1.1.1如果当前用户已点赞
                return Result.fail("您已经点过赞了！");
            }else{
                // 1.1.2 如果当前用户还未点赞，更新布隆过滤器
                bloomFilterService.addKeyToBloomFilter(LIKE_BEHAVIOR_BLOOM_FILTER,articleId.toString(),userId.toString());
            }

        }else {
            //1.2 如果本次操作是取消赞
            if (!isLike(articleId, userId)){
                // 1.2.1 上一次记录是取消点赞或者无记录，即没有点赞
                return Result.fail("您还没有点赞哦");
            }
        }

//        int diff = type == 1 ? 1 : -1;
//
//        // 更新redis
//        updateRedis(articleId, userId, type, diff);
//
//        //新增：更新localcache
//        updateLocalCache(articleId,userId,diff);

        //6. 发送消息到kafka，上面将redis的数据暂时存为1，这里的kafka消费者最终会将数据统一
        Long behaviorId = redisIdWorker.nextId("like-behavior"); // 生成点赞行为ID
        sendLikeBehaviorMsg(behaviorId, articleId, userId, type);

        //7. 返回成功
        return Result.ok();
    }

    @Async
    public void updateRedis(Long articleId, Long userId, Integer type, int diff) {
        //3.增加点赞记录到redis zset
        updateRedisZset(articleId, userId, type);

        //4.更新Redis count，并获取文章获赞总数
        if (updateRedisLikeCount(ARTICLE_LIKE_COUNT + articleId, diff) == -1) {
            log.info("文章获赞总数数据异常，准备删除异常的redis数据...");
            redisTemplate.delete(ARTICLE_LIKE_COUNT + articleId);
            log.info("异常数据删除成功！");
        }

        //5.更新Redis count，获取redis作者获赞总数
        if (updateRedisLikeCount(USER_LIKE_COUNT + userId, diff) == -1) {
            log.info("作者获赞总数数据异常，准备删除异常的redis数据...");
            redisTemplate.delete(USER_LIKE_COUNT + userId);
            log.info("异常数据删除成功！");
        }
    }

    @Async
    public void updateLocalCache(Long articleId, Long userId, int diff) {
        Integer articleCount = Integer.valueOf(cache.get(ARTICLE_LIKE_COUNT + articleId));
        Integer userCount = Integer.valueOf(cache.get(USER_LIKE_COUNT + userId));
        //只有缓存中已经有数据才会更新，如果本来就没有数据的则不会存入local cache
        if (articleCount != null){
            cache.put(ARTICLE_LIKE_COUNT + articleId,String.valueOf(articleCount + diff));
        }
        if (userCount != null) {
            cache.put(USER_LIKE_COUNT + userId, String.valueOf(userCount + diff));
        }
    }

    public String getCache(String key){
        return cache.get(key);
    }

    @Override
    public boolean saveLikeLog(LikeBehavior likeBehavior) {
        if (!save(likeBehavior)){
            return false;
        }
        updateRedis(likeBehavior.getArticleId(),likeBehavior.getUserId(),likeBehavior.getType(),likeBehavior.getType() == 1 ? 1 : -1);
        updateLocalCache(likeBehavior.getArticleId(),likeBehavior.getUserId(),likeBehavior.getType() == 1 ? 1 : -1);
        return true;
    }

    private void updateRedisZset(Long articleId, Long userId, Integer type) {
        //1. 判断操作类型为点赞还是取消赞
        String userLikeZsetKey = USER_LIKE_ZSET_KEY + userId;
        String articleLikeZsetKey = ARTICLE_LIKE_ZSET_KEY + articleId;
        if (type == 1) {
            //1.1 如果为点赞
            // 获取当前时间戳作为score
            double score = Instant.now().getEpochSecond();
            //记录用户点赞列表，zset用于用户查询点赞列表
            if (!redisTemplate.opsForZSet().add(userLikeZsetKey, articleId, score)) {
                log.info("添加点赞记录到文章zset失败，准备删除相关数据");
                redisTemplate.delete(userLikeZsetKey);
            }
            trimLikeZsetList(userLikeZsetKey); //异步裁剪zset长度为固定200以内，更多数据需要查询数据库

            //记录文章点赞列表，zset用于查询文章点赞列表
            if (!redisTemplate.opsForZSet().add(articleLikeZsetKey, articleId, score)) {
                log.info("添加点赞记录到用户zset失败，准备删除相关数据");
                redisTemplate.delete(articleLikeZsetKey);
            }
            trimLikeZsetList(articleLikeZsetKey);//异步裁剪zset长度为固定200以内，更多数据需要查询数据库

        }else {
            //1.2 如果为取消赞
            redisTemplate.opsForZSet().remove(userLikeZsetKey,articleId);
            redisTemplate.opsForZSet().remove(articleLikeZsetKey,userId);
        }

    }

    @Async
    public void trimLikeZsetList(String key) {
        // 获取当前用户的点赞数量
        Long likeCount = redisTemplate.opsForZSet().zCard(key);
        // 如果点赞数量超过200，移除多余的点赞记录
        if (likeCount > ZSET_LENGTH_LIMIT) {
            // 计算需要移除的点赞记录的数量
            long removeCount = likeCount - ZSET_LENGTH_LIMIT;

            // 获取需要移除的点赞记录的分数
            Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScore(key, 0, removeCount - 1);

            // 移除多余的点赞记录
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                redisTemplate.opsForZSet().remove(key, tuple.getValue());
            }
        }
    }

    private int updateRedisLikeCount(String key, int diff) {
        String count = stringRedisTemplate.opsForValue().get(key);
        if (count == null) {
            //如果redis中没有相关数据
            if (diff == 1) {
                //并且本次是点赞操作，先暂时把它存为 1
                redisTemplate.opsForValue().set(key, 1);
            }//end if，如果本次是取消赞操作，先不做任何处理
        } else {
            //如果redis中有相关数据，直接更新值
            if (Integer.valueOf(count) + diff < 0) {
                return -1; //返回-1表示数据异常
            }
            redisTemplate.opsForValue().set(key, Integer.parseInt(count) + diff);
        }
        return count == null ? 0 : Integer.parseInt(count) + diff; //返回自然数（0或正数）表示正常
    }

    private void sendLikeBehaviorMsg(Long behaviorId, Long articleId, Long userId, Integer type) {
        Map<String, Object> data = new HashMap<>();
        data.put("behaviorId", behaviorId);
        data.put("articleId", articleId);
        data.put("type", type);
        Event event = new Event()
                .setTopic(TOPIC_LIKE_BEHAVIOR)
                .setUserId(userId)
                .setData(data);
        kafkaLikeProducer.publishLikeEvent(event);
    }


    private boolean isLike(Long articleId, Long userId) {

        //1. 如果布隆过滤器不存在该数据，返回false
        if (!bloomFilterService.isExist(LIKE_BEHAVIOR_BLOOM_FILTER,articleId.toString(),userId.toString())){
            return false;
        }

        //2. 如果Redis zset存在该数据，直接返回true
        if (isZsetExist(USER_LIKE_ZSET_KEY + userId,articleId.toString())
                || isZsetExist(USER_LIKE_ZSET_KEY + articleId, userId.toString())){
            return true;
        }

        //3. 查询数据库是否存在点赞记录
        LikeBehavior latestLikeBehavior = this.getOne(new LambdaQueryWrapper<LikeBehavior>()
                .eq(LikeBehavior::getArticleId, articleId)
                .eq(LikeBehavior::getUserId, userId)
                .orderByDesc(LikeBehavior::getTime)
                .last("LIMIT 1"));

        // 判断记录是否存在，以及type的值
        if (latestLikeBehavior != null && latestLikeBehavior.getType() == 1) {
            return true;
        }

        return false;
    }

    public boolean isZsetExist(String key, String member){
        if (redisTemplate.opsForZSet().score(key, member) == null)
            return false;
        return true;
    }

    @Scheduled(fixedRate = 2 * 60 * 60 * 1000) // 每2小时执行一次
    //@Scheduled(fixedRate = 2000)
    public void pullHotArticleList() {
        List<Long> hotArticles = articleService.queryHotArticle();
        for (Long articleId : hotArticles) {
            String key = ARTICLE_LIKE_COUNT + articleId;
            String count = stringRedisTemplate.opsForValue().get(key);
            if (count != null) {
                cache.put(key, count);
                log.info("线程{}成功缓存了热点文章信息到本地缓存中",Thread.currentThread().getId());
            }
        }
    }

    @Scheduled(fixedRate = 2 * 60 * 60 * 1000) // 每2小时执行一次
    //@Scheduled(fixedRate = 2000)
    public void pullHotUserList() {
        List<Long> hotUsers = userService.queryHotUser();
        for (Long userId : hotUsers) {
            String key = USER_LIKE_COUNT + userId;
            String count = stringRedisTemplate.opsForValue().get(key);
            if (count != null) {
                cache.put(key, count);
                log.info("线程{}成功缓存了热点用户信息到本地缓存中",Thread.currentThread().getId());
            }
        }
    }
}
