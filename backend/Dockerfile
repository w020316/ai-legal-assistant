# syntax=docker/dockerfile:1
# 多阶段构建：Maven 构建 + JRE 运行
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 配置阿里云 Maven 镜像（国内加速，避免中央仓库连接中断）
RUN mkdir -p /usr/share/maven/ref && \
    printf '%s\n' \
      '<?xml version="1.0" encoding="UTF-8"?>' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"' \
      '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
      '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>aliyun</id>' \
      '      <mirrorOf>central</mirrorOf>' \
      '      <name>Aliyun Maven Mirror</name>' \
      '      <url>https://maven.aliyun.com/repository/public</url>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' > /usr/share/maven/ref/settings.xml
COPY pom.xml .
# 使用 BuildKit cache mount 持久化 Maven 本地仓库，避免 pom 变化时重复下载所有依赖
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B -s /usr/share/maven/ref/settings.xml
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B -s /usr/share/maven/ref/settings.xml

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    mkdir -p /app/uploads /app/logs
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
