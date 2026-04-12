FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source code
COPY src src/

# Build the application
RUN ./gradlew build -x test

# Use multi-stage build to reduce image size
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create data directory for SQLite
RUN mkdir -p /app/data

# Copy the built JAR
COPY --from=0 /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Set JVM options for Virtual Threads and memory management
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:ZCollectionInterval=120 -Xmx4G -Xms2G"

ENTRYPOINT ["java", "-jar", "app.jar"]
