package com.like.controller;

import com.hmdp.dto.Result;
import com.like.dto.LikeBehaviorDTO;
import com.like.service.ILikeBehaviorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/like")
public class LikeController {

    @Resource
    private ILikeBehaviorService likeBehaviorService;
    /**
     * 用户点赞或取消点赞的行为接口
     */
    @PostMapping("/behavior")
    public Result like(@RequestBody LikeBehaviorDTO dto) {
        return likeBehaviorService.like(dto);
    }

//    /**
//     * 获取某一用户的点赞列表接口，为 文章ID 的 LIST
//     */
//    @PostMapping("/user")
//    public Result getUserLikeList(@RequestBody LikeUserDTO dto) {
//        return likeUserService.getUserLikeList(dto);
//    }
//
//    /**
//     * 获取某一文章的点赞列表接口，为 用户ID 的list
//     */
//    @PostMapping("/article")
//    public Result getArticleLikeList(@RequestBody LikeArticleDTO dto) {
//        return likeArticleService.getArticleLikeList(dto);
//    }
}
