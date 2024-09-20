package com.like.event;

import com.alibaba.fastjson.JSONObject;
import com.hmdp.entity.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
@Component
@Slf4j
public class KafkaLikeProducer {
    @Resource
    private KafkaTemplate kafkaTemplate;

    public void publishEvent(Event event) {
        // 将事件发布到指定的主题
        kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));
    }

    public void publishLikeEvent(Event event) {
        Long userId = event.getUserId();
        if (userId !=null) {
            kafkaTemplate.send(event.getTopic(), userId, JSONObject.toJSONString(event));
        }else {
            return;
        }
    }
}
