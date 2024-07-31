package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Event;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.like.dto.LikeBehaviorDTO;
import com.like.entity.LikeBehavior;
import com.like.event.KafkaLikeProducer;
import com.like.mapper.LikeBehaviorMapper;
import com.like.service.ILikeBehaviorService;
import com.like.utils.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private CacheManager cacheManager;
    @Resource
    private BloomFilterService bloomFilterService;
    @Resource
    private RedisIdWorker redisIdWorker;

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

        //3.增加点赞记录到redis zset
        updateRedisZset(articleId,userId,type);

        int diff = type == 1 ? 1 : -1;

        //4.更新Redis count，并获取文章获赞总数
        int articleCount = updateRedisLikeCount(ARTICLE_LIKE_COUNT + articleId, diff);
        if (articleCount == -1) {
            return Result.fail("数据异常");
        }

        //5.更新Redis count，获取redis作者获赞总数
        if (updateRedisLikeCount(USER_LIKE_COUNT + userId, diff) == -1) {
            return Result.fail("数据异常");
        }

        //6. 发送消息到kafka，上面将redis的数据暂时存为1，这里的kafka消费者最终会将数据统一
        Long behaviorId = redisIdWorker.nextId("like-behavior"); // 生成点赞行为ID
        sendLikeBehaviorMsg(behaviorId, articleId, userId, type);

        //7. 返回文章总获赞数量
        return Result.ok(articleCount);
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
            redisTemplate.opsForZSet().add(userLikeZsetKey, articleId, score);
            trimLikeZsetList(userLikeZsetKey); //异步裁剪zset长度为固定200以内，更多数据需要查询数据库

            //记录文章点赞列表，zset用于查询文章点赞列表
            redisTemplate.opsForZSet().add(articleLikeZsetKey, articleId, score);
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
        kafkaLikeProducer.publishEvent(event);
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
}
