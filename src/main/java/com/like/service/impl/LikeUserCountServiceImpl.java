package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.like.entity.Article;
import com.like.entity.LikeArticleCount;
import com.like.entity.LikeUserCount;
import com.like.mapper.ArticleMapper;
import com.like.mapper.LikeUserCountMapper;
import com.like.service.ILikeUserCountService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LikeUserCountServiceImpl extends ServiceImpl<LikeUserCountMapper, LikeUserCount> implements ILikeUserCountService {

    @Resource
    private LikeUserCountMapper likeUserCountMapper;
    @Resource
    private ArticleMapper articleMapper;

    @Async
    public CompletableFuture<Void> updateBatchCount(List<LikeArticleCount> likeArticleCountList) {
        for (LikeArticleCount likeArticleCount : likeArticleCountList) {
            // 根据articleId查询对应的userId
            Article article = articleMapper.selectById(likeArticleCount.getArticleId());
            if (article != null) {
                int count = likeArticleCount.getLikeCount();
                Long authorId = article.getUserId();

                // 查询数据库中是否已存在该记录
                LikeUserCount existingCount = likeUserCountMapper.selectOne(new LambdaQueryWrapper<LikeUserCount>()
                        .eq(LikeUserCount::getUserId, authorId));

                if (existingCount != null) {
                    // 如果数据库中存在数据，则累加获赞总数
                    existingCount.setLikeCount(existingCount.getLikeCount() + count);
                    likeUserCountMapper.updateById(existingCount);
                } else {
                    // 如果数据库中不存在数据，则直接保存
                    likeUserCountMapper.insert(new LikeUserCount()
                            .setUserId(authorId)
                            .setLikeCount(count));
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}