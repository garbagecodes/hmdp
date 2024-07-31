package com.like.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeBehaviorDTO {
    /**
     * 文章ID
     */
    @NotBlank(message = "文章ID不能为空")
    private Long articleId;

    /**
     * 点赞类型 (1-点赞, 0-取消点赞)
     */
    @NotNull(message = "点赞类型不能为空")
    private Integer type;
}
