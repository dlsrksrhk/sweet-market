local memberState = redis.call('GET', KEYS[3])
if memberState ~= ARGV[2] then
    return 0
end

local pendingMemberToken = ARGV[1]
local expiresAt = redis.call('ZSCORE', KEYS[2], pendingMemberToken)
if not expiresAt or tonumber(expiresAt) <= tonumber(ARGV[3]) then
    redis.call('ZREM', KEYS[2], pendingMemberToken)
    redis.call('DEL', KEYS[3])
    redis.call('DECR', KEYS[1])
    return 0
end

redis.call('ZREM', KEYS[2], pendingMemberToken)
redis.call('SET', KEYS[3], 'issued', 'KEEPTTL')
return 1
