# 审计哈希链(1.1)

读者:管理员、运维、安全评审。读完能理解哈希链证明什么、怎么在控制台校验、怎么用独立脚本离线校验、怎么把链头存到团队版之外。

> 本页功能从团队版 **1.1.0** 起提供。1.0 的审计没有哈希链。

## 它解决什么问题

审计表在 1.0 就是"只追加"的:团队版自己没有修改、删除审计的入口。但有数据库写权限的人可以绕过团队版直接改表。哈希链用来**发现**这种事:

- 每条审计带一个哈希,哈希里包含上一条审计的哈希。改了其中任何一条,或删掉中间一条,后面的链就接不上。
- 校验可以在控制台点一下完成,也可以拿导出的 JSON 用独立脚本离线完成,不依赖团队版。

它能**发现**篡改,不能阻止篡改。要防住"整条链被重算",需要把链头存到团队版之外(见下文"外部锚定")。

## 算法

每条审计写入时:

```
hash = lowercase_hex( SHA-256( prev_hash + "\n" + canonical ) )
```

- `prev_hash`:上一条审计(按 `id`)的 `hash`;链的第一条用 64 个 `0`。
- `canonical`:下列 19 个值组成的 JSON 数组,用紧凑格式(无空格、无换行)序列化,字符串按 JSON 标准转义(非 ASCII 字符**原样输出**,不转成 `\uXXXX`),整数按十进制,缺失为 `null`:

  `[id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key, service, rule_key, change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash]`

  示例:`[12,1763452800000,"USER",1,"张三","CONSOLE","10.0.0.1","change.publish",1,"prod","order-service","k",12,null,"{}",null,null,345,"0000…"]`
- UTF-8 编码后做 SHA-256。

说明:

- 写入在同一个数据库事务里串行完成:锁定链头 → 插入记录得到 `id` → 计算哈希 → 写入这条记录的 `prev_hash` / `hash` → 更新链头。提交之后的审计行不会再被修改。
- 审计写入失败会让所在的业务操作一起失败,不会出现"操作成功了、审计没记"。

## 升级时封存的含义

1.0 的审计没有哈希。升级到 1.1 时,团队版按 `id` 顺序给**已有记录**逐条计算并写入哈希(一次性回填,分批 1000 条),然后追加一条 `audit.chain.sealed` 审计(来源"系统",原因 `sealed N records at upgrade`)。

这意味着:

- **1.1 升级之前的记录是在升级那一刻封存的**,哈希链只能证明**升级之后**它们没被改过。升级之前它们有没有被改动过,链无法证明。
- 升级之前请先备份数据库,备份本身就是升级前状态的凭证。
- 想让封存更可信:升级后立刻导出一份审计 JSON 并保存到团队版之外,记下链头(见下文)。

## 保留期与 anchor

配置了 `csap.team.audit.retention-days` 清理旧审计后,剩下的第一条的 `prev_hash` 指向已被删除的记录。清理时团队版会把**被删的最后一条**的 `{id, hash}` 存为 **anchor**,校验从 anchor 接上:

- 剩下的第一条的 `prev_hash` 必须等于 anchor 的 `hash`;
- `audit.purge`(清理审计)记录的原因里也带 anchor 的 id 和 hash;
- JSON 导出的开头带 `anchor`(没有清理过时为 `null`)。

所以清理不会让链断掉,校验仍然通过。

## 在控制台校验

管理员:菜单"审计"→ "校验哈希链"按钮,显示结果:是否通过、校验条数、链头 id 与哈希、第一条有问题的记录 id 和原因。

每次校验都会记一条审计 `audit.verify`,原因里带结果。

### 接口

`GET /api/v1/audit/verify?fromId=&toId=`(仅管理员)

```bash
curl -s 'https://team.example.com/api/v1/audit/verify' -H 'X-CSAP-Team: 1' -b cookies.txt
```

```json
{"ok":true,"checked":1532,"headId":1540,"headHash":"8736ef83…","firstBrokenId":null,"problem":null}
```

| 字段 | 说明 |
|---|---|
| `ok` | 是否通过 |
| `checked` | 已校验的条数 |
| `headId` / `headHash` | 链头(校验范围内最后一条)的 id 与哈希 |
| `firstBrokenId` | 第一条有问题的记录 id,通过时为 `null` |
| `problem` | 问题类型,通过时为 `null` |

