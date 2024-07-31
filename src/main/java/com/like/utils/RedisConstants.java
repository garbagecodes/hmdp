package com.like.utils;

public class RedisConstants {
    public static final String USER_LIKE_ZSET_KEY = "likeZset:userId:";
    public static final String ARTICLE_LIKE_ZSET_KEY = "likeZset:articleId:";
    public static final String IS_LIKE_KEY = "isLike:";
    public static final Long IS_LIKE_TTL = 24L;

    public static final String ARTICLE_LIKE_COUNT = "likeCount:articleId:";

    public static final String USER_LIKE_COUNT = "likeCount:userId:";
    public static final String LIKE_BEHAVIOR_BLOOM_FILTER = "like-behavior-bloom-filter";
    public static final Integer HASH_BITS = 23;
    public static final Integer ZSET_LENGTH_LIMIT = 200;

}
