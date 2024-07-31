package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.like.entity.LikeArticleCount;
import com.like.mapper.LikeArticleCountMapper;
import com.like.service.ILikeArticleCountService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LikeArticleCountServiceImpl extends ServiceImpl<LikeArticleCountMapper, LikeArticleCount> implements ILikeArticleCountService {

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