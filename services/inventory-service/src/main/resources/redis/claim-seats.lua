-- KEYS are seat keys; ARGV are reservation ID and remaining lifetime in milliseconds.
-- A retry must never extend the original reservation deadline.
local ttl = tonumber(ARGV[2])
if not ttl or ttl <= 0 then return 0 end
for _, key in ipairs(KEYS) do
    local owner = redis.call('GET', key)
    if owner and owner ~= ARGV[1] then return 0 end
end
for _, key in ipairs(KEYS) do
    redis.call('SET', key, ARGV[1], 'NX', 'PX', ttl)
end
return 1
