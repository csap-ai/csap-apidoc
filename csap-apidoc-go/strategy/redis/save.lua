-- csap-apidoc Redis 保存脚本。与 docs/design/redis-mode.md 第 3 节一致;Java 与 Go 内嵌同一段文本,改动前先改约定文档。
-- KEYS = [rules, version, history:{ruleKey}, changes]
-- ARGV = [ruleKey, after, updatedBy, updatedAt, historyLimit, channel];after 为空字符串表示删除
-- 返回新版本号;内容没变化时返回 -1(不加版本号、不留记录)
local before = redis.call('HGET', KEYS[1], ARGV[1])
local after = ARGV[2]
if (before == false and after == '') or before == after then
  return -1
end
local v = redis.call('INCR', KEYS[2])
if after == '' then
  redis.call('HDEL', KEYS[1], ARGV[1])
else
  redis.call('HSET', KEYS[1], ARGV[1], after)
end
local rec = cjson.encode({
  id = v,
  ruleKey = ARGV[1],
  before = before or cjson.null,
  after = (after == '') and cjson.null or after,
  version = v,
  updatedBy = ARGV[3],
  updatedAt = tonumber(ARGV[4])
})
redis.call('LPUSH', KEYS[3], rec)
local limit = tonumber(ARGV[5])
local old = redis.call('LRANGE', KEYS[3], limit, -1)
for _, r in ipairs(old) do
  local o = cjson.decode(r)
  redis.call('HDEL', KEYS[4], tostring(o.id))
end
redis.call('LTRIM', KEYS[3], 0, limit - 1)
redis.call('HSET', KEYS[4], tostring(v), ARGV[1])
redis.call('PUBLISH', ARGV[6], tostring(v))
return v
