# ==============================================================================
# 🔥 Forest Fire Spread Predictor — Production Docker Container for Render
# Problem Statement No. 86 | Java 17 OOP Cellular Automaton Engine
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build the Java backend using official Maven image
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /project

# Copy Maven POM and source files
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src

# Build shaded executable fat JAR
WORKDIR /project/backend
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# Stage 2: Minimal runtime image with Eclipse Temurin JRE 17
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the generated fat JAR from builder stage
COPY --from=builder /project/backend/target/forest-fire-predictor-1.0.0.jar ./app.jar

# Copy default datasets and frontend static assets
COPY data ./data
COPY frontend ./frontend

# Render injects PORT at runtime. 7070 is the local-development default.
ENV PORT=7070
EXPOSE 7070

# Launch the Javalin application
ENTRYPOINT ["java", "-jar", "app.jar"]
