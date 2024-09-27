local orderId = tonumber(ARGV[1])
local voucherId = tonumber(ARGV[2])
local buyNumber = tonumber(ARGV[3])
local userId = tonumber(ARGV[4])
local stockKey = "seckill:stock:" .. voucherId
local orderSetKey = "seckill:order" -- 假设订单存储在名为seckill:orders的Set中
local buyCountKey = "seckill:buyCount:" .. voucherId

-- 检查订单是否存在于Set中
if redis.call("SISMEMBER", orderSetKey, orderId) == 1 then
    -- 订单存在，恢复库存
    redis.call("INCRBY", stockKey, buyNumber)

    -- 从Set中删除订单信息
    redis.call("SREM", orderSetKey, orderId)

    -- 减少用户在哈希结构中的购买数量
    local currentBuyCount = tonumber(redis.call("HGET", buyCountKey, userId))
    if currentBuyCount then
        redis.call("HSET", buyCountKey, userId, currentBuyCount - buyNumber)
    end

    return 1 -- 表示成功
else
    return 0 -- 表示订单不存在
end