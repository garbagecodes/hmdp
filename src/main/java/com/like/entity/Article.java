package com.like.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_article_info")
public class Article implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键,文章ID
     */
    @TableId(value = "article_id", type = IdType.AUTO)
    private Long articleId;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章封面图片地址
     */
    private String cover;

    /**
     * 文章状态 (例如：0-草稿，1-已发布，2-已下架，3-已删除)
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
