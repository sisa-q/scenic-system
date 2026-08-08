# ============================================================
# 后端镜像：多阶段构建（构建镜像 → 运行镜像）
# 1) build 阶段：Maven 3.9 + JDK 21 打包
# 2) run 阶段：仅 JDK 21 JRE，镜像更小
# ============================================================
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
FROM ${MAVEN_IMAGE} AS build
WORKDIR /app
# 先拷贝 pom 并预下载依赖，利用 Docker 层缓存加速后续构建
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B package -DskipTests

FROM ${JRE_IMAGE}
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /app/target/houduana-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]