-- A delayed cancellation may only remove locks owned by the original reservation.
for _, key in ipairs(KEYS) do
    if redis.call('GET', key) == ARGV[1] then redis.call('DEL', key) end
end
return 1
