#!/usr/bin/env python3
"""CSAP Apidoc 团队版 审计哈希链 独立校验脚本。

只用 Python 3 标准库,不依赖团队版。输入是团队版"导出 JSON"的文件:
    {"anchor": {"id": n, "hash": "..."} | null, "items": [ ... ]}

用法:
    python3 verify-audit-chain.py audit-export.json
    python3 verify-audit-chain.py audit-export.json --head-hash <外部保存的链头哈希>
    python3 verify-audit-chain.py --self-test

退出码:0 通过;1 未通过(输出第一条有问题的 id);2 输入有误。

算法(与团队版 1.1 契约一致):
    hash = lowercase_hex( SHA-256( prev_hash + "\\n" + canonical ) )
    canonical = 下列 19 个值组成的 JSON 数组,紧凑格式(无空格、无换行),
                非 ASCII 字符原样输出,整数按十进制,缺失为 null:
    [id, at, actor_type, actor_id, actor_name, source, ip, action, project_id,
     env_key, service, rule_key, change_id, before_value, after_value, diff,
     reason, oss_history_id, prev_hash]
    链的第一条 prev_hash 为 64 个 "0";保留期清理后,第一条的 prev_hash 等于 anchor.hash。
"""
import argparse
import hashlib
import json
import sys
import tempfile
import os

ZERO = "0" * 64

# canonical 中 19 个值的顺序;每项 (snake_case, camelCase)。导出 JSON 两种写法都接受。
FIELDS = [
    ("id", "id"),
    ("at", "at"),
    ("actor_type", "actorType"),
    ("actor_id", "actorId"),
    ("actor_name", "actorName"),
    ("source", "source"),
    ("ip", "ip"),
    ("action", "action"),
    ("project_id", "projectId"),
    ("env_key", "envKey"),
    ("service", "service"),
    ("rule_key", "ruleKey"),
    ("change_id", "changeId"),
    ("before_value", "beforeValue"),
    ("after_value", "afterValue"),
    ("diff", "diff"),
    ("reason", "reason"),
    ("oss_history_id", "ossHistoryId"),
]


def _get(item, snake, camel):
    if camel in item:
        return item[camel]
    return item.get(snake)  # 缺失按 null


def get_prev(item):
    v = item.get("prevHash", item.get("prev_hash"))
    return v


def get_hash(item):
    return item.get("hash")


