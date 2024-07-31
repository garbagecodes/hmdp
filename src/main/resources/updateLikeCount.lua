local key = ARGV[1]
local diff = tonumber(ARGV[2])
local newCount = 1

-- 检查diff是否为nil
if diff == nil then
    return 11  -- 返回一个错误码
end

if redis.call('exists', key) == 1 then
    newCount = tonumber(redis.call('get', key))
    newCount = newCount + diff
end
redis.call('set', key, newCount)
return newCount