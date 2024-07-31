package com.like.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.vo.ArticleVO;
import com.like.entity.Article;

import java.util.List;

public interface IArticleService extends IService<Article> {
    /**
     * 根据作者查询文章列表
     *
     * @param authorId 作者ID
     * @return 文章列表
     */
    List<Article> queryArticlesByAuthorId(Long authorId);

    /**
     * 根据文章ID查询文章
     *
     * @param articleId 文章ID
     * @return 文章对象
     */
    ArticleVO queryArticleById(Long articleId);

    List<Long> queryHotArticle();
}
