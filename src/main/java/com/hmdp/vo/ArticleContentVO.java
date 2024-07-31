package com.hmdp.vo;

import lombok.Data;

@Data
public class ArticleContentVO {
    /**
     * 主键,文章ID
     */
    private Long articleId;

    /**
     * 图片数量
     */
    private Integer imageCount;

    /**
     * 文章内容
     */
    private String content;

    /**
     * 图片路径 (;分隔)
     */
    private String images;

    /**
     * 是否点赞
     */
    private Integer isLike;  //0--未点赞，1--已点赞

    private Integer likeCount;
}
