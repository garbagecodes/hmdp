package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.like.service.IArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/article")
public class ArticleController {
    @Resource
    private IArticleService articleService;

    /**
     * 根据作者ID获取文章列表
     * @param authorId 作者ID
     * @return 文章列表
     */
    @GetMapping("/author/{authorId}")
    public Result getArticlesByAuthorId(@PathVariable Long authorId) {
        return Result.ok(articleService.queryArticlesByAuthorId(authorId));
    }

    /**
     * 根据文章ID获取文章详情
     * @param articleId 文章ID
     * @return 文章详情
     */
    @GetMapping("/{articleId}")
    public Result getArticleById(@PathVariable Long articleId) {
        return Result.ok(articleService.queryArticleById(articleId));
    }

    /**
     * 获取热点文章total篇
     * @param total
     * @return
     */
    @GetMapping("/hot/{total}")
    public Result getHotArticle(@PathVariable Long total) {
        return Result.ok(articleService.queryHotArticle());
    }

}
