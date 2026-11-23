# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 캐시한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/*-SNAPSHOT.jar app.jar

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system ssok && useradd --system --gid ssok ssok
COPY --from=build /workspace/app.jar app.jar
USER ssok

# saved_at 오프셋과 LocalDateTime 기준 시간대를 DB(serverTimezone=Asia/Seoul)와 맞춘다.
ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
