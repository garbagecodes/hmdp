package com.hmdp;

import com.like.utils.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import javax.annotation.Resource;

@Slf4j
@SpringBootTest
class BloomFilterTest {

    @Resource
    private BloomFilterService bloomFilterService;

    @Test
    void initBloomFilterFromDatabase() {
        bloomFilterService.initBloomFilter();
        log.info("布隆过滤器已初始化");
    }

    @Test
    void resultTest(){
        String[] s  = new String[]{"1","1011"};
        if(bloomFilterService.isExist("like-behavior-bloom-filter",s)){
            System.out.println("存在该数据");
        }else {
            System.out.println("不存在该数据");
        }
    }
}
