package com.like.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.vo.ArticleVO;
import com.like.entity.Article;
import com.like.mapper.ArticleMapper;
import com.like.service.IArticleService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements IArticleService {

    @Override
    public List<Article> queryArticlesByAuthorId(Long authorId) {
        // 使用 MyBatis Plus 的 lambda 查询功能
        return baseMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, authorId)
                .eq(Article::getStatus, 1)); // 假设 1 表示已发布的状态
    }

    @Override
    public ArticleVO queryArticleById(Long articleId) {
        // 使用 MyBatis Plus 的 selectById 方法
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(baseMapper.selectById(articleId),articleVO);
        return articleVO;
    }

    @Override
    public List<Long> queryHotArticle() {
        List<Long> hotList = new ArrayList<>();
        hotList.add(1L);
        hotList.add(2L);
        hotList.add(5L);
        return hotList;
    }
}