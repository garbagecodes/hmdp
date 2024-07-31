package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfig {
    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建redis模板对象...");
        //创建Template
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate();
        //设置redis的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        /*
         注：当前配置类不是必须的，因为 Spring Boot 框架会自动装配 RedisTemplate 对象，
          但是默认的key序列化器和value序列化器都是JdkSerializationRedisSerializer，
          默认序列化器导致存到Redis中的数据和原始数据有差别（可读性差），
          故设置为需要修改序列化器。
          这里使用StringRedisSerializer作为key的序列化器，转为String存储。
          使用GenericJackson2JsonRedisSerializer作为value的序列化器，转为json对象存储。
         */

        //设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        //设置redis value的序列化器
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        log.info("redis模板对象创建完成...");
        return redisTemplate;
    }
}
