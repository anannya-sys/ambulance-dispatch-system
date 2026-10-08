# ---------- Stage 1: build the React dashboard ----------
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------- Stage 2: build the Spring Boot jar (dashboard bundled as static files) ----------
FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /build
COPY backend/pom.xml ./
# Download dependencies in their own layer so code edits rebuild fast
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# ---------- Stage 3: small runtime image ----------
FROM eclipse-temurin:17-jre
ENV TZ=Asia/Kolkata \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseG1GC"
RUN useradd --system --uid 1001 goldenhour \
 && apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=api /build/target/ambulance-dispatch-1.0.0.jar app.jar
USER goldenhour
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
