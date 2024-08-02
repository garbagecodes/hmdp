package com.like.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.like.entity.LikeArticleCount;
import com.like.entity.LikeUserCount;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ILikeUserCountService extends IService<LikeUserCount> {

    CompletableFuture<Void> updateBatchCount(List<LikeArticleCount> likeArticleCountList);

    Map<Long, Integer> queryBatchCount(List<Long> userIds);
}
