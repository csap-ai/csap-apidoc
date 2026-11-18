#!/usr/bin/env python3
"""Mock 生成规则的参考实现(1.4.0 设计的一部分)。

用途:生成并校验黄金样本。Java、Go 的实现必须对 cases/*.input.json 产生与 cases/*.expected.json
数值相等的 JSON(比较时按 JSON 值比较,对象键顺序、数字的书写形式不敏感)。
用法: python3 reference.py            # 重新生成所有 expected 并写盘
      python3 reference.py --check    # 只检查,不一致时退出码为 1
规则正文见 ../1.4.0-mock-schema.md 第 3 节;两边冲突时以文档正文为准,并修正本脚本。
"""
import datetime
import glob
import json
import os
import re
import sys

M64 = (1 << 64) - 1


def fnv1a64(data: bytes) -> int:
    h = 0xCBF29CE484222325
    for b in data:
        h = ((h ^ b) * 0x100000001B3) & M64
    return h


def splitmix64(x: int) -> int:
    x = (x + 0x9E3779B97F4A7C15) & M64
    z = x
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
    return z ^ (z >> 31)


def rand(seed: str, path: str) -> int:
    return splitmix64(fnv1a64((seed + "\n" + path).encode("utf-8")))


ARRAY_NAMES = ("list", "set", "collection", "iterable", "array", "arraylist", "linkedlist", "hashset", "linkedhashset",
               "treeset", "sortedset", "vector", "queue", "deque", "arraydeque")


def kind_of(data_type):
    """dataType → (kind, elementType)。kind: string integer number boolean date datetime time object array"""
    t = (data_type or "").strip()
    if t.endswith("[]"):
        return "array", t[:-2]
    lt, gt = t.find("<"), t.rfind(">")
    raw = t[:lt] if lt > 0 else t
    raw = raw[raw.rfind(".") + 1:].lower()
    if raw in ARRAY_NAMES or (lt > 0 and (raw.endswith("list") or raw.endswith("set"))):
        return "array", (t[lt + 1:gt] if lt > 0 and gt > lt else "string")
    if raw in ("int", "integer", "int32", "short", "byte", "long", "int64", "biginteger", "atomicinteger", "atomiclong"):
        return "integer", None
    if raw in ("double", "float", "float32", "float64", "bigdecimal", "number"):
        return "number", None
    if raw in ("boolean", "bool"):
        return "boolean", None
    if raw == "localdate":
        return "date", None
    if raw in ("localdatetime", "date", "offsetdatetime", "zoneddatetime", "instant", "timestamp", "time"):
        return "datetime", None
    if raw == "localtime":
        return "time", None
    if raw.endswith("map") or raw in ("object", "jsonnode", "jsonobject"):
        return "object", None
    return "string", None


def coerce(s, kind):
    """示例/默认值/候选值按字段类型转换;失败返回 (False, None)。"""
    s = s.strip() if kind in ("integer", "number", "boolean") else s
    if kind == "integer":
        if re.fullmatch(r"[+-]?\d+", s):
            return True, int(s)
        return False, None
    if kind == "number":
        try:
            v = float(s)
        except ValueError:
            return False, None
        if v != v or v in (float("inf"), float("-inf")):
            return False, None
        return True, v
    if kind == "boolean":
        if s.lower() in ("true", "false"):
            return True, s.lower() == "true"
        return False, None
    if kind in ("string", "date", "datetime", "time"):
        return True, s
    return False, None


def parse_range(p):
    parts = p.split(",", 1)
    lo = int(parts[0].strip())
    if len(parts) == 1:
        return lo, lo
    if parts[1].strip() == "":
        return lo, -1
    return lo, int(parts[1].strip())


ID_RE = re.compile(r"(?i:^id$)|.*[a-z0-9](Id|ID)$|.*_id$")
NAMES = ["Alice", "Bob", "Carol", "David"]


def validates(p, typ):
    return [v for v in (p.get("validate") or []) if (v.get("type") or "").lower() == typ]


