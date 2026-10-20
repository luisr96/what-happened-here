# The full import as one container: Postgres + PostGIS and the importer side by side, so the
# staging COPY never leaves the machine. Built from the repository root:
#   docker build -f importer/Dockerfile -t what-happened-here-importer .
# Deployed as the throwaway Fly app in fly.import.toml. Settings come from the environment
# (IMPORT_SOURCE, IMPORT_LIMIT, POSTGRES_PASSWORD), never from the image.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Build files first, so dependency downloads are cached until they change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY extractor/build.gradle.kts extractor/
COPY sampler/build.gradle.kts sampler/
COPY api/build.gradle.kts api/
COPY importer/build.gradle.kts importer/
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet :importer:dependencies
COPY extractor/src extractor/src
COPY sampler/src sampler/src
COPY api/src/main/resources api/src/main/resources
COPY importer/src importer/src
# Spring Boot also writes a "-plain" jar without dependencies; keep only the runnable one.
RUN ./gradlew --no-daemon :importer:bootJar -x test \
    && cp "$(ls importer/build/libs/*.jar | grep -v -- -plain)" /src/importer.jar

# Same Postgres + PostGIS as the tests use. Its Debian has no Java 21 package, so the runtime is
# copied over from the Temurin image (both are glibc builds).
FROM postgis/postgis:18-3.6
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:$PATH" \
    PGDATA=/data/pgdata \
    POSTGRES_DB=whh
COPY --from=build /src/importer.jar /app/importer.jar
COPY importer/docker/run-import.sh /app/run-import.sh
RUN chmod +x /app/run-import.sh
ENTRYPOINT ["/app/run-import.sh"]
