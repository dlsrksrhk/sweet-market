local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])
for _, memberToken in ipairs(expired) do
    local separator = string.find(memberToken, ':')
    local memberId = string.sub(memberToken, 1, separator - 1)
    local token = string.sub(memberToken, separator + 1)
    local memberKey = ARGV[8] .. memberId
    if redis.call('GET', memberKey) == token then
        redis.call('DEL', memberKey)
        if tonumber(redis.call('GET', KEYS[1]) or '0') > tonumber(ARGV[2]) then
            redis.call('DECR', KEYS[1])
        end
    end
    redis.call('ZREM', KEYS[2], memberToken)
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[6])
end

local memberState = redis.call('GET', KEYS[3])
if memberState == 'issued' then
    return {'ALREADY_ISSUED'}
end
if memberState then
    return {'IN_PROGRESS'}
end

if tonumber(redis.call('GET', KEYS[1])) >= tonumber(ARGV[3]) then
    return {'SOLD_OUT'}
end

redis.call('INCR', KEYS[1])
redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[6])
redis.call('ZADD', KEYS[2], ARGV[5], ARGV[7] .. ':' .. ARGV[4])
redis.call('PEXPIRE', KEYS[2], ARGV[6])

return {'RESERVED', ARGV[4]}
