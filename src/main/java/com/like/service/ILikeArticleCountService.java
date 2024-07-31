package com.like.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.like.entity.LikeArticleCount;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ILikeArticleCountService extends IService<LikeArticleCount> {

    CompletableFuture<Void> updateBatchCount(List<LikeArticleCount> likeArticleCountList);
}
