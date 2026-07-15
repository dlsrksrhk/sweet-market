if redis.call('GET', KEYS[3]) ~= ARGV[2] then
    return 0
end

if redis.call('ZREM', KEYS[2], ARGV[1]) == 0 then
    return 0
end

redis.call('DEL', KEYS[3])
redis.call('DECR', KEYS[1])
return 1