def fit_string(s, p):
    for v in validates(p, "length") + validates(p, "size"):
        try:
            lo, hi = parse_range(v.get("pattern") or "")
        except ValueError:
            continue
        if len(s) < lo:
            s += "x" * (lo - len(s))
        if hi >= 0 and len(s) > hi:
            s = s[:hi]
    return s


def mock_value(m, kind, r):
    if not isinstance(m, str):
        return m
    if m.startswith("@@"):
        return m[1:]
    if not m.startswith("@"):
        return m
    mm = re.fullmatch(r"@int\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)", m)
    if mm:
        lo, hi = int(mm.group(1)), int(mm.group(2))
        return lo + r % (hi - lo + 1) if hi >= lo else lo
    mm = re.fullmatch(r"@oneOf\((.*)\)", m)
    if mm:
        items = [x.strip() for x in mm.group(1).split(",")]
        pick = items[r % len(items)]
        ok, v = coerce(pick, kind)
        return v if ok else pick
    if m == "@uuid":
        h = "%016x%016x" % (r, splitmix64(r))
        return "%s-%s-4%s-8%s-%s" % (h[0:8], h[8:12], h[13:16], h[17:20], h[20:32])
    return m  # 未知表达式原样返回


def enum_values(p):
    for v in validates(p, "enum"):
        vals = [x.strip() for x in (v.get("pattern") or "").split(",") if x.strip()]
        if vals:
            return vals
    names = [e.get("name") for e in (p.get("extendDescr") or []) if e.get("name")]
    return names


