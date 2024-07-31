package com.like.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.like.entity.Article;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章实体的 Mapper 接口
 */
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 根据作者 ID 查询文章列表
     *
     * @param authorId 作者ID
     * @return 文章列表
     */
    List<Article> selectArticlesByAuthorId(@Param("authorId") Long authorId);

    /**
     * 根据文章 ID 查询文章
     *
     * @param articleId 文章ID
     * @return 文章对象
     */
    Article selectArticleById(@Param("articleId") Long articleId);
}
