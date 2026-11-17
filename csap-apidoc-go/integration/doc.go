// Package integration 是需要真实数据库的集成测试,独立成模块,避免主模块引入数据库驱动。
//
//	cd integration && go test ./...                              # SQLite(纯 Go 驱动,无需外部服务)
//	CSAP_TEST_PG_DSN=postgres://... go test ./...                 # 加测 PostgreSQL
//	CSAP_TEST_MYSQL_DSN='user:pass@tcp(127.0.0.1:3306)/db' go test ./...  # 加测 MySQL
package integration
