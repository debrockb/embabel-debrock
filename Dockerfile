# Stage 1 — build the Spring Boot JAR.
# NOTE: eclipse-temurin:21-jdk-alpine's musl + Gradle native-platform
# binaries crash the JVM on some arm64 hosts ("SIGSEGV in
# libnative-platform-file-events.so") during ./gradlew. Use the glibc
# (default Debian/Ubuntu) variant — it's larger at build time but the
# runtime stage still runs on a slim JRE image, so the final image size
# is unchanged.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source code
COPY src src/

# Build the application
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

# Stage 2 — lean JRE runtime.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create data directory for SQLite
RUN mkdir -p /app/data

# Copy the built JAR
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Set JVM options for Virtual Threads and memory management
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:ZCollectionInterval=120 -Xmx4G -Xms2G"

ENTRYPOINT ["java", "-jar", "app.jar"]
