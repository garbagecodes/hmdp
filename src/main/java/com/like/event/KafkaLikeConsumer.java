package com.like.event;

import com.alibaba.fastjson.JSONObject;
import com.hmdp.entity.Event;
import com.like.entity.LikeArticleCount;
import com.like.entity.LikeBehavior;
import com.like.service.ILikeArticleCountService;
import com.like.service.ILikeBehaviorService;
import com.like.service.ILikeUserCountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.like.utils.KafkaConstants.TOPIC_LIKE_BEHAVIOR;
import static com.like.utils.KafkaConstants.TOPIC_SAVE_DB_FAILED;

@Component
@Slf4j
public class KafkaLikeConsumer {
    @Resource
    private ILikeBehaviorService likeBehaviorService;
    @Resource
    private ILikeArticleCountService likeArticleCountService;
    @Resource
    private ILikeUserCountService likeUserCountService;
    @Resource
    private KafkaLikeProducer kafkaLikeProducer;
//    private final List<LikeBehavior> likeBehaviorBuffer = new ArrayList<>(); // 存储接收到的消息
    private final Map<String, Integer> articleCountBuffer = new HashMap<>();
    private final Map<String, Integer> userCountBuffer = new HashMap<>();
    private final Map<LikeBehavior, Acknowledgment> acks = new HashMap<>();

    @KafkaListener(topics = TOPIC_LIKE_BEHAVIOR)
    public void likeBehaviorMsgConsumer(ConsumerRecord record, Acknowledgment ack) {
        //1. 消息校验
        if (record == null || record.value() == null) {
            log.error("消息的内容为空!");
            return;
        }

        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if (event == null) {
            log.error("消息格式错误!");
            return;
        }

        //2. 提取点赞行为数据
        Map<String, Object> data = event.getData();
        Long articleId = Long.valueOf(data.get("articleId").toString());
        Long userId = event.getUserId();
        Integer type = Integer.valueOf(data.get("type").toString());
        Long behaviorId = Long.valueOf(data.get("behaviorId").toString());
        LocalDateTime time = LocalDateTime.now();

        LikeBehavior likeBehavior = new LikeBehavior()
                .setBehaviorId(behaviorId)
                .setUserId(userId)
                .setArticleId(articleId)
                .setType(type)
                .setTime(time);

        // 3. 插入数据库
        int tryCount = 0;
        try {
            while (!likeBehaviorService.saveLikeLog(likeBehavior)) {
                if (++tryCount < 100) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (tryCount >= 100) {
                Map<String, Object> likeData = new HashMap<>();
                likeData.put("behaviorId", behaviorId);
                likeData.put("articleId", articleId);
                likeData.put("type",type);
                likeData.put("time", time);
                Event saveDBFailedEvent = new Event()
                        .setUserId(userId)
                        .setTopic(TOPIC_SAVE_DB_FAILED)
                        .setData(likeData);
                kafkaLikeProducer.publishEvent(saveDBFailedEvent);
            }
        }

        //4. 统计文章和用户获赞的总数量
        int diff = type == 1 ? 1 : -1;
        articleCountBuffer.put(articleId.toString(), articleCountBuffer.getOrDefault(articleId.toString(), 0) + diff);
        userCountBuffer.put(userId.toString(), userCountBuffer.getOrDefault(userId.toString(), 0) + diff);

        //5. 确认
        ack.acknowledge();
    }

    @KafkaListener(topics = TOPIC_SAVE_DB_FAILED)
    public void saveDBFailedMsgConsumer(ConsumerRecord record, Acknowledgment ack) {
        //1. 消息校验
        if (record == null || record.value() == null) {
            log.error("消息的内容为空!");
            return;
        }

        Event event = JSONObject.parseObject(record.value().toString(), Event.class);
        if (event == null) {
            log.error("消息格式错误!");
            return;
        }

        //2. 提取点赞行为数据
        Map<String, Object> data = event.getData();
        Long articleId = Long.valueOf(data.get("articleId").toString());
        Long userId = event.getUserId();
        articleCountBuffer.put(articleId.toString(), articleCountBuffer.getOrDefault(articleId.toString(), 0)-1);
        userCountBuffer.put(userId.toString(), userCountBuffer.getOrDefault(userId.toString(), 0)-1);

        //3. 给上层发消息
        //sendMsgToApp(JSONObject.parseObject(data.toString(), LikeBehavior.class));
    }

    @Scheduled(fixedRate = 3000) // 每3秒执行一次
    private void flush() {
        updateLikeCount();
    }

    private void updateLikeCount() {
        if (!articleCountBuffer.isEmpty()) {
            List<LikeArticleCount> countList = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : articleCountBuffer.entrySet()) {
                Long articleId = Long.valueOf(entry.getKey());
                Integer count = entry.getValue();
                countList.add(new LikeArticleCount().setArticleId(articleId).setLikeCount(count));
            }
            likeArticleCountService.updateBatchCount(countList);
            likeUserCountService.updateBatchCount(countList);
            articleCountBuffer.clear();
        }
    }

}
