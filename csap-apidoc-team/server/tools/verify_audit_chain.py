#!/usr/bin/env python3
"""
CSAP Apidoc 团队版 审计哈希链离线校验脚本(Python 3 标准库,不依赖团队版)。

用法:
    python3 verify_audit_chain.py audit-export.json [--allow-gaps]

输入:控制台"导出 → JSON"得到的文件,格式 {"anchor": {"id","hash"}|null, "items": [...]}。
算法(与团队版一致):
    hash = lowercase_hex(SHA-256(UTF-8(prev_hash + "\n" + canonical)))
    canonical = 下列 19 个值组成的 JSON 数组,紧凑格式(无空格、无换行),非 ASCII 字符原样输出:
      [id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key,
       service, rule_key, change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash]
    链的第一条 prev_hash 为 64 个 "0";保留期清理后,剩下的第一条的 prev_hash 指向 anchor.hash。

检查:
  1. 每条记录用自己的 prevHash 重算 hash,必须等于 hash;
  2. 按 id 升序,相邻两条的 prevHash 必须等于前一条的 hash(导出带了筛选条件时中间会有缺口,加 --allow-gaps 跳过);
  3. 第一条:prevHash 为全 0,或等于 anchor.hash;否则提示"导出从链中间开始"(不算失败)。
退出码:0 通过,1 发现问题,2 参数 / 文件错误。
"""
import hashlib
import json
import sys

ZERO = "0" * 64
# 导出 JSON 里的字段名,顺序即 canonical 数组的顺序(最后一个 prev_hash 用记录自己的 prevHash)
FIELDS = ["id", "at", "actorType", "actorId", "actorName", "source", "ip", "action", "projectId", "envKey",
          "service", "ruleKey", "changeId", "beforeValue", "afterValue", "diff", "reason", "ossHistoryId", "prevHash"]


def canonical(item):
    values = [item.get(f) for f in FIELDS]
    return json.dumps(values, ensure_ascii=False, separators=(",", ":"))


def compute_hash(item):
    data = (item["prevHash"] + "\n" + canonical(item)).encode("utf-8")
    return hashlib.sha256(data).hexdigest()


def verify(doc, allow_gaps=False):
    """返回 (ok, 消息列表)。"""
    messages = []
    items = sorted(doc["items"], key=lambda r: r["id"])
    anchor = doc.get("anchor")
    ok = True
    prev = None
    for i, item in enumerate(items):
        if not item.get("hash") or not item.get("prevHash"):
            messages.append("id=%s 缺少 hash / prevHash" % item["id"])
            ok = False
            prev = item
            continue
        if compute_hash(item) != item["hash"]:
            messages.append("id=%s HASH_MISMATCH:内容被改过" % item["id"])
            ok = False
        if prev is None:
            start_ok = item["prevHash"] == ZERO or (anchor is not None and item["prevHash"] == anchor["hash"])
            if not start_ok:
                messages.append("提示:导出从链中间开始(id=%s),无法核对它之前的记录" % item["id"])
        elif prev.get("hash") and item["prevHash"] != prev["hash"] and not allow_gaps:
            messages.append("id=%s PREV_MISMATCH:与前一条(id=%s)接不上,中间可能有记录被删" % (item["id"], prev["id"]))
            ok = False
        prev = item
    return ok, messages


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    allow_gaps = "--allow-gaps" in argv
    if len(args) != 1:
        print(__doc__)
        return 2
    try:
        with open(args[0], encoding="utf-8") as f:
            doc = json.load(f)
        if not isinstance(doc, dict) or "items" not in doc:
            print("文件不是 1.1 的审计 JSON 导出格式(应为 {\"anchor\":...,\"items\":[...]})")
            return 2
    except (OSError, ValueError) as e:
        print("读取失败: %s" % e)
        return 2
    ok, messages = verify(doc, allow_gaps)
    for m in messages:
        print(m)
    print("%s:检查了 %d 条记录" % ("通过" if ok else "未通过", len(doc["items"])))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
