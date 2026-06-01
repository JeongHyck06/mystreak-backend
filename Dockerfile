# syntax=docker/dockerfile:1

### Build stage ###
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐싱: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능한 jar 빌드 (테스트는 CI 단계에서 별도 수행)
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

### Runtime stage ###
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# 보안을 위해 non-root 유저로 실행
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown -R spring:spring /app
USER spring

EXPOSE 8080

# 컨테이너 메모리에 맞춰 JVM 힙 자동 조정 + 추가 옵션 주입 가능
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
