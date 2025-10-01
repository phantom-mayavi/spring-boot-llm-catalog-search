# Multi-stage Dockerfile for Spring Boot Catalog Search Application

# Build stage
FROM gradle:8.4-jdk17 AS builder

WORKDIR /app

# Copy gradle files first for better layer caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle

# Copy source code
COPY src src

# Build the application
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM openjdk:17-jre-slim

WORKDIR /app

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built jar from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to spring user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
