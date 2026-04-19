# csap-apidoc-devtools — Try-it-out Reverse Proxy (`POST /csap/apidoc/devtools/proxy`)

> Status: **opt-in**, disabled by default. Ships as part of `csap-apidoc-devtools`.

The standalone `csap-apidoc-ui` issues "Try it out" requests directly from the
browser (`fetch` → target service). When the doc UI lives on a different
origin from the target service and the target service does not return
`Access-Control-Allow-*`, the browser blocks the response and the user sees
"Failed to fetch" with no useful diagnostics. See
[`docs/cors.md`](../docs/cors.md) for the full landscape.

This module exposes a small same-origin reverse proxy the UI can call
instead of going direct. Because the proxy runs inside the host application
(same origin as the doc UI's API endpoints), no CORS preflight is involved —
the host's own CORS rules already permit the call.

---

## When to enable

✅ You operate the host app behind an authenticated, internal-only
   network boundary (corp VPN / private subnet / behind Spring Security).<br/>
✅ You need devs / QA to fire try-it-out requests from a `csap-apidoc-ui`
   instance hosted on a separate origin and you cannot ship CORS headers
   from every target service.<br/>
✅ You can maintain an explicit allowlist of hostnames the proxy is allowed
   to reach.

❌ Do **not** enable on a publicly reachable, unauthenticated host. This
   endpoint is by definition a Server-Side Request Forgery (SSRF)
   primitive. Read the security model before enabling.

---

## Security model

| Mitigation | What it does |
|---|---|
| `enabled=false` default | Bean is not registered. Endpoint returns 404. |
| `allowed-hosts` mandatory | Empty list with proxy on → every request fails with HTTP 502 / `host_not_allowed`. Fail-closed. |
| Hostname-only allowlist | No regex; literal hostnames + `*.example.com` prefix wildcards only. Case-insensitive. Port ignored. Matching is auditable. |
| `Cookie`, `X-Forwarded-For`, `X-Real-IP` stripped | Defaults; configurable via `strip-headers`. Prevents the proxy from leaking the doc UI user's session into upstream. |
| `Set-Cookie` stripped from response | Cross-origin cookies wouldn't be honoured by the browser anyway, and forwarding them leaks server state. |
| `max-body-bytes` cap | Inbound request bodies over the cap → `body_too_large` (415). Upstream responses over the cap are truncated and tagged with header `X-Csap-Apidoc-Proxy-Truncated: 1`. |
| `timeout-ms` | Upstream connect + read timeout. Prevents hanging proxy threads. |
| `log-bodies=false` default | Request / response bodies are NOT logged unless explicitly opted in. Bearer tokens stay out of log aggregation by default. |
| `X-Csap-Apidoc-Proxy: 1` response header | Lets the UI confirm the response actually came through the proxy. |

> **Recommended hardening.** Pair this proxy with Spring Security and lock
> the path `/csap/apidoc/devtools/proxy` to authenticated devtools users
> only (e.g. via a dedicated role). The proxy itself does not perform any
> authentication of the caller — that is intentional, so you can plug in
> the same auth scheme already protecting the rest of your app.

---

## Configuration

```yaml
csap:
  apidoc:
    devtools:
      proxy:
        enabled: true
        # REQUIRED when enabled. Empty = every request denied.
        allowed-hosts:
          - api.staging.example.com
          - "*.staging.example.com"
          - localhost
          - 127.0.0.1
        # 5 MiB default; covers req + resp each.
        max-body-bytes: 5242880
        # 30 s default.
        timeout-ms: 30000
        # Headers that must NEVER be forwarded upstream.
        strip-headers:
          - Cookie
          - X-Forwarded-For
          - X-Real-IP
        # Set true for short debugging windows ONLY — tokens land in logs.
        log-bodies: false
```

---

## Wire protocol

**Request** — `POST /csap/apidoc/devtools/proxy`

```json
{
  "method": "GET",
  "url": "https://api.staging.example.com/orders/42?expand=items",
  "headers": {
    "X-Tenant-Id": "demo",
    "Authorization": "Bearer eyJ..."
  },
  "body": "{\"foo\":\"bar\"}"
}
```

`body` is a raw string and is omitted for `GET` / `HEAD` even if supplied.

**Response — happy path**: HTTP status, headers, and body mirror the upstream
response, with these adjustments:
- Response gains `X-Csap-Apidoc-Proxy: 1`.
- `Set-Cookie`, `Transfer-Encoding`, `Content-Length`, `Connection`,
  `Keep-Alive`, `Upgrade`, and any `Proxy-*` headers are stripped.
- If the body was over the cap, it is truncated and the response gains
  `X-Csap-Apidoc-Proxy-Truncated: 1`.

**Response — failure** (allowlist violation, malformed URL, upstream
unreachable): HTTP **502** with a JSON envelope:

```json
{ "error": "host_not_allowed", "message": "...", "url": "..." }
```

| `error`               | when |
|---|---|
| `host_not_allowed`    | URL host is not on `allowed-hosts` |
| `body_too_large`      | Inbound request body > `max-body-bytes` (HTTP 415) |
| `invalid_url`         | URL is missing, non-absolute, or non-http(s) |
| `invalid_method`      | `method` is not a known HTTP verb |
| `invalid_request`     | `url` field missing |
| `upstream_unreachable`| Network error, timeout, DNS failure, etc. |

---

## Quick test

```bash
# 1. Enable in your application.yml (see above), restart the host app.

# 2. Confirm the endpoint exists.
curl -i -X POST http://localhost:8083/csap/apidoc/devtools/proxy \
  -H 'Content-Type: application/json' \
  -d '{"method":"GET","url":"https://api.staging.example.com/health"}'

# Look for "X-Csap-Apidoc-Proxy: 1" in the response headers.

# 3. Confirm the allowlist is enforced.
curl -i -X POST http://localhost:8083/csap/apidoc/devtools/proxy \
  -H 'Content-Type: application/json' \
  -d '{"method":"GET","url":"https://example.org/whatever"}'

# Expect HTTP 502 with {"error":"host_not_allowed",...}.
```

---

## Operational notes

- The proxy uses Spring's `RestTemplate` with a single timeout applied to
  both connect and read. Upstream errors (4xx / 5xx) are forwarded
  verbatim, **never** translated into the JSON error envelope — the JSON
  envelope is reserved for the proxy's own decisions (allowlist,
  body-cap, network failure).
- All proxy decisions are logged at INFO with host / method / status /
  latency. Headers are logged at DEBUG. Bodies are off by default; set
  `log-bodies=true` to turn them on temporarily and remember to roll back.
- The `csap-apidoc-ui` UI auto-detects this header and surfaces "via
  devtools proxy" in the response viewer so users always know whether the
  request went direct or through the proxy.
