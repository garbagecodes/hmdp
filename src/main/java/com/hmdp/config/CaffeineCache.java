package com.hmdp.config;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CaffeineCache {

    @Bean
    public LoadingCache<String, String> loadingCache() {
        CacheLoader<String, String> cacheLoader = key -> {
            return null;
        };

        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build(cacheLoader);
    }
}
