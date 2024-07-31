package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.like.entity.LikeArticleCount;
import com.like.mapper.LikeArticleCountMapper;
import com.like.service.ILikeArticleCountService;
import com.like.service.ILikeBehaviorService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.like.utils.RedisConstants.ARTICLE_LIKE_COUNT;

@Service
public class LikeArticleCountServiceImpl extends ServiceImpl<LikeArticleCountMapper, LikeArticleCount> implements ILikeArticleCountService {

    @Resource
    private ILikeBehaviorService likeBehaviorService;

    public Map<Long, Integer> queryBatchCount(List<Long> articleIds){
        Map<Long, Integer> counts = new HashMap<>();
        List<Long> queryIds = new ArrayList<>();
        for (Long articleId: articleIds) {
            String count = likeBehaviorService.getCache(ARTICLE_LIKE_COUNT + articleId);
            if (count != null){
                counts.put(articleId,Integer.valueOf(count));
            }else {
                queryIds.add(articleId);
            }
        }
        counts.putAll(this.queryBatchCount(queryIds));
        return counts;
    }

    @Async
    @Override
    public CompletableFuture<Void> updateBatchCount(List<LikeArticleCount> likeArticleCountList) {
        for (LikeArticleCount likeArticleCount : likeArticleCountList) {
            LambdaQueryWrapper<LikeArticleCount> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(LikeArticleCount::getArticleId, likeArticleCount.getArticleId());
            LikeArticleCount existingCount = this.getOne(queryWrapper);

            if (existingCount != null) {
                // 如果数据库中存在数据，则累加获赞总数
                existingCount.setLikeCount(existingCount.getLikeCount() + likeArticleCount.getLikeCount());
                this.updateById(existingCount);
            } else {
                // 如果数据库中不存在数据，则直接保存
                this.save(likeArticleCount);
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}