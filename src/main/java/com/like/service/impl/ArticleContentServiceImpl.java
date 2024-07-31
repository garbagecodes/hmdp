package com.like.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.like.entity.ArticleContent;
import com.like.mapper.ArticleContentMapper;
import com.like.service.IArticleContentService;
import org.springframework.stereotype.Service;

@Service
public class ArticleContentServiceImpl extends ServiceImpl<ArticleContentMapper, ArticleContent> implements IArticleContentService {

}