# The API as a container. Built from the repository root (it needs the extractor module):
#   docker build -f api/Dockerfile -t what-happened-here-api .
# Database settings come from the environment at run time (SPRING_DATASOURCE_URL, _USERNAME,
# _PASSWORD), never from the image.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Build files first, so dependency downloads are cached until they change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY extractor/build.gradle.kts extractor/
COPY sampler/build.gradle.kts sampler/
COPY api/build.gradle.kts api/
# settings.gradle.kts lists every subproject, so Gradle needs each one's build file to configure.
COPY importer/build.gradle.kts importer/
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet :api:dependencies
COPY extractor/src extractor/src
COPY api/src api/src
# Spring Boot also writes a "-plain" jar without dependencies; keep only the runnable one.
RUN ./gradlew --no-daemon :api:bootJar -x test \
    && cp "$(ls api/build/libs/*.jar | grep -v -- -plain)" /src/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
