-- CSAP Apidoc 团队版 升级脚本:schema 版本 1 -> 2 (postgresql)
-- 1.0 的库升到 1.1:新增表和列。启动时 csap.team.database.create-tables=true 会自动执行本脚本,
-- 之后再执行 Java 部分的数据迁移(服务地址迁移、修改单环境、游标改名、审计哈希链回填),全部成功后写 schema.version=2。
-- create-tables=false 时请先手工执行本脚本,再启动 1.1(数据迁移在启动时自动完成)。
-- 由 server/generate-schema.sh 从 server/upgrade-2-template.sql 生成,不要手工修改。
-- 未在真实库验证的方言:dm、kingbase。

CREATE TABLE csap_team_service_env (
    service VARCHAR(128) NOT NULL,
    env_key VARCHAR(32) NOT NULL,
    base_url VARCHAR(512),
    doc_path VARCHAR(256) NOT NULL,
    source VARCHAR(16) NOT NULL,
    last_seen_at BIGINT,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (service, env_key)
);

CREATE TABLE csap_team_env_instance (
    env_key VARCHAR(32) NOT NULL,
    service VARCHAR(128) NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    last_seen_at BIGINT NOT NULL,
    PRIMARY KEY (env_key, service, instance_id)
);

CREATE TABLE csap_team_comment (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    item_id BIGINT,
    author_id BIGINT NOT NULL,
    body TEXT,
    deleted SMALLINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX csap_team_comment_change ON csap_team_comment (change_id, id);

ALTER TABLE csap_team_change ADD COLUMN env_key VARCHAR(32);
ALTER TABLE csap_team_change ADD COLUMN source_env VARCHAR(32);
CREATE INDEX csap_team_change_env ON csap_team_change (env_key, status);
ALTER TABLE csap_team_audit ADD COLUMN env_key VARCHAR(32);
ALTER TABLE csap_team_token ADD COLUMN env_key VARCHAR(32);
ALTER TABLE csap_team_webhook ADD COLUMN env_keys VARCHAR(512);