def compute_hash(item, prev_hash):
    values = [_get(item, s, c) for s, c in FIELDS] + [prev_hash]
    canonical = json.dumps(values, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256((prev_hash + "\n" + canonical).encode("utf-8")).hexdigest()


def verify(doc, allow_partial=False, allow_gaps=False):
    """返回 (ok, checked, first_broken_id, problem, head_id, head_hash)。"""
    anchor = doc.get("anchor")
    items = sorted(doc["items"], key=lambda x: x["id"])
    expected_prev = anchor["hash"] if anchor else ZERO
    head_id, head_hash = (anchor["id"], anchor["hash"]) if anchor else (None, None)
    checked = 0
    for i, it in enumerate(items):
        h, p = get_hash(it), get_prev(it)
        if not h or not p:
            return False, checked, it["id"], "MISSING_HASH", head_id, head_hash
        if allow_gaps or (i == 0 and allow_partial):
            expected_prev = p  # 只导出了一段:第一条的 prev_hash 无法核对,按它自己的值接着算
        if p != expected_prev:
            return False, checked, it["id"], "PREV_MISMATCH", head_id, head_hash
        if compute_hash(it, p) != h:
            return False, checked, it["id"], "HASH_MISMATCH", head_id, head_hash
        checked += 1
        expected_prev = h
        head_id, head_hash = it["id"], h
    return True, checked, None, None, head_id, head_hash


def run(doc, allow_partial=False, head_hash_expected=None, allow_gaps=False):
    ok, checked, bad, problem, head_id, head_hash = verify(doc, allow_partial, allow_gaps)
    if ok and head_hash_expected and head_hash != head_hash_expected.lower():
        print("未通过:链内部一致,但链头哈希与外部保存的值不一致(整条链可能被重算过)")
        print("  链头 id=%s hash=%s" % (head_id, head_hash))
        return 1
    if ok:
        print("通过:共校验 %d 条,链头 id=%s hash=%s" % (checked, head_id, head_hash))
        return 0
    print("未通过:第一条有问题的记录 id=%s,原因 %s(此前已校验通过 %d 条)" % (bad, problem, checked))
    return 1


def self_test():
    # 脚本内构造 3 条记录:算一遍写入 prevHash/hash,再校验
    items, prev = [], ZERO
    raw = [
        dict(id=1, at=1763452800000, actorType="USER", actorId=1, actorName="张三", source="CONSOLE",
             ip="10.0.0.1", action="change.create", projectId=1, envKey="dev", service="order-service",
             ruleKey="demo.OrderController.create", changeId=1, beforeValue=None,
             afterValue='{"amount":{"min":1}}', diff=None, reason='含"引号"与\\反斜杠', ossHistoryId=None),
        dict(id=2, at=1763452860000, actorType="USER", actorId=1, actorName="张三", source="CONSOLE",
             ip="10.0.0.1", action="change.publish", projectId=1, envKey="prod", service="order-service",
             ruleKey="demo.OrderController.create", changeId=1, beforeValue=None, afterValue="{}",
             diff=None, reason=None, ossHistoryId=345),
        dict(id=4, at=1763452920000, actorType="SYSTEM", actorId=None, actorName=None, source="SYSTEM",
             ip=None, action="audit.purge", projectId=None, envKey=None, service=None, ruleKey=None,
             changeId=None, beforeValue=None, afterValue=None, diff=None, reason="换行\n制表\t", ossHistoryId=None),
    ]  # id 跳号(3 回滚)但 prev 接得上,应当通过
    for r in raw:
        r = dict(r)
        r["prevHash"] = prev
        r["hash"] = compute_hash(r, prev)
        prev = r["hash"]
        items.append(r)
    doc = {"anchor": None, "items": items}
    assert verify(doc)[0], "原始链应当通过"
    # JSON 往返(模拟导出文件)后仍应通过
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "export.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False)
        with open(path, encoding="utf-8") as f:
            assert verify(json.load(f))[0], "导出文件往返后应当通过"
    # 改内容 -> HASH_MISMATCH(id=2)
    t = json.loads(json.dumps(doc)); t["items"][1]["action"] = "change.reject"
    r = verify(t); assert (r[0], r[2], r[3]) == (False, 2, "HASH_MISMATCH"), r
    # 删中间一条 -> PREV_MISMATCH(id=4)
    t = json.loads(json.dumps(doc)); del t["items"][1]
    r = verify(t); assert (r[0], r[2], r[3]) == (False, 4, "PREV_MISMATCH"), r
    # 带筛选的导出(中间有缺口):--allow-gaps 只校验每条自身的 hash
    t = json.loads(json.dumps(doc)); del t["items"][1]
    assert verify(t, allow_gaps=True)[0], "--allow-gaps 时缺口不算错"
    t["items"][1]["reason"] = "被改"
    r = verify(t, allow_gaps=True); assert (r[0], r[2], r[3]) == (False, 4, "HASH_MISMATCH"), r
    # 缺 hash -> MISSING_HASH
    t = json.loads(json.dumps(doc)); t["items"][2]["hash"] = None
    r = verify(t); assert (r[0], r[2], r[3]) == (False, 4, "MISSING_HASH"), r
    # anchor:去掉第 1 条,以它的 hash 作为 anchor,应当通过
    t = {"anchor": {"id": 1, "hash": items[0]["hash"]}, "items": items[1:]}
    assert verify(t)[0], "带 anchor 的链应当通过"
    # 链头与外部锚定值比对
    assert run(doc, head_hash_expected=items[-1]["hash"]) == 0
    assert run(doc, head_hash_expected="0" * 64) == 1
    print("自测通过:3 条记录校验通过;改内容、删中间一条、缺 hash 均被发现;带 anchor 的链通过。")
    return 0


def main():
    ap = argparse.ArgumentParser(description="校验团队版导出的审计 JSON 的哈希链")
    ap.add_argument("file", nargs="?", help='导出的 JSON 文件,格式 {"anchor":...,"items":[...]}')
    ap.add_argument("--self-test", action="store_true", help="用脚本内构造的记录自测")
    ap.add_argument("--head-hash", help="外部保存的链头哈希(例如 audit.checkpoint 事件里的 headHash),额外比对")
    ap.add_argument("--allow-partial", action="store_true",
                    help="导出的只是中间一段:不核对第一条的 prev_hash,只校验这一段内部")
    ap.add_argument("--allow-gaps", action="store_true",
                    help="带筛选条件的导出(记录不连续):不核对前后衔接,只校验每条记录自身的 hash")
    a = ap.parse_args()
    if a.self_test:
        return self_test()
    if not a.file:
        ap.print_usage(sys.stderr)
        return 2
    try:
        with open(a.file, encoding="utf-8") as f:
            doc = json.load(f)
        if not isinstance(doc, dict) or "items" not in doc:
            print('输入格式不对:1.1 的导出是 {"anchor":...,"items":[...]};1.0 的数组格式需要先包装成这个形式', file=sys.stderr)
            return 2
        return run(doc, a.allow_partial, a.head_hash, a.allow_gaps)
    except (OSError, ValueError, KeyError) as e:
        print("读取或解析失败:%s" % e, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
