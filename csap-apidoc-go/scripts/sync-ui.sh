#!/usr/bin/env bash
# 把 csap-apidoc-ui 的构建产物同步到 ui/static,供 go:embed 内嵌。
# 前端源码在仓库的 csap-apidoc-ui/,构建后产物在 csap-apidoc-ui/src/main/resources/static(与 Java 版 jar 里的一致)。
# 用法:scripts/sync-ui.sh            直接同步已有产物
#      scripts/sync-ui.sh --build    先 npm run build 再同步
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
ui_src="$here/../csap-apidoc-ui"
if [[ "${1:-}" == "--build" ]]; then
  (cd "$ui_src" && npm ci && npm run build && rm -rf src/main/resources/static && mkdir -p src/main/resources/static && cp -R dist/. src/main/resources/static/ && mv src/main/resources/static/index.html src/main/resources/static/csap-api.html)
fi
src="$ui_src/src/main/resources/static"
dst="$here/ui/static"
rm -rf "$dst"
mkdir -p "$dst"
cp -R "$src/." "$dst/"
echo "synced $(find "$dst" -type f | wc -l | tr -d ' ') files from $src"
