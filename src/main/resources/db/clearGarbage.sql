DROP TABLE IF EXISTS `latest_like_behavior`;
CREATE TABLE latest_like_behavior AS
WITH ranked_likes AS (
    SELECT
        behavior_id,
        article_id,
        user_id,
        type,
    time,
    ROW_NUMBER() OVER (PARTITION BY article_id, user_id ORDER BY time DESC) AS rn
    FROM
    like_behavior
    )
SELECT
    behavior_id,
    article_id,
    user_id,
    type,
    time
FROM
    ranked_likes
WHERE
    rn = 1 AND type = 1
ORDER BY
    time;