package com.like.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LikeStatusDTO {
    /**
     * 文章ID
     */
    @NotBlank(message = "文章ID不能为空")
    private Long articleId;
}
