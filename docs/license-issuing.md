# 授权签发(仅发行方使用)

> **仅发行方使用。不要把本文、`license-tool` 或任何私钥交给客户。**

读者:团队版的发行方。读完能生成密钥、签发和校验授权文件,并把发行公钥打进正式版本。

## 原理

- 授权文件是一段 base64 文本,内容是授权信息(JSON)加 **Ed25519 签名**。
- 团队版 jar 里内置**发行公钥**,启动后用它校验授权;校验通过才按授权的人数和到期时间生效。
- 签名用**私钥**生成。私钥只在发行方手里,客户拿到的 jar 里只有公钥,无法自己签发。

授权信息字段:

| 字段 | 说明 |
|---|---|
| `id` | 授权编号,默认随机 UUID,可用 `--id` 指定 |
| `licensee` | 授权对象(客户名称) |
| `edition` | 固定 `TEAM` |
| `maxMembers` | 活跃用户人数上限 |
| `issuedAt` | 签发时间(毫秒) |
| `expiresAt` | 到期时间(毫秒),为 `--expires` 指定日期的当天 UTC 末尾 |

## 工具

`license-tool` 是独立的命令行 jar(Java 17),不依赖团队版 server。

构建:

```bash
mvn -B -pl license-tool -am package -DskipTests
# 产出 license-tool/target/csap-apidoc-team-license-tool-1.0.0-SNAPSHOT.jar
```

下面用 `LT` 代表这个 jar 的路径:

```bash
LT=license-tool/target/csap-apidoc-team-license-tool-1.0.0-SNAPSHOT.jar
```

### keygen:生成密钥对(只做一次)

```bash
java -jar $LT keygen --out ./keys
```

输出:

```
已生成:
  私钥(妥善保管,不要入库): ./keys/private.pem
  公钥(替换 server 的 META-INF/csap-team/license-public.pem): ./keys/public.pem
```

- 目录里已有 `private.pem` 或 `public.pem` 时不覆盖,直接报错退出。
- 私钥文件权限为 `600`;私钥不会打印到终端。

### issue:签发授权

```bash
java -jar $LT issue --key ./keys/private.pem \
    --licensee "示例公司" --max-members 50 --expires 2027-12-31 --out team.lic
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `--key` | 是 | 私钥文件 |
| `--licensee` | 是 | 授权对象名称 |
| `--max-members` | 是 | 活跃用户人数上限,正整数 |
| `--expires` | 是 | 到期日期,格式 `yyyy-MM-dd`,当天有效 |
| `--id` | 否 | 授权编号,默认随机 UUID |
| `--out` | 否 | 写入文件;不指定则把授权文本打印到标准输出 |

### verify:校验授权

```bash
java -jar $LT verify --pub ./keys/public.pem team.lic
```

签名有效时输出"授权有效"和授权内容,退出码 `0`;无效时输出"授权无效: 签名校验失败或格式错误",退出码 `1`。

交付客户前,务必**用将要发布的公钥**校验一遍。

退出码:`0` 成功,`1` 失败(例如签名无效),`2` 参数错误。

## 私钥保管要求

| 要求 | 说明 |
|---|---|
| 只在发行方保管 | 不交给客户、不放到客户环境、不放到测试或 CI 环境 |
| 不入库 | 不提交到任何 Git 仓库(包括本私有仓库);不放进镜像、日志、聊天记录、工单 |
| 离线或受控存放 | 放在加密存储或硬件密钥 / 密钥管理系统里,访问要有记录 |
| 最小人数 | 能签发的人越少越好 |
| 备份 | 至少一份加密的离线备份,放在另一处。**丢失私钥意味着无法再为已发布的版本签发授权** |
| 泄露处置 | 一旦怀疑泄露:生成新密钥对,用新公钥发布新版本,为客户重新签发授权;旧版本的授权无法吊销 |

仓库根目录的 `.gitignore` 已排除 `*.pem` 和 `*.key`(只放行内置的公钥文件),避免误提交;密钥仍应存放在仓库之外。

## 把发行公钥打进正式版本

仓库里的 `server/src/main/resources/META-INF/csap-team/license-public.pem` 是**占位公钥**,对应的私钥已销毁,所以用占位公钥的构建**不会接受任何授权**(都显示为"无效",按试用运行)。正式发行前必须替换。

步骤:

1. `keygen` 生成密钥对(只做一次,之后一直用同一对)。
2. 用 `keys/public.pem` 的内容覆盖 `server/src/main/resources/META-INF/csap-team/license-public.pem`。
3. 重新构建:

   ```bash
   mvn -B -pl server -am package -DskipTests
   ```

4. 用新 jar 做一次端到端验证:启动 → 控制台"管理 → 授权"上传刚签发的授权 → 状态应为"有效",人数和到期时间与签发时一致。
5. 用于发布的 Docker 镜像也要用这个新 jar 重新构建。

注意:

- 只提交**公钥**。提交前确认 `git diff` 里没有私钥内容。
- 公钥一旦发布给客户,就不能再换(换了,旧授权全部失效)。

### 不重新构建的办法(测试 / 内部用)

团队版支持用配置指定公钥文件,覆盖 jar 内置的公钥:

```bash
java -jar csap-apidoc-team-server-1.0.0.jar --csap.team.license-public-key-file=/path/to/public.pem
```

(环境变量 `CSAP_TEAM_LICENSEPUBLICKEYFILE`)。这适合内部测试;**面向客户不要用**,因为客户可以借此换成自己的公钥而绕过授权。

## 向客户交付

1. `issue` 生成 `team.lic`。
2. `verify` 用正式公钥校验通过。
3. 把 `team.lic` 发给客户。客户在控制台"管理 → 授权"上传,或放到服务器上用 `csap.team.license-file` 指定。
4. 记录:授权编号、客户、人数、到期日、签发日期、签发人。续期和扩容重新签发即可,客户无需重启。

## 授权的行为(给客服的参考)

| 状态 | 行为 |
|---|---|
| 没有授权 | 试用:最多 5 个活跃用户 |
| 有效 | 活跃用户上限 = `maxMembers` |
| 过期 | 已有数据和功能**不锁**;新增或启用用户受 5 人限制 |
| 无效 | 按试用处理;控制台上传无效授权会被拒绝,原授权不变 |
