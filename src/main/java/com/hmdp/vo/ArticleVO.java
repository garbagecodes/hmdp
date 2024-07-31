package com.hmdp.vo;

import lombok.Data;

@Data
public class ArticleVO {

    private Long articleId;

    /**
     * 作者ID
     */
    private Long userId;

    private String username;  //用户名
    private String avatar; //头像
    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章封面图片地址
     */
    private String cover;
    private Integer likeCount;

}
