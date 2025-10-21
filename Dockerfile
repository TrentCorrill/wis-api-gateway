# Multi-stage Dockerfile for WIS API Gateway
# Stage 1: Build the application with Gradle
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy Gradle files first for better layer caching
COPY build.gradle settings.gradle gradle.properties ./

# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port 8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Default to local profile (can be overridden)
CMD ["--spring.profiles.active=local"]
