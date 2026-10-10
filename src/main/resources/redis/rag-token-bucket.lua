-- Two keys share {rag}; constant time; Redis TIME avoids application clock skew.
local clock = redis.call('TIME')
local now = tonumber(clock[1]) + tonumber(clock[2]) / 1000000
local function bucket(key, cap, rate)
  local state = redis.call('HMGET', key, 'tokens', 'time')
  local previous = tonumber(state[2]) or now
  local tokens = math.min(cap, (tonumber(state[1]) or cap) + math.max(0, now - previous) * rate)
  return {tokens, cap, rate}
end
local ip = bucket(KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]))
local global = bucket(KEYS[2], tonumber(ARGV[3]), tonumber(ARGV[4]))
local globalEnabled = ARGV[5] == '1'
local allowed = ip[1] >= 1 and (not globalEnabled or global[1] >= 1)
local wait = math.max(math.max(0, 1-ip[1])/ip[3], globalEnabled and math.max(0, 1-global[1])/global[3] or 0)
if allowed then
  ip[1] = ip[1] - 1
  if globalEnabled then global[1] = global[1] - 1 end
end
local function save(key, b)
  redis.call('HSET', key, 'tokens', b[1], 'time', now)
  redis.call('EXPIRE', key, math.max(30, math.ceil(2*b[2]/b[3])))
end
save(KEYS[1], ip)
if globalEnabled then save(KEYS[2], global) end
return {allowed and 1 or 0, math.floor(ip[1]), math.floor(global[1]), allowed and 0 or math.max(1,math.ceil(wait))}
