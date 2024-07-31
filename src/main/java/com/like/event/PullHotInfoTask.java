package com.like.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class PullHotInfoTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

}