不传 `fromId` / `toId` = 全部(从 anchor 或链首开始),流式分批计算,大库也不会一次读进内存。

### 问题类型

| `problem` | 含义 |
|---|---|
| `HASH_MISMATCH` | 这条记录的内容与它的哈希对不上:内容被改过 |
| `PREV_MISMATCH` | 这条记录的 `prev_hash` 不等于上一条的 `hash`:中间有记录被删、被插入或被替换 |
| `MISSING_HASH` | 这条记录没有哈希(升级后不应出现) |
| `GAP` | id 不连续;仅用于说明原因 |

注意:数据库自增 id 在事务回滚时会跳号,**id 跳号本身不算错**。实际判断以 `prev_hash` 能否接上为准。

## 离线独立校验

脚本 [tools/verify-audit-chain.py](tools/verify-audit-chain.py) 只用 Python 3 标准库,不依赖团队版。第三方拿到导出的 JSON 就能校验。

1. 管理员在审计页筛选(不筛选 = 全部),点"导出 JSON"。导出内容是:

   ```json
   {"anchor":{"id":100,"hash":"…"},"items":[{"id":101,"at":…,"prevHash":"…","hash":"…", …}]}
   ```

   `anchor` 在没有清理过时为 `null`。导出列增加了 `envKey`、`prevHash`、`hash`。

2. 校验:

   ```bash
   python3 verify-audit-chain.py audit-export.json
   ```

   通过时输出"通过:共校验 N 条,链头 id=… hash=…",退出码 0;不通过时输出第一条有问题的记录 id 和原因(`HASH_MISMATCH` / `PREV_MISMATCH` / `MISSING_HASH`),退出码 1;输入有误退出码 2。

3. 其他选项:

   | 选项 | 说明 |
   |---|---|
   | `--head-hash <哈希>` | 再与外部保存的链头哈希比对(例如收到的 `audit.checkpoint`)。不一致就判未通过,说明整条链可能被重算过 |
   | `--allow-partial` | 导出的只是中间一段:不核对第一条的 `prev_hash`,只校验这一段内部是否连续、哈希是否对 |
   | `--self-test` | 用脚本内构造的 3 条记录自测:算一遍再校验,并验证改内容、删记录都会被发现 |

```bash
python3 verify-audit-chain.py --self-test
python3 verify-audit-chain.py audit-export.json --head-hash 8736ef83…
```

导出文件要**完整**(含 `anchor` 和全部 `items`)才能核对链头与链首;筛选后的导出只适合配合 `--allow-partial` 做段内校验。

1.0 的导出是数组,没有哈希,不能用这个脚本校验。

## 外部锚定:`audit.checkpoint`

单靠团队版自己的库,有足够权限的人可以把整条链**重新算一遍**再写回去,校验仍会通过。对策是把链头存到团队版之外,之后任何人重算整条链都会发现链头对不上。

订阅 Webhook 事件 **`audit.checkpoint`**:

- 每天 00:05(服务器时区)发送一次当前链头;可用 `csap.team.audit.checkpoint-cron` 调整(默认 `0 5 0 * * *`);
- 只有订阅了的 Webhook 才会收到;
- 通用 Webhook 请求体:

  ```json
  {"event":"audit.checkpoint","at":1763453100000,
   "checkpoint":{"headId":1540,"headHash":"8736ef83…","count":1532},
   "text":"审计链头 #1540 8736ef83…"}
  ```

  (`text` 的具体文案以实际发送为准。)

建议:把这个 Webhook 指向**别的系统**——运维群、外部归档、对象存储——让它由团队版之外的人保管。要核对时,取某天的 `headId` / `headHash`,与导出的记录或校验接口返回的值比对,或把 `headHash` 交给脚本的 `--head-hash`(导出范围需截止到那个 `headId` 的链头)。

## 常见问题

| 问题 | 回答 |
|---|---|
| 校验不通过一定是被篡改吗 | 不一定,也可能是数据库恢复、手工迁移或升级中断。先看第一条有问题的 id 附近的记录,再对照备份;确认异常后按安全事件处理 |
| 清理过旧审计,校验还能通过吗 | 能,校验从 anchor 接上 |
| 从备份恢复到旧时间点 | 恢复后链是备份时刻的链,自洽;但链头比外部保存的 checkpoint 旧,这是预期的 |
| 升级前的记录能证明没被改吗 | 不能,见上文"升级时封存" |
