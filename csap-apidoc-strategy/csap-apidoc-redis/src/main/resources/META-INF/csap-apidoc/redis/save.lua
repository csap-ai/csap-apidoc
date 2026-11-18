-- CSAP Apidoc Redis 模式:保存一个接口的规则(原子执行),约定见 docs/design/redis-mode.md 第 3 节。
-- KEYS = [rules, version, history:{ruleKey}, changes]
-- ARGV = [ruleKey, after, updatedBy, updatedAt, historyLimit, channel];after 为空字符串表示删除
-- 返回 -1(内容相同,没有修改)或新版本号。Java 与 Go 使用同一段脚本文本。

local ruleKey = ARGV[1]
local after = ARGV[2]
local updatedBy = ARGV[3]
local updatedAt = tonumber(ARGV[4])
local limit = tonumber(ARGV[5])
local channel = ARGV[6]

-- 1. 修改前的规则(不存在为 nil)
local before = redis.call('HGET', KEYS[1], ruleKey)
if before == false then
  before = nil
end
local afterValue = nil
if after ~= '' then
  afterValue = after
end

-- 2. 内容相同:不加版本号,不留记录
if before == afterValue then
  return -1
end

-- 3. 版本号加 1
local v = redis.call('INCR', KEYS[2])

-- 4. 写规则(为空则删除)
if afterValue == nil then
  redis.call('HDEL', KEYS[1], ruleKey)
else
  redis.call('HSET', KEYS[1], ruleKey, afterValue)
end

-- 5. 写修改记录,裁掉超出 historyLimit 的旧记录并同步删除 changes 索引
local record = cjson.encode({
  id = v,
  ruleKey = ruleKey,
  before = before or cjson.null,
  after = afterValue or cjson.null,
  version = v,
  updatedBy = updatedBy,
  updatedAt = updatedAt
})
redis.call('LPUSH', KEYS[3], record)
if limit > 0 then
  local old = redis.call('LRANGE', KEYS[3], limit, -1)
  for _, r in ipairs(old) do
    local oldId = cjson.decode(r).id
    redis.call('HDEL', KEYS[4], tostring(oldId))
  end
  redis.call('LTRIM', KEYS[3], 0, limit - 1)
end

-- 6. 修改记录索引:修改记录 id -> 规则键
redis.call('HSET', KEYS[4], tostring(v), ruleKey)

-- 7. 通知其他实例
redis.call('PUBLISH', channel, tostring(v))
return v
