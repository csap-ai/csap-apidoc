#!/usr/bin/env python3
"""JSON Schema 导出的参考实现(1.4.0 设计的一部分),规则正文见 ../1.4.0-mock-schema.md 第 4 节。

输入 cases/*.method.json(一个接口的 Method JSON,字段同 /csap/apidoc 里的 methodList 元素),
输出 cases/*.schema.json(/csap/schema/method 返回的 data 部分)。
用法: python3 schema_reference.py [--check]
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from reference import kind_of, coerce, parse_range, children_of  # noqa: E402

DRAFT = "https://json-schema.org/draft/2020-12/schema"
MAX_DEPTH = 12


def base_schema(data_type):
    kind, elem = kind_of(data_type)
    if kind == "array":
        return {"type": "array", "items": base_schema(elem)}
    t = (data_type or "").strip()
    raw = t[: t.find("<")] if "<" in t else t
    raw = raw[raw.rfind(".") + 1:].lower()
    if kind == "integer":
        wide = raw in ("long", "int64", "biginteger", "atomiclong")
        return {"type": "integer", "format": "int64" if wide else "int32"}
    if kind == "number":
        if raw in ("float", "float32"):
            return {"type": "number", "format": "float"}
        if raw == "bigdecimal":
            return {"type": "number"}
        return {"type": "number", "format": "double"}
    if kind == "boolean":
        return {"type": "boolean"}
    if kind == "date":
        return {"type": "string", "format": "date"}
    if kind == "datetime":
        return {"type": "string", "format": "date-time"}
    if kind == "time":
        return {"type": "string", "format": "time"}
    if kind == "object":
        return {"type": "object"}
    return {"type": "string"}


def apply_validate(s, p):
    """validate → JSON Schema 关键字。s 是该字段自身(数组字段则是数组层)的 schema。"""
    t = s.get("type")
    pats = []

    def lo_(key, v):
        s[key] = max(s.get(key, v), v)

    def hi_(key, v):
        s[key] = min(s.get(key, v), v)

    for v in p.get("validate") or []:
        vt = (v.get("type") or "").lower()
        pat = v.get("pattern") or ""
        if vt in ("notempty", "notblank"):
            if t == "string":
                lo_("minLength", 1)
            elif t == "array":
                lo_("minItems", 1)
            elif t == "object":
                lo_("minProperties", 1)
        elif vt in ("length", "size"):
            try:
                lo, hi = parse_range(pat)
            except ValueError:
                continue
            k = {"string": ("minLength", "maxLength"), "array": ("minItems", "maxItems"),
                 "object": ("minProperties", "maxProperties")}.get(t)
            if k:
                lo_(k[0], lo)
                if hi >= 0:
                    hi_(k[1], hi)
        elif vt in ("min", "max"):
            if t in ("integer", "number"):
                try:
                    n = float(pat)
                except ValueError:
                    continue
                n = int(n) if n == int(n) else n
                (lo_ if vt == "min" else hi_)("minimum" if vt == "min" else "maximum", n)
        elif vt == "pattern":
            if t == "string" and pat and not pat.startswith("PATTERN_"):
                pats.append(pat)
        elif vt == "enum":
            vals = [x.strip() for x in pat.split(",") if x.strip()]
            out = []
            for x in vals:
                ok, c = coerce(x, t if t in ("integer", "number", "boolean") else "string")
                if ok:
                    out.append(c)
            if out:
                s["enum"] = out
    if pats:
        s["pattern"] = pats[0]
        if len(pats) > 1:
            s["allOf"] = [{"pattern": x} for x in pats[1:]]


def x_validate(p):
    out = []
    for v in p.get("validate") or []:
        e = {k: v[k] for k in ("type", "pattern", "message", "code", "level") if v.get(k) not in (None, "")}
        out.append(e)
    return out


def field_schema(p, depth):
    kids = children_of(p)
    kind, elem = kind_of(p.get("dataType"))
    if p.get("modelType") == "ARRAY" and kind != "array":
        kind = "array"
    if kids and depth < MAX_DEPTH:
        obj = object_schema(kids, depth + 1)
        s = {"type": "array", "items": obj} if kind == "array" else obj
    elif kids:
        s = {"type": "array", "items": {"type": "object"}} if kind == "array" else {"type": "object"}
    elif kind == "array":
        s = {"type": "array", "items": base_schema(elem)}
    elif p.get("modelType") in ("OBJECT", "T_OBJECT") and kind == "string":
        s = {"type": "object"}
    else:
        s = base_schema(p.get("dataType"))
    d = p.get("description") or p.get("value") or ""
    if d:
        s["description"] = d
    ek = s.get("type")
    if p.get("example") and ek in ("string", "integer", "number", "boolean"):
        ok, v = coerce(p["example"], ek)
        s["examples"] = [v if ok else p["example"]]
    if p.get("defaultValue") and ek in ("string", "integer", "number", "boolean"):
        ok, v = coerce(p["defaultValue"], ek)
        s["default"] = v if ok else p["defaultValue"]
    apply_validate(s, p)
    names = [e.get("name") for e in (p.get("extendDescr") or []) if e.get("name")]
    if names and "enum" not in s and ek == "string":
        s["enum"] = names
    xv = x_validate(p)
    if xv:
        s["x-csap-validate"] = xv
    if p.get("keyName"):
        s["x-csap-key"] = p["keyName"]
    return s


def object_schema(params, depth):
    props, req = {}, []
    for p in params:
        if p.get("hidden") or not p.get("name"):
            continue
        props[p["name"]] = field_schema(p, depth)
        if p.get("required") and p["name"] not in req:
            req.append(p["name"])
    s = {"type": "object", "properties": props}
    if req:
        s["required"] = req
    return s


def location(p, verb):
    pt = p.get("paramType") or "DEFAULT"
    m = {"PATH": "path", "QUERY": "query", "HEADER": "header", "FORM": "form", "FORM_DATA": "form", "BODY": "body"}
    if pt in m:
        return m[pt]
    return "query" if verb in ("GET", "DELETE", "HEAD") else "body"


def request_schema(method):
    verb = (method.get("methods") or ["GET"])[0]
    secs, whole = {}, None
    for md in method.get("request") or []:
        for p in md.get("parameters") or []:
            if p.get("hidden") or not p.get("name"):
                continue
            loc = location(p, verb)
            secs.setdefault(loc, []).append(p)
        if md.get("modelType") == "ARRAY":
            body = [p for p in md.get("parameters") or [] if location(p, verb) == "body"]
            if body:
                whole = body
                secs["body"] = [q for q in secs.get("body", []) if q not in body]
                if not secs["body"]:
                    del secs["body"]
    props, req = {}, []
    for loc in ("path", "query", "header", "form", "body"):
        if loc == "body" and whole is not None:
            props["body"] = {"type": "array", "items": object_schema(whole, 1)}
            req.append("body")
            continue
        if loc in secs:
            o = object_schema(secs[loc], 1)
            props[loc] = o
            if o.get("required"):
                req.append(loc)
    if not props:
        return None
    s = {"$schema": DRAFT, "type": "object", "properties": props}
    if req:
        s["required"] = req
    return s


def response_schema(method):
    models = method.get("response") or []
    if not models:
        return None
    md = models[0]
    mt = md.get("modelType")
    if mt == "BASE_DATA":
        s = base_schema(md.get("name"))
    elif mt == "ARRAY":
        s = {"type": "array", "items": object_schema(md.get("parameters") or [], 1)}
    else:
        s = object_schema(md.get("parameters") or [], 1)
    out = {"$schema": DRAFT}
    out.update(s)
    return out


def method_doc(method):
    paths = (method.get("apiPath") or [""])[0].rstrip("/") + "/" + ((method.get("paths") or [""])[0]).strip("/")
    paths = "/" + paths.strip("/") if paths.strip("/") else "/"
    return {
        "operationId": method["key"],
        "method": (method.get("methods") or ["GET"])[0],
        "path": paths,
        "summary": method.get("value") or "",
        "deprecated": method.get("status") == "DEPRECATED",
        "request": request_schema(method),
        "response": response_schema(method),
    }


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    bad = 0
    for f in sorted(glob.glob(os.path.join(here, "cases", "*.method.json"))):
        out = method_doc(json.load(open(f, encoding="utf-8")))
        exp = f.replace(".method.json", ".schema.json")
        if "--check" in sys.argv:
            if json.load(open(exp, encoding="utf-8")) != out:
                print("MISMATCH", exp)
                bad = 1
        else:
            open(exp, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
            print("wrote", exp)
    sys.exit(bad)


if __name__ == "__main__":
    main()
