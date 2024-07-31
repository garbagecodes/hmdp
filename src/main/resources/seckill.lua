--1. 参数列表
--1.1. 优惠券id
local voucherId = ARGV[1]
-- 1.2. 用户id
local userId = ARGV[2]
-- 1.3. 订单id
local orderId = ARGV[3]
-- 1.4. 当前时间戳（毫秒）
local currentTime = tonumber(ARGV[4])
-- 1.5. 请求购买数量
local requestQuantity = tonumber(ARGV[5])
-- 1.6. 最大限购值
local maxLimit = tonumber(ARGV[6])

-- 2. 数据key
-- 2.1. 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2. 订单key
local orderKey = 'seckill:order'
-- 2.3. 用户购买数量key
local buyCountKey = 'seckill:buyCount:' .. voucherId

-- 3. 从Redis获取开始时间和结束时间
local beginTime = redis.call('hget', stockKey, 'beginTime')
local endTime = redis.call('hget', stockKey, 'endTime')

if beginTime then
    beginTime = tonumber(beginTime)
else
    return 1
end

if endTime then
    endTime = tonumber(endTime)
else
    return 1
end

-- 4. 检查活动是否已经开始和是否已经结束
if currentTime < beginTime then
    -- 4.1. 秒杀尚未开始，返回2
    return 2
elseif currentTime > endTime then
    -- 4.2. 秒杀已经结束，返回3
    return 3
end

-- 5. 检查库存是否充足
local stock = redis.call('hget', stockKey, 'stock')
if stock then
    stock = tonumber(stock)
    if stock < requestQuantity then
        -- 5.1. 库存不足，返回4
        return 4
    end
else
    return 1
end

-- 6. 检查用户购买数量是否超过限购值
local currentBuyCount = redis.call('hget', buyCountKey, 'userId:' .. userId)
if currentBuyCount then
    currentBuyCount = tonumber(currentBuyCount)
else
    currentBuyCount = 0
end

if (currentBuyCount + requestQuantity) > maxLimit then
    -- 6.1. 超过限购值，返回5
    return 5
end

-- 7. 扣减库存
redis.call('hincrby', stockKey, 'stock', -requestQuantity)

-- 8. 更新用户购买数量
redis.call('hincrby', buyCountKey, 'userId:' .. userId, requestQuantity)

-- 9.下单（保存用户）sadd orderKey orderId
redis.call('sadd', orderKey, orderId)

return 0