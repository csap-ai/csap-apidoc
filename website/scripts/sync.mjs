// 构建前把仓库根目录的 CHANGELOG.md 同步成站点的"发布说明"页,保证只有一份来源。
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(join(here, '..', '..', 'CHANGELOG.md'), 'utf8');
const body = src
  .replace(/^# Changelog\s*\n/, '')
  // 去掉指向仓库内其他文件的相对链接(站点里不存在)
  .replace(/\]\((?!https?:|#)([^)]+)\)/g, '](https://github.com/csap-ai/csap-apidoc/blob/main/$1)');
const out = `---
title: 发布说明
---

# 发布说明

> 本页由仓库根目录的 [CHANGELOG.md](https://github.com/csap-ai/csap-apidoc/blob/main/CHANGELOG.md) 自动生成。各版本的安装包见 [Maven Central](https://central.sonatype.com/namespace/ai.csap.apidoc) 和 [GitHub Releases](https://github.com/csap-ai/csap-apidoc/releases)。

${body}`;
writeFileSync(join(here, '..', 'docs', 'changelog.md'), out);
console.log('synced CHANGELOG.md -> docs/changelog.md');
