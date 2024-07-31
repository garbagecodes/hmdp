package com.like.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.like.entity.LikeBehavior;
import com.like.mapper.LikeBehaviorMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.like.utils.RedisConstants.LIKE_BEHAVIOR_BLOOM_FILTER;

@Service
public class BloomFilterService {
    @Resource
    private LikeBehaviorMapper likeBehaviorMapper;
    @Resource
    private RedissonClient client;

    private static final int PAGE_SIZE = 1000; // 每次查询的数据量

    public void initBloomFilter() {
        RBloomFilter<Object> bloomFilter = client.getBloomFilter(LIKE_BEHAVIOR_BLOOM_FILTER);
        // 初始化布隆过滤器，设计预计元素数量为100万，误差率为1%
        bloomFilter.tryInit(1000000L,0.01);

        int pageNum = 1;
        List<String> keys;
        do {
            keys = getKeysFromDatabase(pageNum, PAGE_SIZE);
            for (String key : keys) {
//                addKeyToBloomFilter(key);
                bloomFilter.add(key);
            }
            pageNum += 1;
        } while (!keys.isEmpty());

    }

    public List<LikeBehavior> getLikeBehaviorList(int pageNum, int pageSize) {
        // 创建分页对象
        Page<LikeBehavior> pageInfo = new Page<>(pageNum, pageSize);

        // 构建查询条件
        QueryWrapper<LikeBehavior> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("type", 1); // 根据实际需求构建查询条件

        // 调用 selectPage() 方法进行分页查询
        Page<LikeBehavior> pageResult = likeBehaviorMapper.selectPage(pageInfo, queryWrapper);

        // 获取查询到的用户列表
        List<LikeBehavior> likeBehaviors = pageResult.getRecords();

        // 返回分页后的结果
        return likeBehaviors;
    }

    private List<String> getKeysFromDatabase(int pageNum, int pageSize) {
        List<LikeBehavior> likeBehaviorList = getLikeBehaviorList(pageNum, pageSize);
        List<String> keys = new ArrayList<>();
        for (LikeBehavior likeBehavior : likeBehaviorList) {
            if (likeBehavior.getType() == 1) {
                keys.add(likeBehavior.getArticleId().toString() + likeBehavior.getUserId().toString());
            }
        }
        return keys;
    }

    public boolean addKeyToBloomFilter(String name, String ... args){
        RBloomFilter<Object> bloomFilter = client.getBloomFilter(name);
        StringBuilder key = new StringBuilder();
        for (String arg : args) {
            key.append(arg);
        }
        return bloomFilter.add(key.toString());
    }

    public boolean isExist(String name, String ... args){
        RBloomFilter<Object> bloomFilter = client.getBloomFilter(name);
        StringBuilder key = new StringBuilder();
        for (String arg : args) {
            key.append(arg);
        }
        return bloomFilter.contains(key.toString());
    }

}