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
# Playwright Chromium（SPA 页面无头渲染兜底）；本地开发无浏览器时应用自动降级为可读错误
ENV PKB_JS_RENDER=true
# 从 fat jar 抽出 playwright 三个 jar 调 CLI（BOOT-INF/lib 是归档内路径，不能直接进类路径）
RUN python3 -c "import zipfile,re; z=zipfile.ZipFile('/app/app.jar'); [z.extract(n, '/tmp/pw') for n in z.namelist() if re.match(r'BOOT-INF/lib/(playwright|driver)', n)]" \
 && java -cp '/tmp/pw/BOOT-INF/lib/*' com.microsoft.playwright.CLI install --with-deps chromium \
 && rm -rf /tmp/pw
ENV PKB_STORAGE_PATH=/data/storage
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", \
  "--spring.web.resources.static-locations=file:/app/static/", \
  "--spring.mvc.async.request-timeout=300000"]
