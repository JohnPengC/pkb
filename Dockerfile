FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM node:20-alpine AS frontend-build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jre
# MarkItDown（Python）用于结构化文档转 Markdown；缺失时应用自动回退 Tika
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-pip \
 && pip3 install --no-cache-dir --break-system-packages "markitdown[all]" \
 && apt-get clean && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-build /build/target/pkb-0.1.0-SNAPSHOT.jar app.jar
COPY --from=frontend-build /build/dist ./static
ENV PKB_STORAGE_PATH=/data/storage
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", \
  "--spring.web.resources.static-locations=file:/app/static/", \
  "--spring.mvc.async.request-timeout=300000"]