def gen_scalar(p, kind, path, ctx):
    r = rand(ctx["seed"], path)
    name = p.get("name") or ""
    low = name.lower().replace("_", "").replace("-", "")
    if not ctx.get("heuristics", True):
        name, low = "", ""  # 关闭启发式:所有按字段名的分支都不会命中
    # 1 mock
    if p.get("mock") is not None:
        return mock_value(p["mock"], kind, r)
    # 2 example / 3 default
    for key in ("example", "defaultValue"):
        s = p.get(key)
        if s:
            ok, v = coerce(s, kind)
            if ok:
                return v
    # 4 enum
    ev = enum_values(p)
    if ev:
        pick = ev[r % len(ev)]
        ok, v = coerce(pick, kind)
        if ok:
            return v
    # 5 约束 / 6 名称 / 7 类型
    if kind == "integer":
        mins = [float(v["pattern"]) for v in validates(p, "min")]
        maxs = [float(v["pattern"]) for v in validates(p, "max")]
        if not mins and not maxs:
            if ID_RE.match(name):
                return 1000 + r % 9000
            if low == "code":
                return 200
            if low in ("page", "pageno", "pagenum", "current", "pageindex"):
                return 1
            if low in ("pagesize", "size", "limit", "perpage"):
                return 10
            if low == "age":
                return 18 + r % 60
        lo = int(max(mins)) if mins else 1
        hi = int(min(maxs)) if maxs else (lo + 99 if mins else 100)
        if hi < lo:
            hi = lo
        return lo + r % min(hi - lo + 1, 1 << 53)
    if kind == "number":
        mins = [float(v["pattern"]) for v in validates(p, "min")]
        maxs = [float(v["pattern"]) for v in validates(p, "max")]
        lo = max(mins) if mins else 1.0
        hi = min(maxs) if maxs else (lo + 99 if mins else 100.0)
        if hi < lo:
            hi = lo
        steps = int(round((hi - lo) * 100))
        return round(lo + (r % (steps + 1)) / 100.0, 2)
    if kind == "boolean":
        if low == "success":
            return True
        return r % 2 == 0
    if kind == "date":
        return (datetime.date(2026, 1, 1) + datetime.timedelta(days=r % 365)).isoformat()
    if kind in ("datetime", "time"):
        secs = (r >> 16) % 86400
        t = "%02d:%02d:%02d" % (secs // 3600, secs % 3600 // 60, secs % 60)
        if kind == "time":
            return t
        d = (datetime.date(2026, 1, 1) + datetime.timedelta(days=r % 365)).isoformat()
        return d + "T" + t + "Z"
    if kind == "object":
        return {}
    # string
    if low in ("message", "msg"):
        s = "ok"
    elif low == "code":
        s = "200"
    elif low == "email":
        s = "user%d@example.com" % (r % 1000)
    elif low in ("phone", "mobile", "tel", "telephone", "cellphone"):
        s = "13%09d" % (r % 10**9)
    elif low in ("name", "username", "nickname", "realname", "fullname"):
        s = NAMES[r % 4]
    elif ID_RE.match(name):
        s = "id-%08x" % (r >> 32)
    elif low in ("url", "link", "website", "homepage", "avatar", "image", "img", "icon", "logo", "picture"):
        s = "https://example.com/" + name
    else:
        s = "string"
    return fit_string(s, p)


def children_of(p):
    c = p.get("children")
    if c and c.get("parameters"):
        return c["parameters"]
    return p.get("parameters") or []


def gen_object(params, path, depth, ctx):
    out = {}
    for p in params:
        if p.get("hidden") or not p.get("name"):
            continue
        out[p["name"]] = gen_field(p, path + "." + p["name"], depth, ctx)
    return out


def array_len(p, ctx):
    n = ctx["arraySize"]
    for v in validates(p, "size") + validates(p, "length"):
        try:
            lo, hi = parse_range(v.get("pattern") or "")
        except ValueError:
            continue
        n = max(n, lo)
        if hi >= 0:
            n = min(n, hi)
    for v in validates(p, "notempty"):
        n = max(n, 1)
    return max(0, min(n, 10))


def gen_field(p, path, depth, ctx):
    """depth = 所在对象的深度(根对象为 1)。"""
    kind, elem = kind_of(p.get("dataType"))
    kids = children_of(p)
    if p.get("modelType") == "ARRAY" and kind != "array":
        kind, elem = "array", elem or "string"
    if p.get("mock") is not None:
        return mock_value(p["mock"], kind, rand(ctx["seed"], path))
    if kind == "array":
        n = array_len(p, ctx)
        if kids:
            if depth + 1 > ctx["maxDepth"]:
                return []
            return [gen_object(kids, "%s[%d]" % (path, i), depth + 1, ctx) for i in range(n)]
        ek, _ = kind_of(elem)
        ep = {"name": p.get("name"), "dataType": elem}
        return [gen_scalar(ep, ek, "%s[%d]" % (path, i), ctx) for i in range(n)]
    if kids:
        if depth + 1 > ctx["maxDepth"]:
            return None
        return gen_object(kids, path, depth + 1, ctx)
    return gen_scalar(p, kind, path, ctx)


def generate(case):
    ctx = {"seed": case.get("seed", "csap"), "arraySize": case.get("arraySize", 2), "maxDepth": case.get("maxDepth", 6),
           "heuristics": case.get("heuristics", True)}
    models = case.get("response") or []
    if not models:
        return None
    md = models[0]
    params = md.get("parameters") or []
    mt = md.get("modelType")
    if mt == "BASE_DATA":
        k, _ = kind_of(md.get("name"))
        return gen_scalar({"name": ""}, k, "$", ctx)
    if mt == "ARRAY":
        n = ctx["arraySize"]
        return [gen_object(params, "$[%d]" % i, 1, ctx) for i in range(n)]
    return gen_object(params, "$", 1, ctx)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    bad = 0
    for f in sorted(glob.glob(os.path.join(here, "cases", "*.input.json"))):
        case = json.load(open(f, encoding="utf-8"))
        out = generate(case)
        exp = f.replace(".input.json", ".expected.json")
        text = json.dumps(out, ensure_ascii=False, indent=2) + "\n"
        if "--check" in sys.argv:
            if json.load(open(exp, encoding="utf-8")) != out:
                print("MISMATCH", exp)
                bad = 1
        else:
            open(exp, "w", encoding="utf-8").write(text)
            print("wrote", exp)
    sys.exit(bad)


if __name__ == "__main__":
    main()
