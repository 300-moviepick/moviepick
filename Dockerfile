# MoviePick 백엔드 운영 이미지 (docker-compose.prod.yml이 빌드한다)
# 1단계: 소스에서 실행 가능한 jar를 만든다 (테스트는 CI에서 돌리므로 여기서는 건너뜀)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 내려받기를 소스 복사보다 먼저 해서, 소스만 바뀌었을 때 이 단계를 캐시로 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# bootJar만 실행해 실행 가능한 jar 하나만 만든다 (plain jar 없음)
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: JRE만 있는 가벼운 실행 이미지
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# root가 아닌 사용자로 실행한다
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
# JVM 메모리·시간대는 compose의 JAVA_TOOL_OPTIONS로 지정한다
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
