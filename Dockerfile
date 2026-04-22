FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 直接复制本地打包好的 Jar，避免在 2 核 2G 服务器里做 Maven 构建占用过多内存。
ARG JAR_FILE=target/Database-ai-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENV SPRING_PROFILES_ACTIVE=server
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:+UseStringDeduplication -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
