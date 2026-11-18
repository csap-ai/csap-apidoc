#!/usr/bin/env bash
# 把 Java 版两套前端的构建产物同步到 ui/ 下,供 go:embed 内嵌:
#   文档页面  csap-apidoc-ui/src/main/resources/static            -> ui/static   (/csap-api.html)
#   管理页面  csap-apidoc-devtools/src/main/resources/static      -> ui/devtools (/devtools-ui)
# 两个目录就是 Java jar 里打包的产物,Go 版与 Java 版用同一份前端。
#
# 用法(在 csap-apidoc-go 目录或任意位置执行):
#   scripts/sync-ui.sh                    从当前工作区同步
#   scripts/sync-ui.sh --ref origin/main  从指定 git 提交同步(不动工作区,推荐发版前用)
#   scripts/sync-ui.sh --build            先在工作区重新构建两套前端(需要 Node 18+),再同步
#
# 同步后会打印两套前端的文件清单;提交前请运行 go test ./ui/ 确认页面引用的资源都在。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
ref=""
build=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref) ref="$2"; shift 2 ;;
    --build) build=1; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
if [[ -n "$ref" && $build == 1 ]]; then
  echo "--ref 与 --build 不能同时使用" >&2; exit 2
fi

docs_src="csap-apidoc-ui/src/main/resources/static"
dev_src="csap-apidoc-devtools/src/main/resources/static"

if [[ $build == 1 ]]; then
  (cd "$repo/csap-apidoc-ui" && npm ci && npm run build \
    && rm -rf src/main/resources/static && mkdir -p src/main/resources/static \
    && cp -R dist/. src/main/resources/static/ \
    && mv src/main/resources/static/index.html src/main/resources/static/csap-api.html)
  (cd "$repo/csap-apidoc-devtools/devtools" && npm ci && npm run build \
    && find "$repo/$dev_src" -mindepth 1 -maxdepth 1 ! -name favicon.ico -exec rm -rf {} + \
    && cp -R dist/. "$repo/$dev_src/")
fi

tmp=""
if [[ -n "$ref" ]]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  git -C "$repo" archive "$ref" "$docs_src" "$dev_src" | tar -x -C "$tmp"
  root="$tmp"
else
  root="$repo"
fi

sync_dir() {
  local src="$1" dst="$2"
  [[ -d "$src" ]] || { echo "missing $src" >&2; exit 1; }
  rm -rf "$dst"
  mkdir -p "$dst"
  cp -R "$src/." "$dst/"
  echo "synced $(find "$dst" -type f | wc -l | tr -d ' ') files -> ${dst#$here/}"
  (cd "$dst" && find . -type f | sort | sed 's/^/  /')
}

sync_dir "$root/$docs_src" "$here/ui/static"
sync_dir "$root/$dev_src" "$here/ui/devtools"
# devtools 的页面文件名统一成 index.html,由 /devtools-ui 输出
if [[ -f "$here/ui/devtools/csap-api-devtools.html" ]]; then
  mv "$here/ui/devtools/csap-api-devtools.html" "$here/ui/devtools/index.html"
fi
src_desc="${ref:-working tree}"
echo "source: $src_desc ($(git -C "$repo" rev-parse --short "${ref:-HEAD}"))"
