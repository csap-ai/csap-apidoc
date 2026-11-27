-- 与 docker-compose.multi-env.yml 配套:首次初始化时再建两个库,分别给 dev / test 环境
CREATE DATABASE app_dev OWNER csap;
CREATE DATABASE app_test OWNER csap;
