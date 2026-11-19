# CSAP Apidoc 团队版 运行镜像
# 先在仓库根目录构建 jar(web/dist 会被打进去):  mvn -B -pl server -am package -DskipTests
# 再构建镜像:                                    docker build -f docker/Dockerfile -t csap-apidoc-team:1.0.0 .
FROM eclipse-temurin:17-jre

RUN groupadd --system csap && useradd --system --gid csap --home-dir /app csap \
    && mkdir -p /app/data && chown -R csap:csap /app
WORKDIR /app

COPY server/target/csap-apidoc-team-server-*.jar /app/app.jar

USER csap
ENV JAVA_OPTS="-Xms256m -Xmx768m" \
    SERVER_PORT=8090

EXPOSE 8090
VOLUME ["/app/data"]

# 数据库、授权等配置用环境变量(见 docker-compose 示例)或挂载 /app/config/application.yml
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
