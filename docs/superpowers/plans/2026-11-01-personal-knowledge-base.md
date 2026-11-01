# 个人知识库系统（PKB）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 2 周时间交付可演示的个人知识库系统：文档/URL → LLM 提炼 → 可编辑知识文档（版本管理）→ PGvector 检索 → RAG 问答 + MCP Server 外挂。

**Architecture:** Spring Boot 模块化单体（包级模块：provider / ingestion / distillation / knowledge / retrieval / chat / mcp），PostgreSQL 16 + PGvector 单库承载业务与向量，Vue 3 前端，Docker Compose 部署。

**Tech Stack:** Java 17、Spring Boot 3.4、Spring AI MCP starter、Apache Tika、jsoup、java-diff-utils、JUnit 5、Testcontainers、Vue 3 + Vite + Element Plus + md-editor-v3。

**关联文档：** 设计 spec 见 `docs/superpowers/specs/2026-11-01-personal-knowledge-base-design.md`。本计划对 spec 有一处有意简化：Ollama 通过其 OpenAI 兼容端点（`http://localhost:11434/v1`）接入，因此 provider 只需一套 OpenAI 兼容实现 + 两套配置预设，而非两个实现类。

## Global Constraints

- 项目根目录：`/Users/johnpeng/Code/pkb`（已 `git init`，设计文档与本计划已作为首次提交入库，直接开始 Task 1 的 Step 2）
- Java 17（`java -version` 确认）；Maven 3.9+；Node 20+；Docker 可用
- Spring Boot `3.4.5`，spring-ai-bom `1.0.1`（执行时若依赖拉取失败，升级到当时最新 3.4.x / 1.0.x 并在 commit message 中注明）
- 后端包名：`com.pkb`；后端端口 `8080`；前端 Vite 端口 `5173`（代理 `/api` → `8080`）
- 向量维度固定 **1024**；可切换的 embedding 模型必须同维度
- 检索（含 RAG、MCP）默认仅查 `CONFIRMED` 文档，可用参数放宽
- 上传文件上限 **50MB**；界面与错误文案用**中文**
- 数据迁移用 Flyway，迁移文件放 `src/main/resources/db/migration/`
- Commit 使用 Conventional Commits（如 `feat: add ingestion dedup`）；每个 Task 结束必须提交
- 所有 DB 集成测试继承 `AbstractIntegrationTest`（Testcontainers + pgvector 镜像）
- YAGNI：不做 spec「非目标」清单中的任何功能

## 文件结构总览

```
pkb/
├── pom.xml                          # Maven 单模块
├── Dockerfile                       # Task 20
├── docker-compose.yml               # Task 1（postgres）/ Task 20（加 app）
├── .env.example                     # Task 1
├── .gitignore                       # Task 1
├── README.md / LICENSE              # Task 20
├── src/main/java/com/pkb/
│   ├── PkbApplication.java
│   ├── common/
│   │   ├── config/AsyncConfig.java
│   │   ├── error/BizException.java
│   │   └── error/GlobalExceptionHandler.java
│   ├── provider/
│   │   ├── ChatProvider.java            # 接口：chat / chatStream
│   │   ├── EmbeddingProvider.java       # 接口：embed / embedAll / modelId
│   │   ├── ModelProperties.java         # @ConfigurationProperties("pkb.model")
│   │   ├── ModelConfigHolder.java       # 支持热切换 Chat 模型
│   │   ├── OpenAiCompatibleChatProvider.java
│   │   ├── OpenAiCompatibleEmbeddingProvider.java
│   │   └── dto/ (ChatRequest, ChatResponse, EmbeddingRequest, EmbeddingResponse records)
│   ├── ingestion/
│   │   ├── SourceDocument.java / SourceType.java
│   │   ├── ImportTask.java / TaskStatus.java
│   │   ├── SourceDocumentRepository.java / ImportTaskRepository.java
│   │   ├── HashUtil.java
│   │   ├── TextExtractor.java           # Apache Tika
│   │   ├── UrlFetcher.java              # jsoup
│   │   ├── IngestionService.java
│   │   ├── IngestionController.java
│   │   └── TaskRunner.java              # 异步执行 + 重试
│   ├── distillation/
│   │   ├── PromptTemplates.java
│   │   ├── DistilledDraft.java          # record(title, summary, content)
│   │   ├── DistillationService.java
│   │   └── DraftParser.java             # 解析 <<<TITLE>>> 标记格式
│   ├── knowledge/
│   │   ├── KnowledgeDocument.java / DocStatus.java
│   │   ├── KnowledgeDocumentVersion.java
│   │   ├── Tag.java
│   │   ├── KnowledgeDocumentRepository.java / VersionRepository.java / TagRepository.java
│   │   ├── KnowledgeService.java
│   │   ├── DiffService.java             # java-diff-utils
│   │   ├── DocumentSavedEvent.java
│   │   └── KnowledgeController.java
│   ├── retrieval/
│   │   ├── DocumentChunk.java
│   │   ├── ChunkingService.java
│   │   ├── ChunkRepository.java          # JdbcTemplate + pgvector
│   │   ├── IndexService.java             # 事件驱动重建单文档索引
│   │   ├── ReindexService.java           # 全量重建（模型切换）
│   │   ├── RetrievalService.java
│   │   ├── SearchHit.java                # record(documentId, title, snippet, score)
│   │   ├── RetrievalController.java
│   │   └── AdminController.java          # 模型状态 / 热切换 / 全量重建
│   ├── chat/
│   │   ├── ChatService.java
│   │   └── ChatController.java           # GET /api/chat SSE
│   └── mcp/
│       └── KnowledgeMcpTools.java        # @Tool 三个工具
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__init.sql
├── src/test/java/com/pkb/
│   ├── AbstractIntegrationTest.java
│   └── ...（各模块测试，见各 Task）
├── src/test/resources/
│   └── samples/ (sample.txt, sample.md, sample.docx 由测试代码生成)
└── frontend/
    ├── package.json / vite.config.js / index.html
    └── src/
        ├── main.js / App.vue / router/index.js
        ├── api/ (client.js, ingest.js, docs.js, search.js)
        └── views/ (ImportView.vue, DocsView.vue, DocDetailView.vue, ChatView.vue)
```

---

### Task 1: 项目骨架与基础设施

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/pkb/PkbApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `.gitignore`
- Test: `src/test/java/com/pkb/PkbApplicationTests.java`

**Interfaces:**
- Consumes: 无
- Produces: 可启动的 Spring Boot 应用（`8080`）、可用的 Postgres（`localhost:5432`，库 `pkb`，用户 `pkb`，密码 `pkb`）

- [ ] **Step 1: 初始化仓库与目录**

```bash
mkdir -p /Users/johnpeng/Code/pkb && cd /Users/johnpeng/Code/pkb
git init
mkdir -p src/main/java/com/pkb src/main/resources/db/migration src/test/java/com/pkb src/test/resources/samples
```

- [ ] **Step 2: 编写 `pom.xml`（完整内容）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.5</version>
        <relativePath/>
    </parent>
    <groupId>com.pkb</groupId>
    <artifactId>pkb</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.1</spring-ai.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-core</artifactId>
            <version>2.9.2</version>
        </dependency>
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-parsers-standard-package</artifactId>
            <version>2.9.2</version>
        </dependency>
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.18.1</version>
        </dependency>
        <dependency>
            <groupId>io.github.jbellis</groupId>
            <artifactId>java-diff-utils</artifactId>
            <version>4.12</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 编写 `PkbApplication.java` 与 `application.yml`**

```java
package com.pkb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class PkbApplication {
    public static void main(String[] args) {
        SpringApplication.run(PkbApplication.class, args);
    }
}
```

```yaml
server:
  port: 8080

spring:
  application:
    name: pkb
  datasource:
    url: jdbc:postgresql://localhost:5432/pkb
    username: pkb
    password: pkb
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
  jackson:
    default-property-inclusion: non_null

management:
  endpoints:
    web:
      exposure:
        include: health

pkb:
  storage-path: ${PKB_STORAGE_PATH:./storage}
  model:
    chat:
      base-url: ${CHAT_BASE_URL:https://api.moonshot.cn/v1}
      api-key: ${CHAT_API_KEY:changeme}
      model: ${CHAT_MODEL:moonshot-v1-8k}
    embedding:
      base-url: ${EMBEDDING_BASE_URL:https://api.moonshot.cn/v1}
      api-key: ${EMBEDDING_API_KEY:changeme}
      model: ${EMBEDDING_MODEL:text-embedding-3-small}
      dimension: 1024
```

> 注：`pkb.model.embedding.dimension` 仅作声明与校验用途，表结构维度在 Flyway 中固定为 1024。

- [ ] **Step 4: 编写 `docker-compose.yml`、`.env.example`、`.gitignore`**

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: pkb-postgres
    environment:
      POSTGRES_DB: pkb
      POSTGRES_USER: pkb
      POSTGRES_PASSWORD: pkb
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pkb"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  pgdata:
```

```bash
# .env.example
# 云端模型（默认 Kimi；也可填 DeepSeek 等任何 OpenAI 兼容端点）
CHAT_BASE_URL=https://api.moonshot.cn/v1
CHAT_API_KEY=sk-your-key
CHAT_MODEL=moonshot-v1-8k
EMBEDDING_BASE_URL=https://api.moonshot.cn/v1
EMBEDDING_API_KEY=sk-your-key
EMBEDDING_MODEL=text-embedding-3-small
# 本地 Ollama 预设：改用以下值即可（Ollama 提供 OpenAI 兼容端点）
# CHAT_BASE_URL=http://localhost:11434/v1
# CHAT_API_KEY=ollama
# CHAT_MODEL=qwen2.5:7b
# EMBEDDING_BASE_URL=http://localhost:11434/v1
# EMBEDDING_API_KEY=ollama
# EMBEDDING_MODEL=bge-m3
PKB_STORAGE_PATH=./storage
```

```gitignore
target/
*.class
*.jar
!.mvn/wrapper/*.jar
.idea/
.vscode/
*.iml
.DS_Store
storage/
node_modules/
frontend/dist/
.env
```

- [ ] **Step 5: 写第一个测试（先失败）**

```java
package com.pkb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PkbApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -DskipTests=false`
Expected: **FAIL** — 数据库连不上（Postgres 未启动），Flyway 报错。

- [ ] **Step 6: 启动 Postgres，让测试通过**

```bash
cd /Users/johnpeng/Code/pkb
docker compose up -d postgres
docker compose ps   # 等待 healthy
mvn -q test
```

Expected: 测试仍可能因无 Flyway 迁移 + `ddl-auto: validate` 失败——这是预期，Task 2 补齐 schema 后变绿。本步验证：应用能连上数据库（错误从"连接拒绝"变为"validate 失败"）。

- [ ] **Step 7: 验证启动并提交**

```bash
mvn spring-boot:run &
sleep 20 && curl -s http://localhost:8080/actuator/health
kill %1
```

Expected health 输出包含 `"status":"UP"`（db 组件可能是 DOWN 直到 Task 2，这是预期）。

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "chore: project skeleton with compose postgres"
```

---

### Task 2: 数据库 Schema（Flyway V1）

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Test: `src/test/java/com/pkb/AbstractIntegrationTest.java`
- Test: `src/test/java/com/pkb/SchemaMigrationTest.java`

**Interfaces:**
- Consumes: Task 1 的 Spring Boot 骨架
- Produces: 6 张表（`source_document`、`import_task`、`knowledge_document`、`knowledge_document_version`、`document_chunk`、`tag` + `document_tag`），`vector` 扩展；所有后续 Task 的 JPA 实体映射以此 schema 为准

- [ ] **Step 1: 编写迁移 SQL（完整内容）**

```sql
-- V1__init.sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE source_document (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type   VARCHAR(10)  NOT NULL CHECK (source_type IN ('FILE', 'URL')),
    file_name     VARCHAR(512),
    url           TEXT,
    content_hash  VARCHAR(64)  NOT NULL,
    storage_path  VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_source_document_hash ON source_document (content_hash);

CREATE TABLE import_task (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id  UUID NOT NULL REFERENCES source_document (id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','EXTRACTING','DISTILLING','COMPLETED','FAILED')),
    error_message       TEXT,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_task_status ON import_task (status);

CREATE TABLE knowledge_document (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id  UUID NOT NULL UNIQUE REFERENCES source_document (id),
    title               VARCHAR(512) NOT NULL,
    summary             TEXT,
    content             TEXT NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED')),
    current_version     INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_document_status ON knowledge_document (status);

CREATE TABLE knowledge_document_version (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES knowledge_document (id),
    version_no   INT NOT NULL,
    content      TEXT NOT NULL,
    change_note  VARCHAR(512),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, version_no)
);

CREATE TABLE document_chunk (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    chunk_index      INT NOT NULL,
    content          TEXT NOT NULL,
    embedding        vector(1024),
    embedding_model  VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_chunk_document ON document_chunk (document_id);
CREATE INDEX idx_document_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE tag (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE document_tag (
    document_id UUID NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);
```

- [ ] **Step 2: 编写集成测试基类**

```java
package com.pkb;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
}
```

同时创建 `src/test/resources/application-test.yml`：

```yaml
pkb:
  storage-path: ${java.io.tmpdir}/pkb-test-storage
  model:
    chat:
      base-url: http://localhost:1
      api-key: test
      model: test-chat
    embedding:
      base-url: http://localhost:1
      api-key: test
      model: test-embedding
      dimension: 1024
```

- [ ] **Step 3: 编写 Schema 验证测试（先运行确认失败）**

```java
package com.pkb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAllTablesAndVectorExtension() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).contains(
                "source_document", "import_task", "knowledge_document",
                "knowledge_document_version", "document_chunk", "tag", "document_tag");

        String ext = jdbc.queryForObject(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class);
        assertThat(ext).isEqualTo("vector");

        Integer dim = jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'",
                Integer.class);
        // pgvector 把维度存为 atttypmod
        assertThat(dim).isEqualTo(1024);
    }
}
```

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=SchemaMigrationTest`
Expected: **PASS**（Flyway 在测试启动时自动执行 V1）。同时重跑 `mvn -q test`：`PkbApplicationTests.contextLoads` 也应通过（本地 compose 的 postgres 在跑）。注意 `contextLoads` 是 `@SpringBootTest` 但未继承基类——修改它继承 `AbstractIntegrationTest`：

```java
class PkbApplicationTests extends AbstractIntegrationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: database schema via flyway V1"
```

---

### Task 3: 模型 Provider 抽象（Chat + Embedding）

**Files:**
- Create: `src/main/java/com/pkb/provider/ChatProvider.java`
- Create: `src/main/java/com/pkb/provider/EmbeddingProvider.java`
- Create: `src/main/java/com/pkb/provider/ModelProperties.java`
- Create: `src/main/java/com/pkb/provider/ModelConfigHolder.java`
- Create: `src/main/java/com/pkb/provider/OpenAiCompatibleChatProvider.java`
- Create: `src/main/java/com/pkb/provider/OpenAiCompatibleEmbeddingProvider.java`
- Create: `src/main/java/com/pkb/provider/dto/OpenAiDtos.java`
- Create: `src/main/java/com/pkb/common/error/BizException.java`
- Test: `src/test/java/com/pkb/provider/OpenAiCompatibleProviderTest.java`

**Interfaces:**
- Consumes: `pkb.model.*` 配置（Task 1）
- Produces（后续 Task 依赖的精确签名）:
  - `ChatProvider`: `String chat(String systemPrompt, String userPrompt)`；`Flux<String> chatStream(String systemPrompt, String userPrompt)`
  - `EmbeddingProvider`: `float[] embed(String text)`；`List<float[]> embedAll(List<String> texts)`；`String modelId()`
  - `ModelConfigHolder`: `ModelProperties.ModelEndpoint currentChat()`；`ModelProperties.ModelEndpoint currentEmbedding()`；`void switchChat(String baseUrl, String apiKey, String model)`
  - `BizException extends RuntimeException`：构造 `BizException(String message)`

- [ ] **Step 1: 编写接口与配置类**

```java
// ChatProvider.java
package com.pkb.provider;

import reactor.core.publisher.Flux;

public interface ChatProvider {
    String chat(String systemPrompt, String userPrompt);
    Flux<String> chatStream(String systemPrompt, String userPrompt);
}
```

```java
// EmbeddingProvider.java
package com.pkb.provider;

import java.util.List;

public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
    /** 当前 embedding 模型标识，写入 document_chunk.embedding_model */
    String modelId();
}
```

```java
// ModelProperties.java
package com.pkb.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pkb.model")
public record ModelProperties(ModelEndpoint chat, ModelEndpoint embedding) {
    public record ModelEndpoint(String baseUrl, String apiKey, String model) {
    }
}
```

```java
// ModelConfigHolder.java
package com.pkb.provider;

import org.springframework.stereotype.Component;

/**
 * 运行期模型配置持有者。Chat 模型支持热切换（Task 12 的管理接口写这里）；
 * Embedding 不允许静默热切换——必须走 Task 12 的全量重建流程。
 */
@Component
public class ModelConfigHolder {

    private volatile ModelProperties.ModelEndpoint chat;
    private final ModelProperties.ModelEndpoint embedding;

    public ModelConfigHolder(ModelProperties properties) {
        this.chat = properties.chat();
        this.embedding = properties.embedding();
    }

    public ModelProperties.ModelEndpoint currentChat() {
        return chat;
    }

    public ModelProperties.ModelEndpoint currentEmbedding() {
        return embedding;
    }

    public void switchChat(String baseUrl, String apiKey, String model) {
        this.chat = new ModelProperties.ModelEndpoint(baseUrl, apiKey, model);
    }
}
```

```java
// BizException.java
package com.pkb.common.error;

/** 业务异常：message 直接面向用户展示（中文）。 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 编写 DTO 与实现类**

```java
// dto/OpenAiDtos.java
package com.pkb.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class OpenAiDtos {

    public record ChatRequest(String model, List<Message> messages, boolean stream) {
        public record Message(String role, String content) {
        }
    }

    public record ChatResponse(List<Choice> choices) {
        public record Choice(Message message, Delta delta) {
            public record Message(String content) {
            }
            public record Delta(String content) {
            }
        }
    }

    public record EmbeddingRequest(String model, List<String> input) {
    }

    public record EmbeddingResponse(List<Entry> data) {
        public record Entry(@JsonProperty("embedding") List<Double> embedding) {
        }
    }

    private OpenAiDtos() {
    }
}
```

```java
// OpenAiCompatibleChatProvider.java
package com.pkb.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkb.common.error.BizException;
import com.pkb.provider.dto.OpenAiDtos.ChatRequest;
import com.pkb.provider.dto.OpenAiDtos.ChatResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleChatProvider implements ChatProvider {

    private final ModelConfigHolder holder;
    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleChatProvider(ModelConfigHolder holder,
                                        RestClient.Builder restBuilder,
                                        WebClient.Builder webBuilder) {
        this.holder = holder;
        this.restClient = restBuilder.build();
        this.webClient = webBuilder.build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        var ep = holder.currentChat();
        ChatRequest body = new ChatRequest(ep.model(), List.of(
                new ChatRequest.Message("system", systemPrompt),
                new ChatRequest.Message("user", userPrompt)), false);
        try {
            ChatResponse resp = restClient.post()
                    .uri(ep.baseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
            if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
                throw new BizException("模型返回为空，请稍后重试");
            }
            return resp.choices().get(0).message().content();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用模型服务失败：" + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        var ep = holder.currentChat();
        Map<String, Object> body = Map.of(
                "model", ep.model(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        return webClient.post()
                .uri(ep.baseUrl() + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + ep.apiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<String>() {
                })
                .takeUntil("[DONE]"::equals)
                .filter(line -> !"[DONE]".equals(line) && !line.isBlank())
                .mapNotNull(this::extractDelta)
                .onErrorMap(e -> new BizException("调用模型服务失败：" + e.getMessage()));
    }

    private String extractDelta(String dataLine) {
        try {
            JsonNode node = objectMapper.readTree(dataLine);
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
```

```java
// OpenAiCompatibleEmbeddingProvider.java
package com.pkb.provider;

import com.pkb.common.error.BizException;
import com.pkb.provider.dto.OpenAiDtos.EmbeddingRequest;
import com.pkb.provider.dto.OpenAiDtos.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final ModelConfigHolder holder;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingProvider(ModelConfigHolder holder, RestClient.Builder builder) {
        this.holder = holder;
        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        var ep = holder.currentEmbedding();
        try {
            EmbeddingResponse resp = restClient.post()
                    .uri(ep.baseUrl() + "/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ep.apiKey())
                    .body(new EmbeddingRequest(ep.model(), texts))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (resp == null || resp.data() == null || resp.data().size() != texts.size()) {
                throw new BizException("向量化返回数量不匹配，请稍后重试");
            }
            return resp.data().stream().map(entry -> {
                float[] v = new float[entry.embedding().size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = entry.embedding().get(i).floatValue();
                }
                return v;
            }).toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用向量化服务失败：" + e.getMessage());
        }
    }

    @Override
    public String modelId() {
        return holder.currentEmbedding().model();
    }
}
```

- [ ] **Step 3: 编写契约测试（MockWebServer）**

```java
// OpenAiCompatibleProviderTest.java
package com.pkb.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleProviderTest {

    private MockWebServer server;
    private ModelConfigHolder holder;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/v1").toString().replaceAll("/$", "");
        holder = new ModelConfigHolder(new ModelProperties(
                new ModelProperties.ModelEndpoint(base, "test-key", "chat-model"),
                new ModelProperties.ModelEndpoint(base, "test-key", "embed-model")));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void chatReturnsContent() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(om.writeValueAsString(Map.of("choices", List.of(
                        Map.of("message", Map.of("content", "你好")))))));
        var provider = new OpenAiCompatibleChatProvider(holder, RestClient.builder(), WebClient.builder());
        assertThat(provider.chat("sys", "user")).isEqualTo("你好");
    }

    @Test
    void embedReturnsFloatArray() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(om.writeValueAsString(Map.of("data", List.of(
                        Map.of("embedding", List.of(0.1, 0.2, 0.3)))))));
        var provider = new OpenAiCompatibleEmbeddingProvider(holder, RestClient.builder());
        assertThat(provider.embed("文本")).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(provider.modelId()).isEqualTo("embed-model");
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=OpenAiCompatibleProviderTest`
Expected: 2 tests PASS

- [ ] **Step 5: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: model provider abstraction with openai-compatible impl"
```

---

### Task 4: 导入模块——实体、去重与任务创建

**Files:**
- Create: `src/main/java/com/pkb/ingestion/SourceType.java`
- Create: `src/main/java/com/pkb/ingestion/TaskStatus.java`
- Create: `src/main/java/com/pkb/ingestion/SourceDocument.java`
- Create: `src/main/java/com/pkb/ingestion/ImportTask.java`
- Create: `src/main/java/com/pkb/ingestion/SourceDocumentRepository.java`
- Create: `src/main/java/com/pkb/ingestion/ImportTaskRepository.java`
- Create: `src/main/java/com/pkb/ingestion/HashUtil.java`
- Create: `src/main/java/com/pkb/ingestion/IngestionService.java`
- Create: `src/main/java/com/pkb/ingestion/IngestionController.java`
- Test: `src/test/java/com/pkb/ingestion/IngestionServiceTest.java`

**Interfaces:**
- Consumes: `BizException`（Task 3）
- Produces:
  - `IngestionService`: `ImportTask createFromFile(MultipartFile file)`；`ImportTask createFromUrl(String url)`；`List<ImportTask> listTasks()`；`ImportTask retry(UUID taskId)`
  - `SourceDocument`：字段 `id, sourceType, fileName, url, contentHash, storagePath, createdAt`
  - `ImportTask`：字段 `id, sourceDocument, status, errorMessage, retryCount, createdAt, updatedAt`；`void markStatus(TaskStatus)`；`void fail(String message)`
  - 端点：`POST /api/ingest/file`、`POST /api/ingest/url`、`GET /api/ingest/tasks`、`POST /api/ingest/tasks/{id}/retry`

- [ ] **Step 1: 编写枚举与实体**

```java
// SourceType.java
package com.pkb.ingestion;

public enum SourceType {
    FILE, URL
}
```

```java
// TaskStatus.java
package com.pkb.ingestion;

public enum TaskStatus {
    PENDING, EXTRACTING, DISTILLING, COMPLETED, FAILED
}
```

```java
// SourceDocument.java
package com.pkb.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "source_document")
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private SourceType sourceType;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

```java
// ImportTask.java
package com.pkb.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "import_task")
public class ImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocument sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public void markStatus(TaskStatus next) {
        this.status = next;
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(String message) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = message;
        this.updatedAt = OffsetDateTime.now();
    }
}
```

```java
// SourceDocumentRepository.java
package com.pkb.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {
    Optional<SourceDocument> findByContentHash(String contentHash);
}
```

```java
// ImportTaskRepository.java
package com.pkb.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportTaskRepository extends JpaRepository<ImportTask, UUID> {
    List<ImportTask> findAllByOrderByCreatedAtDesc();
}
```

```java
// HashUtil.java
package com.pkb.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private HashUtil() {
    }
}
```

- [ ] **Step 2: 编写服务与控制器**

```java
// IngestionService.java
package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("docx", "pdf", "txt", "md");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final SourceDocumentRepository sourceRepository;
    private final ImportTaskRepository taskRepository;
    private final TaskLauncher taskLauncher;

    @Value("${pkb.storage-path}")
    private String storagePath;

    @Transactional
    public ImportTask createFromFile(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new BizException("不支持的文件格式：" + ext + "，目前支持 docx/pdf/txt/md");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件超过 50MB 上限");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String hash = HashUtil.sha256(bytes);
        var existing = sourceRepository.findByContentHash(hash);
        if (existing.isPresent()) {
            throw new BizException("该内容已导入过（来源：" + describe(existing.get()) + "），请勿重复导入");
        }
        Path stored = store(bytes, ext);
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName(original);
        doc.setContentHash(hash);
        doc.setStoragePath(stored.toString());
        sourceRepository.save(doc);
        return createAndLaunch(doc);
    }

    @Transactional
    public ImportTask createFromUrl(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BizException("URL 不合法，请输入 http(s) 链接");
        }
        byte[] html = fetchRaw(url);
        String hash = HashUtil.sha256(html);
        var existing = sourceRepository.findByContentHash(hash);
        if (existing.isPresent()) {
            throw new BizException("该内容已导入过（来源：" + describe(existing.get()) + "），请勿重复导入");
        }
        Path stored = store(html, "html");
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.URL);
        doc.setUrl(url);
        doc.setContentHash(hash);
        doc.setStoragePath(stored.toString());
        sourceRepository.save(doc);
        return createAndLaunch(doc);
    }

    public List<ImportTask> listTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public ImportTask retry(UUID taskId) {
        ImportTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException("任务不存在"));
        if (task.getStatus() != TaskStatus.FAILED) {
            throw new BizException("仅失败的任务可以重试");
        }
        task.setRetryCount(0);
        task.setErrorMessage(null);
        task.markStatus(TaskStatus.PENDING);
        launchAfterCommit(task.getId());
        return task;
    }

    private ImportTask createAndLaunch(SourceDocument doc) {
        ImportTask task = new ImportTask();
        task.setSourceDocument(doc);
        taskRepository.save(task);
        launchAfterCommit(task.getId());
        return task;
    }

    /** 事务提交后再触发异步执行，避免异步线程读到未提交的任务记录。 */
    private void launchAfterCommit(UUID taskId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskLauncher.launch(taskId);
            }
        });
    }

    private Path store(byte[] bytes, String ext) {
        try {
            Path dir = Path.of(storagePath);
            Files.createDirectories(dir);
            Path file = dir.resolve(UUID.randomUUID() + "." + ext);
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] fetchRaw(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; pkb/0.1)")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw new BizException("抓取失败：HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("抓取失败：" + e.getMessage());
        }
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String describe(SourceDocument doc) {
        return doc.getSourceType() == SourceType.FILE ? doc.getFileName() : doc.getUrl();
    }
}
```

`TaskLauncher` 是一个薄封装，避免自调用导致 `@Async` 失效，真正的执行逻辑在 Task 7 的 `TaskRunner` 中：

```java
// TaskLauncher.java
package com.pkb.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskLauncher {

    private final TaskRunner taskRunner;

    @Async
    public void launch(UUID taskId) {
        taskRunner.run(taskId);
    }
}
```

> Task 4 编译时需要 `TaskRunner` 已存在——先在本 Task 创建一个占位实现（Step 3），Task 7 再替换为完整实现。

```java
// ingestion/TaskRunner.java（本 Task 的占位版，Task 7 替换）
package com.pkb.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskRunner {

    private final ImportTaskRepository taskRepository;

    public void run(UUID taskId) {
        // Task 7 实现：抽取 → 提炼 → 生成草稿 → 向量化
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        task.fail("提炼流程尚未实现");
        taskRepository.save(task);
    }
}
```

```java
// IngestionController.java
package com.pkb.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/file")
    public TaskView upload(@RequestParam("file") MultipartFile file) {
        return TaskView.from(ingestionService.createFromFile(file));
    }

    @PostMapping("/url")
    public TaskView submitUrl(@RequestBody Map<String, String> body) {
        return TaskView.from(ingestionService.createFromUrl(body.get("url")));
    }

    @GetMapping("/tasks")
    public List<TaskView> tasks() {
        return ingestionService.listTasks().stream().map(TaskView::from).toList();
    }

    @PostMapping("/tasks/{id}/retry")
    public TaskView retry(@PathVariable UUID id) {
        return TaskView.from(ingestionService.retry(id));
    }

    public record TaskView(UUID id, String status, String errorMessage, int retryCount,
                           String sourceLabel, String createdAt, String updatedAt) {
        static TaskView from(ImportTask task) {
            var doc = task.getSourceDocument();
            String label = doc.getSourceType() == SourceType.FILE ? doc.getFileName() : doc.getUrl();
            return new TaskView(task.getId(), task.getStatus().name(), task.getErrorMessage(),
                    task.getRetryCount(), label,
                    task.getCreatedAt().toString(), task.getUpdatedAt().toString());
        }
    }
}
```

- [ ] **Step 3: 编写测试（去重与校验逻辑）**

```java
// IngestionServiceTest.java
package com.pkb.ingestion;

import com.pkb.AbstractIntegrationTest;
import com.pkb.common.error.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class IngestionServiceTest extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Test
    void rejectsUnsupportedExtension() {
        var file = new MockMultipartFile("file", "a.exe", "application/octet-stream", "x".getBytes());
        assertThatThrownBy(() -> ingestionService.createFromFile(file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的文件格式");
    }

    @Test
    void rejectsDuplicateContent() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "相同内容".getBytes());
        ingestionService.createFromFile(file);
        var same = new MockMultipartFile("file", "b.txt", "text/plain", "相同内容".getBytes());
        assertThatThrownBy(() -> ingestionService.createFromFile(same))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已导入过");
    }

    @Test
    void createsPendingTask() {
        var file = new MockMultipartFile("file", "c.txt", "text/plain", "内容c".getBytes());
        ImportTask task = ingestionService.createFromFile(file);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.getSourceDocument().getContentHash()).hasSize(64);
    }
}
```

> 注意：`launchAfterCommit` 依赖事务提交后回调——`@Transactional` 的测试默认回滚、永不提交，因此异步执行在测试中不会触发，任务保持创建时的 PENDING 状态，测试天然隔离、无需额外配置。

- [ ] **Step 4: 运行测试**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=IngestionServiceTest`
Expected: 3 tests PASS

- [ ] **Step 5: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: ingestion entities, dedup and task endpoints"
```

---

### Task 5: 文本抽取（Tika + jsoup）

**Files:**
- Create: `src/main/java/com/pkb/ingestion/TextExtractor.java`
- Create: `src/main/java/com/pkb/ingestion/UrlFetcher.java`
- Create: `src/test/resources/samples/sample.txt`
- Create: `src/test/resources/samples/sample.md`
- Test: `src/test/java/com/pkb/ingestion/TextExtractorTest.java`
- Test: `src/test/java/com/pkb/ingestion/UrlFetcherTest.java`

**Interfaces:**
- Consumes: `BizException`
- Produces:
  - `TextExtractor`: `String extract(Path file)` — 对 FILE 类型来源，Tika 抽取纯文本
  - `UrlFetcher`: `String extract(Path htmlFile)` — 对 URL 类型来源，jsoup 从已存 HTML 中提取正文（标题 + 正文文本，返回格式 `# 标题\n\n正文`）

- [ ] **Step 1: 编写实现**

```java
// TextExtractor.java
package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextExtractor {

    private final Tika tika = new Tika();

    public String extract(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            String text = tika.parseToString(in);
            if (text == null || text.isBlank()) {
                throw new BizException("未能从文件中抽取到文本内容");
            }
            return text.strip();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文本抽取失败：" + e.getMessage());
        }
    }
}
```

```java
// UrlFetcher.java
package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从已抓取的 HTML 文件中提取正文。
 * 策略：优先 article 标签 → main 标签 → body；移除 script/style/nav/footer 等噪音。
 * 仅支持服务端渲染页面（MVP 边界，见 spec 1.3）。
 */
@Component
public class UrlFetcher {

    public String extract(Path htmlFile) {
        try {
            String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);
            doc.select("script, style, nav, footer, header, aside, noscript").remove();
            Element main = firstNonNull(doc.selectFirst("article"),
                    doc.selectFirst("main"),
                    doc.body());
            if (main == null) {
                throw new BizException("未能从页面中提取正文内容");
            }
            String text = main.text().strip();
            if (text.isBlank()) {
                throw new BizException("页面正文为空，可能是需要 JS 渲染的页面（MVP 暂不支持）");
            }
            String title = doc.title() == null ? "" : doc.title().strip();
            return title.isEmpty() ? text : "# " + title + "\n\n" + text;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取页面文件失败：" + e.getMessage());
        }
    }

    private Element firstNonNull(Element... candidates) {
        for (Element e : candidates) {
            if (e != null) {
                return e;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: 编写测试资源与测试**

`src/test/resources/samples/sample.txt`：

```
个人知识库系统是本次冲刺的演示项目。
它把分散的资料提炼成知识文档，并提供检索与问答能力。
```

`src/test/resources/samples/sample.md`：

```markdown
# 示例文档

## 背景

这是一份用于测试的 Markdown 文档。

- 要点一
- 要点二
```

```java
// TextExtractorTest.java
package com.pkb.ingestion;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsTxt() throws Exception {
        URL resource = getClass().getResource("/samples/sample.txt");
        String text = extractor.extract(Path.of(resource.toURI()));
        assertThat(text).contains("个人知识库系统").contains("检索与问答");
    }

    @Test
    void extractsMarkdown() throws Exception {
        URL resource = getClass().getResource("/samples/sample.md");
        String text = extractor.extract(Path.of(resource.toURI()));
        assertThat(text).contains("示例文档").contains("要点一");
    }
}
```

```java
// UrlFetcherTest.java
package com.pkb.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UrlFetcherTest {

    private final UrlFetcher fetcher = new UrlFetcher();

    @Test
    void extractsArticleText(@TempDir Path dir) throws Exception {
        String html = """
                <html><head><title>测试页面</title></head><body>
                <nav>导航噪音</nav>
                <article><h1>文章标题</h1><p>正文第一段。</p><p>正文第二段。</p></article>
                <script>var x = 1;</script>
                </body></html>
                """;
        Path file = dir.resolve("page.html");
        Files.writeString(file, html);
        String result = fetcher.extract(file);
        assertThat(result).startsWith("# 测试页面");
        assertThat(result).contains("正文第一段");
        assertThat(result).doesNotContain("导航噪音");
        assertThat(result).doesNotContain("var x");
    }
}
```

- [ ] **Step 3: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest='TextExtractorTest,UrlFetcherTest'`
Expected: 3 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: text extraction via tika and jsoup"
```

---

### Task 6: 知识提炼（Distillation）

**Files:**
- Create: `src/main/java/com/pkb/distillation/PromptTemplates.java`
- Create: `src/main/java/com/pkb/distillation/DistilledDraft.java`
- Create: `src/main/java/com/pkb/distillation/DraftParser.java`
- Create: `src/main/java/com/pkb/distillation/DistillationService.java`
- Test: `src/test/java/com/pkb/distillation/DraftParserTest.java`
- Test: `src/test/java/com/pkb/distillation/DistillationServiceTest.java`

**Interfaces:**
- Consumes: `ChatProvider.chat(String, String)`（Task 3）
- Produces:
  - `DistilledDraft`: record，字段 `String title, String summary, String content`
  - `DistillationService`: `DistilledDraft distill(String rawText, String sourceHint)` — 超长自动分段提炼再汇总（`sourceHint` 为文件名或 URL，写入 Prompt 供溯源）
  - `DraftParser`: `DistilledDraft parse(String llmOutput, String fallbackTitle)`

- [ ] **Step 1: 编写 Prompt 模板与结果类型**

```java
// PromptTemplates.java
package com.pkb.distillation;

public final class PromptTemplates {

    public static final String DISTILL_SYSTEM = """
            你是一名严谨的知识整理助手。把用户提供的原始资料提炼为结构化的 Markdown 知识文档。
            要求：
            1. 忠实于原文，禁止编造原文没有的事实；不确定的内容写入"待确认"小节。
            2. 使用简体中文；术语保留原文。
            3. 严格按以下标记输出（不要输出任何其他内容）：
            <<<TITLE>>>
            （一句话标题）
            <<<SUMMARY>>>
            （100 字以内摘要）
            <<<CONTENT>>>
            （Markdown 正文，包含：## 摘要、## 核心要点（列表）、## 关键术语（表格：术语|解释）、## 待确认（可无））
            """;

    public static final String SEGMENT_SYSTEM = """
            你是一名知识整理助手。以下是一份长资料的第 %d 段（共 %d 段）。
            提取本段的关键事实、要点和术语，用简体中文列表输出，忠实原文，禁止编造。
            """;

    public static final String AGGREGATE_SYSTEM = """
            你是一名知识整理助手。以下是一份长资料分段提取的笔记汇总。
            请将它们整合为一篇结构化的 Markdown 知识文档，去重、理顺逻辑，忠实于笔记内容，禁止编造。
            严格按以下标记输出（不要输出任何其他内容）：
            <<<TITLE>>>
            （一句话标题）
            <<<SUMMARY>>>
            （100 字以内摘要）
            <<<CONTENT>>>
            （Markdown 正文，包含：## 摘要、## 核心要点（列表）、## 关键术语（表格：术语|解释）、## 待确认（可无））
            """;

    private PromptTemplates() {
    }
}
```

```java
// DistilledDraft.java
package com.pkb.distillation;

public record DistilledDraft(String title, String summary, String content) {
}
```

- [ ] **Step 2: 编写解析器（先写测试）**

```java
// DraftParserTest.java
package com.pkb.distillation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftParserTest {

    private final DraftParser parser = new DraftParser();

    @Test
    void parsesWellFormedOutput() {
        String output = """
                <<<TITLE>>>
                信贷系统架构要点
                <<<SUMMARY>>>
                介绍新一代信贷系统的模块划分。
                <<<CONTENT>>>
                ## 摘要
                正文内容。
                """;
        DistilledDraft draft = parser.parse(output, "兜底标题");
        assertThat(draft.title()).isEqualTo("信贷系统架构要点");
        assertThat(draft.summary()).isEqualTo("介绍新一代信贷系统的模块划分。");
        assertThat(draft.content()).contains("## 摘要");
    }

    @Test
    void fallsBackWhenMarkersMissing() {
        DistilledDraft draft = parser.parse("没有任何标记的输出", "兜底标题");
        assertThat(draft.title()).isEqualTo("兜底标题");
        assertThat(draft.summary()).isEmpty();
        assertThat(draft.content()).isEqualTo("没有任何标记的输出");
    }

    @Test
    void trimsBlankTitleToFallback() {
        String output = """
                <<<TITLE>>>
                <<<SUMMARY>>>
                摘要。
                <<<CONTENT>>>
                正文。
                """;
        DistilledDraft draft = parser.parse(output, "兜底标题");
        assertThat(draft.title()).isEqualTo("兜底标题");
    }
}
```

```java
// DraftParser.java
package com.pkb.distillation;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DraftParser {

    private static final Pattern PATTERN = Pattern.compile(
            "<<<TITLE>>>\\s*(.*?)\\s*<<<SUMMARY>>>\\s*(.*?)\\s*<<<CONTENT>>>\\s*(.*)",
            Pattern.DOTALL);

    public DistilledDraft parse(String llmOutput, String fallbackTitle) {
        if (llmOutput == null) {
            return new DistilledDraft(fallbackTitle, "", "");
        }
        Matcher m = PATTERN.matcher(llmOutput);
        if (!m.find()) {
            return new DistilledDraft(fallbackTitle, "", llmOutput.strip());
        }
        String title = m.group(1).strip();
        if (title.isEmpty()) {
            title = fallbackTitle;
        }
        return new DistilledDraft(title, m.group(2).strip(), m.group(3).strip());
    }
}
```

- [ ] **Step 3: 编写提炼服务（含分段逻辑测试）**

```java
// DistillationServiceTest.java
package com.pkb.distillation;

import com.pkb.provider.ChatProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DistillationServiceTest {

    @Test
    void shortTextDistillsInSingleCall() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider chat = stub(calls, """
                <<<TITLE>>>
                标题
                <<<SUMMARY>>>
                摘要
                <<<CONTENT>>>
                正文
                """);
        var service = new DistillationService(chat, new DraftParser());
        DistilledDraft draft = service.distill("一段不长的资料内容", "a.txt");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(draft.title()).isEqualTo("标题");
    }

    @Test
    void longTextUsesSegmentThenAggregate() {
        AtomicInteger calls = new AtomicInteger();
        ChatProvider chat = stub(calls, """
                <<<TITLE>>>
                汇总标题
                <<<SUMMARY>>>
                汇总摘要
                <<<CONTENT>>>
                汇总正文
                """);
        var service = new DistillationService(chat, new DraftParser());
        String longText = "长文本段落。".repeat(3000); // > 单段阈值
        DistilledDraft draft = service.distill(longText, "b.txt");
        assertThat(calls.get()).isGreaterThan(2); // 多段提炼 + 1 次汇总
        assertThat(draft.title()).isEqualTo("汇总标题");
    }

    private ChatProvider stub(AtomicInteger calls, String response) {
        return new ChatProvider() {
            @Override
            public String chat(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return response;
            }

            @Override
            public Flux<String> chatStream(String systemPrompt, String userPrompt) {
                return Flux.just(response);
            }
        };
    }
}
```

```java
// DistillationService.java
package com.pkb.distillation;

import com.pkb.provider.ChatProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DistillationService {

    /** 单段直接提炼的字符阈值；超过则分段提炼后汇总 */
    static final int SINGLE_SHOT_THRESHOLD = 12_000;
    /** 分段大小（字符） */
    static final int SEGMENT_SIZE = 8_000;

    private final ChatProvider chatProvider;
    private final DraftParser draftParser;

    public DistilledDraft distill(String rawText, String sourceHint) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.length() <= SINGLE_SHOT_THRESHOLD) {
            String userPrompt = "资料来源：" + sourceHint + "\n\n原始资料：\n" + text;
            return draftParser.parse(chatProvider.chat(PromptTemplates.DISTILL_SYSTEM, userPrompt), sourceHint);
        }
        List<String> segments = split(text);
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String system = PromptTemplates.SEGMENT_SYSTEM.formatted(i + 1, segments.size());
            notes.add(chatProvider.chat(system, segments.get(i)));
        }
        StringBuilder joined = new StringBuilder("资料来源：" + sourceHint + "\n\n");
        for (int i = 0; i < notes.size(); i++) {
            joined.append("【第 ").append(i + 1).append(" 段笔记】\n").append(notes.get(i)).append("\n\n");
        }
        return draftParser.parse(
                chatProvider.chat(PromptTemplates.AGGREGATE_SYSTEM, joined.toString()), sourceHint);
    }

    private List<String> split(String text) {
        List<String> segments = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int to = Math.min(from + SEGMENT_SIZE, text.length());
            // 尽量在换行处切断，避免句子被拦腰截断
            if (to < text.length()) {
                int newline = text.lastIndexOf('\n', to);
                if (newline > from + SEGMENT_SIZE / 2) {
                    to = newline;
                }
            }
            segments.add(text.substring(from, to));
            from = to;
        }
        return segments;
    }
}
```

- [ ] **Step 4: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest='DraftParserTest,DistillationServiceTest'`
Expected: 5 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: distillation service with segment-aggregate for long text"
```

---

### Task 7: 异步任务执行器（TaskRunner 完整实现）

**Files:**
- Modify: `src/main/java/com/pkb/ingestion/TaskRunner.java`（替换 Task 4 的占位实现）
- Create: `src/main/java/com/pkb/knowledge/KnowledgeService.java` 的 `createDraft` 部分（完整 KnowledgeService 在 Task 8，本 Task 只建最小可用版）
- Create: `src/main/java/com/pkb/knowledge/KnowledgeDocument.java`
- Create: `src/main/java/com/pkb/knowledge/DocStatus.java`
- Create: `src/main/java/com/pkb/knowledge/KnowledgeDocumentRepository.java`
- Create: `src/main/java/com/pkb/knowledge/DocumentSavedEvent.java`
- Test: `src/test/java/com/pkb/ingestion/TaskRunnerTest.java`

**Interfaces:**
- Consumes: `TextExtractor.extract(Path)`、`UrlFetcher.extract(Path)`（Task 5）；`DistillationService.distill(String, String)`（Task 6）
- Produces:
  - `TaskRunner.run(UUID taskId)`：完整执行 抽取→提炼→建草稿→发 `DocumentSavedEvent`→COMPLETED；失败重试 3 次（间隔 2s/4s/8s）后 FAILED
  - `KnowledgeService.createDraft(SourceDocument, DistilledDraft): KnowledgeDocument`（v1，DRAFT）
  - `KnowledgeDocument` 实体：字段 `id, sourceDocument, title, summary, content, status, currentVersion, createdAt, updatedAt`
  - `DocumentSavedEvent`: record，`UUID documentId()`

- [ ] **Step 1: 编写 Knowledge 最小实现（实体 + createDraft）**

```java
// knowledge/DocStatus.java
package com.pkb.knowledge;

public enum DocStatus {
    DRAFT, CONFIRMED
}
```

```java
// knowledge/KnowledgeDocument.java
package com.pkb.knowledge;

import com.pkb.ingestion.SourceDocument;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false, unique = true)
    private SourceDocument sourceDocument;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DocStatus status = DocStatus.DRAFT;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
```

```java
// knowledge/KnowledgeDocumentRepository.java
package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
}
```

```java
// knowledge/DocumentSavedEvent.java
package com.pkb.knowledge;

import java.util.UUID;

/** 文档创建或保存后发布，触发（重新）向量化。 */
public record DocumentSavedEvent(UUID documentId) {
}
```

```java
// knowledge/KnowledgeService.java（本 Task 最小版，Task 8 扩展）
package com.pkb.knowledge;

import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.SourceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 由提炼结果创建 v1 草稿，并触发首次向量化事件。 */
    @Transactional
    public KnowledgeDocument createDraft(SourceDocument source, DistilledDraft draft) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setSourceDocument(source);
        doc.setTitle(draft.title());
        doc.setSummary(draft.summary());
        doc.setContent(draft.content());
        documentRepository.save(doc);

        KnowledgeDocumentVersion v1 = new KnowledgeDocumentVersion();
        v1.setDocument(doc);
        v1.setVersionNo(1);
        v1.setContent(draft.content());
        v1.setChangeNote("AI 提炼生成");
        versionRepository.save(v1);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }
}
```

```java
// knowledge/KnowledgeDocumentVersion.java
package com.pkb.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "knowledge_document_version")
public class KnowledgeDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "change_note", length = 512)
    private String changeNote;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

```java
// knowledge/VersionRepository.java
package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<KnowledgeDocumentVersion, UUID> {
    List<KnowledgeDocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersionNo(UUID documentId, int versionNo);
}
```

- [ ] **Step 2: 编写 TaskRunner 完整实现（替换占位）**

```java
// ingestion/TaskRunner.java
package com.pkb.ingestion;

import com.pkb.distillation.DistillationService;
import com.pkb.distillation.DistilledDraft;
import com.pkb.knowledge.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRunner {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {2_000, 4_000, 8_000};

    private final ImportTaskRepository taskRepository;
    private final TextExtractor textExtractor;
    private final UrlFetcher urlFetcher;
    private final DistillationService distillationService;
    private final KnowledgeService knowledgeService;
    private final TransactionTemplate transactionTemplate;

    public void run(UUID taskId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                execute(taskId);
                return;
            } catch (Exception e) {
                log.warn("任务 {} 第 {} 次执行失败: {}", taskId, attempt, e.getMessage());
                markAttemptFailed(taskId, attempt, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    return;
                }
                sleep(BACKOFF_MS[attempt - 1]);
            }
        }
    }

    private void execute(UUID taskId) {
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        SourceDocument source = task.getSourceDocument();

        task.markStatus(TaskStatus.EXTRACTING);
        taskRepository.save(task);

        Path stored = Path.of(source.getStoragePath());
        String rawText = source.getSourceType() == SourceType.FILE
                ? textExtractor.extract(stored)
                : urlFetcher.extract(stored);

        task.markStatus(TaskStatus.DISTILLING);
        taskRepository.save(task);

        String hint = source.getSourceType() == SourceType.FILE ? source.getFileName() : source.getUrl();
        DistilledDraft draft = distillationService.distill(rawText, hint);

        // createDraft 内部发布 DocumentSavedEvent 触发首次向量化
        transactionTemplate.executeWithoutResult(tx -> knowledgeService.createDraft(source, draft));

        task.markStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
    }

    private void markAttemptFailed(UUID taskId, int attempt, String message) {
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        task.setRetryCount(attempt);
        if (attempt == MAX_ATTEMPTS) {
            task.fail(message);
        }
        taskRepository.save(task);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

> 注意：`knowledgeService.createDraft` 本身有 `@Transactional`，但 TaskRunner 由 `@Async` 线程调用、不经代理链时事务仍正常（createDraft 是被代理的 bean 方法）。这里用 `TransactionTemplate` 显式包裹只是为了意图清晰，两者保留其一即可——保留 TransactionTemplate 写法。

- [ ] **Step 3: 编写测试（状态机 + 重试）**

```java
// ingestion/TaskRunnerTest.java
package com.pkb.ingestion;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistillationService;
import com.pkb.distillation.DistilledDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TaskRunnerTest extends AbstractIntegrationTest {

    @Autowired
    TaskRunner taskRunner;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    ImportTaskRepository taskRepository;

    @MockitoBean
    DistillationService distillationService;

    @Test
    void completesTaskAndCreatesDraft() throws Exception {
        SourceDocument doc = newFileSource("runner-ok.txt", "任务执行器测试内容");
        ImportTask task = newTask(doc);
        when(distillationService.distill(anyString(), anyString()))
                .thenReturn(new DistilledDraft("提炼标题", "摘要", "## 摘要\n正文"));

        taskRunner.run(task.getId());

        ImportTask done = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.getErrorMessage()).isNull();
    }

    @Test
    void failsAfterThreeAttempts() throws Exception {
        SourceDocument doc = newFileSource("runner-fail.txt", "失败测试内容");
        ImportTask task = newTask(doc);
        when(distillationService.distill(anyString(), anyString()))
                .thenThrow(new RuntimeException("模型超时"));

        taskRunner.run(task.getId()); // 内部重试 3 次，backoff 共约 14 秒

        ImportTask failed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(3);
        assertThat(failed.getErrorMessage()).contains("模型超时");
    }

    private SourceDocument newFileSource(String name, String content) throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "pkb-test-storage");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path file = dir.resolve(name);
        java.nio.file.Files.writeString(file, content);
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName(name);
        doc.setContentHash(HashUtil.sha256(content + name));
        doc.setStoragePath(file.toString());
        return sourceRepository.save(doc);
    }

    private ImportTask newTask(SourceDocument doc) {
        ImportTask task = new ImportTask();
        task.setSourceDocument(doc);
        return taskRepository.save(task);
    }
}
```

> 失败用例内部真实 sleep 共约 14 秒（2+4+8 backoff），属预期耗时。
> 注：`IngestionServiceTest`（Task 4）是 `@Transactional` 测试，afterCommit 回调不会触发，异步执行被天然隔离，与本 Task 的完整实现无冲突。

- [ ] **Step 4: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=TaskRunnerTest`
Expected: 2 tests PASS（失败用例约 14 秒）

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: async task runner with retry and draft creation"
```

---

### Task 8: 知识文档——CRUD、版本快照、标签、确认

**Files:**
- Modify: `src/main/java/com/pkb/knowledge/KnowledgeService.java`（扩展 Task 7 的最小版）
- Create: `src/main/java/com/pkb/knowledge/Tag.java`
- Create: `src/main/java/com/pkb/knowledge/TagRepository.java`
- Create: `src/main/java/com/pkb/knowledge/KnowledgeController.java`
- Create: `src/main/java/com/pkb/knowledge/dto/DocViews.java`
- Test: `src/test/java/com/pkb/knowledge/KnowledgeServiceTest.java`

**Interfaces:**
- Consumes: Task 7 的 `createDraft`、`DocumentSavedEvent`
- Produces:
  - `KnowledgeService` 新增：
    - `Page<KnowledgeDocument> list(DocStatus status, String tag, String keyword, Pageable pageable)`
    - `KnowledgeDocument get(UUID id)`
    - `KnowledgeDocument save(UUID id, String content, String changeNote)` — 版本 +1，首次保存置 CONFIRMED，发 `DocumentSavedEvent`
    - `KnowledgeDocument confirm(UUID id)` — 不改正文直接置 CONFIRMED
    - `List<KnowledgeDocumentVersion> listVersions(UUID id)`
    - `KnowledgeDocumentVersion getVersion(UUID id, int versionNo)`
    - `KnowledgeDocument setTags(UUID id, List<String> tagNames)`
    - `void delete(UUID id)`
  - 端点（`KnowledgeController`）：
    - `GET /api/docs?status=&tag=&keyword=&page=&size=`
    - `GET /api/docs/{id}`、`PUT /api/docs/{id}`、`POST /api/docs/{id}/confirm`
    - `GET /api/docs/{id}/versions`、`GET /api/docs/{id}/versions/{versionNo}`
    - `PUT /api/docs/{id}/tags`、`DELETE /api/docs/{id}`

- [ ] **Step 1: 标签实体与仓库**

```java
// knowledge/Tag.java
package com.pkb.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;
}
```

```java
// knowledge/TagRepository.java
package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    @Query(value = "SELECT t.* FROM tag t JOIN document_tag dt ON dt.tag_id = t.id WHERE dt.document_id = :documentId",
           nativeQuery = true)
    List<Tag> findByDocumentId(@Param("documentId") UUID documentId);

    @Query(value = "SELECT DISTINCT t.* FROM tag t JOIN document_tag dt ON dt.tag_id = t.id ORDER BY t.name",
           nativeQuery = true)
    List<Tag> findAllInUse();

    @Modifying
    @Query(value = "DELETE FROM document_tag WHERE document_id = :documentId", nativeQuery = true)
    void deleteDocumentTags(@Param("documentId") UUID documentId);

    @Modifying
    @Query(value = "INSERT INTO document_tag (document_id, tag_id) VALUES (:documentId, :tagId) ON CONFLICT DO NOTHING",
           nativeQuery = true)
    void link(@Param("documentId") UUID documentId, @Param("tagId") UUID tagId);
}
```

- [ ] **Step 2: 扩展 KnowledgeService（完整替换版）**

```java
// knowledge/KnowledgeService.java
package com.pkb.knowledge;

import com.pkb.common.error.BizException;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.SourceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VersionRepository versionRepository;
    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 由提炼结果创建 v1 草稿，并触发首次向量化事件。 */
    @Transactional
    public KnowledgeDocument createDraft(SourceDocument source, DistilledDraft draft) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setSourceDocument(source);
        doc.setTitle(draft.title());
        doc.setSummary(draft.summary());
        doc.setContent(draft.content());
        documentRepository.save(doc);

        KnowledgeDocumentVersion v1 = new KnowledgeDocumentVersion();
        v1.setDocument(doc);
        v1.setVersionNo(1);
        v1.setContent(draft.content());
        v1.setChangeNote("AI 提炼生成");
        versionRepository.save(v1);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeDocument> list(DocStatus status, String tag, String keyword, Pageable pageable) {
        return documentRepository.search(
                status == null ? null : status.name(),
                keyword == null || keyword.isBlank() ? null : keyword.strip(),
                tag == null || tag.isBlank() ? null : tag.strip(),
                pageable);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument get(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BizException("知识文档不存在"));
    }

    /** 保存编辑内容：版本 +1；首次保存自动确认。 */
    @Transactional
    public KnowledgeDocument save(UUID id, String content, String changeNote) {
        KnowledgeDocument doc = get(id);
        int nextVersion = doc.getCurrentVersion() + 1;
        doc.setContent(content);
        doc.setCurrentVersion(nextVersion);
        doc.setStatus(DocStatus.CONFIRMED);
        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocument(doc);
        version.setVersionNo(nextVersion);
        version.setContent(content);
        version.setChangeNote(changeNote);
        versionRepository.save(version);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }

    /** 不改正文，直接确认草稿。 */
    @Transactional
    public KnowledgeDocument confirm(UUID id) {
        KnowledgeDocument doc = get(id);
        doc.setStatus(DocStatus.CONFIRMED);
        doc.setUpdatedAt(OffsetDateTime.now());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentVersion> listVersions(UUID id) {
        get(id);
        return versionRepository.findByDocumentIdOrderByVersionNoDesc(id);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentVersion getVersion(UUID id, int versionNo) {
        return versionRepository.findByDocumentIdAndVersionNo(id, versionNo)
                .orElseThrow(() -> new BizException("版本不存在：v" + versionNo));
    }

    @Transactional
    public KnowledgeDocument setTags(UUID id, List<String> tagNames) {
        KnowledgeDocument doc = get(id);
        tagRepository.deleteDocumentTags(id);
        for (String name : tagNames) {
            String clean = name.strip();
            if (clean.isEmpty()) {
                continue;
            }
            Tag tag = tagRepository.findByName(clean).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(clean);
                return tagRepository.save(t);
            });
            tagRepository.link(id, tag.getId());
        }
        return doc;
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        documentRepository.deleteById(id);
    }
}
```

`KnowledgeDocumentRepository`（替换 Task 7 版本，新增原生 `search` 方法）：

```java
// knowledge/KnowledgeDocumentRepository.java
package com.pkb.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    @Query(value = """
            SELECT d.* FROM knowledge_document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:keyword IS NULL OR d.title ILIKE '%' || :keyword || '%' OR d.summary ILIKE '%' || :keyword || '%')
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM document_tag dt JOIN tag t ON t.id = dt.tag_id
                    WHERE dt.document_id = d.id AND t.name = :tag))
            ORDER BY d.updated_at DESC
            """,
           countQuery = """
            SELECT count(*) FROM knowledge_document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:keyword IS NULL OR d.title ILIKE '%' || :keyword || '%' OR d.summary ILIKE '%' || :keyword || '%')
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM document_tag dt JOIN tag t ON t.id = dt.tag_id
                    WHERE dt.document_id = d.id AND t.name = :tag))
            """,
           nativeQuery = true)
    Page<KnowledgeDocument> search(@Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("tag") String tag,
                                   Pageable pageable);
}
```

- [ ] **Step 3: 控制器与视图 DTO**

```java
// knowledge/dto/DocViews.java
package com.pkb.knowledge.dto;

import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeDocumentVersion;

import java.util.UUID;

public final class DocViews {

    public record DocSummary(UUID id, String title, String summary, String status,
                             int currentVersion, String updatedAt) {
        public static DocSummary from(KnowledgeDocument d) {
            return new DocSummary(d.getId(), d.getTitle(), d.getSummary(),
                    d.getStatus().name(), d.getCurrentVersion(), d.getUpdatedAt().toString());
        }
    }

    public record DocDetail(UUID id, String title, String summary, String content, String status,
                            int currentVersion, String sourceLabel, String updatedAt) {
        public static DocDetail from(KnowledgeDocument d) {
            var src = d.getSourceDocument();
            String label = src.getSourceType() == com.pkb.ingestion.SourceType.FILE
                    ? src.getFileName() : src.getUrl();
            return new DocDetail(d.getId(), d.getTitle(), d.getSummary(), d.getContent(),
                    d.getStatus().name(), d.getCurrentVersion(), label, d.getUpdatedAt().toString());
        }
    }

    public record VersionView(int versionNo, String changeNote, String createdAt) {
        public static VersionView from(KnowledgeDocumentVersion v) {
            return new VersionView(v.getVersionNo(), v.getChangeNote(), v.getCreatedAt().toString());
        }
    }

    private DocViews() {
    }
}
```

```java
// knowledge/KnowledgeController.java
package com.pkb.knowledge;

import com.pkb.knowledge.dto.DocViews.DocDetail;
import com.pkb.knowledge.dto.DocViews.DocSummary;
import com.pkb.knowledge.dto.DocViews.VersionView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final TagRepository tagRepository;

    @GetMapping
    public Page<DocSummary> list(@RequestParam(required = false) DocStatus status,
                                 @RequestParam(required = false) String tag,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return knowledgeService.list(status, tag, keyword, PageRequest.of(page, size))
                .map(DocSummary::from);
    }

    @GetMapping("/{id}")
    public DocDetail detail(@PathVariable UUID id) {
        return DocDetail.from(knowledgeService.get(id));
    }

    @PutMapping("/{id}")
    public DocDetail save(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return DocDetail.from(knowledgeService.save(id, body.get("content"), body.get("changeNote")));
    }

    @PostMapping("/{id}/confirm")
    public DocDetail confirm(@PathVariable UUID id) {
        return DocDetail.from(knowledgeService.confirm(id));
    }

    @GetMapping("/{id}/versions")
    public List<VersionView> versions(@PathVariable UUID id) {
        return knowledgeService.listVersions(id).stream().map(VersionView::from).toList();
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public Map<String, Object> version(@PathVariable UUID id, @PathVariable int versionNo) {
        var v = knowledgeService.getVersion(id, versionNo);
        return Map.of("versionNo", v.getVersionNo(), "content", v.getContent(),
                "changeNote", v.getChangeNote() == null ? "" : v.getChangeNote());
    }

    @PutMapping("/{id}/tags")
    public DocDetail setTags(@PathVariable UUID id, @RequestBody Map<String, List<String>> body) {
        return DocDetail.from(knowledgeService.setTags(id, body.getOrDefault("tags", List.of())));
    }

    @GetMapping("/tags")
    public List<String> allTags() {
        return tagRepository.findAllInUse().stream().map(Tag::getName).toList();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        knowledgeService.delete(id);
    }
}
```

- [ ] **Step 4: 编写版本管理测试**

```java
// knowledge/KnowledgeServiceTest.java
package com.pkb.knowledge;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class KnowledgeServiceTest extends AbstractIntegrationTest {

    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    VersionRepository versionRepository;

    @Test
    void saveCreatesNewVersionAndConfirms() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(), 
                new DistilledDraft("标题", "摘要", "v1 内容"));
        assertThat(doc.getStatus()).isEqualTo(DocStatus.DRAFT);

        knowledgeService.save(doc.getId(), "v2 内容", "修正错别字");

        KnowledgeDocument saved = knowledgeService.get(doc.getId());
        assertThat(saved.getCurrentVersion()).isEqualTo(2);
        assertThat(saved.getContent()).isEqualTo("v2 内容");
        assertThat(saved.getStatus()).isEqualTo(DocStatus.CONFIRMED);
        assertThat(versionRepository.findByDocumentIdOrderByVersionNoDesc(doc.getId()))
                .hasSize(2);
    }

    @Test
    void confirmWithoutEdit() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "内容"));
        KnowledgeDocument confirmed = knowledgeService.confirm(doc.getId());
        assertThat(confirmed.getStatus()).isEqualTo(DocStatus.CONFIRMED);
        assertThat(confirmed.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void setTagsCreatesAndLinks() {
        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "内容"));
        knowledgeService.setTags(doc.getId(), List.of("java", " rag ", "java"));
        assertThat(knowledgeService.get(doc.getId())).isNotNull();
        // 通过 list 按标签过滤验证
        var page = knowledgeService.list(null, "rag", null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(KnowledgeDocument::getId).contains(doc.getId());
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("k-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
```

- [ ] **Step 5: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=KnowledgeServiceTest`
Expected: 3 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: knowledge document crud, versioning and tags"
```

---

### Task 9: 版本 diff 与回滚

**Files:**
- Create: `src/main/java/com/pkb/knowledge/DiffService.java`
- Create: `src/main/java/com/pkb/knowledge/DiffLine.java`
- Modify: `src/main/java/com/pkb/knowledge/KnowledgeController.java`（追加两个端点）
- Test: `src/test/java/com/pkb/knowledge/DiffServiceTest.java`

**Interfaces:**
- Consumes: `KnowledgeService.getVersion`、`KnowledgeService.save`（Task 8）
- Produces:
  - `DiffLine`: record，`String type()`（`ADD`/`DEL`/`KEEP`）、`String text()`
  - `DiffService`: `List<DiffLine> diff(String oldContent, String newContent)`
  - 端点：`GET /api/docs/{id}/diff?from=1&to=2`、`POST /api/docs/{id}/rollback/{versionNo}`

- [ ] **Step 1: 编写 DiffService（先写测试）**

```java
// DiffServiceTest.java
package com.pkb.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffServiceTest {

    private final DiffService diffService = new DiffService();

    @Test
    void marksAddedDeletedKeptLines() {
        List<DiffLine> lines = diffService.diff("保留行\n删除行", "保留行\n新增行");
        assertThat(lines).containsExactly(
                new DiffLine("KEEP", "保留行"),
                new DiffLine("DEL", "删除行"),
                new DiffLine("ADD", "新增行"));
    }

    @Test
    void identicalContentAllKept() {
        List<DiffLine> lines = diffService.diff("一样\n内容", "一样\n内容");
        assertThat(lines).allMatch(l -> l.type().equals("KEEP"));
    }
}
```

```java
// knowledge/DiffLine.java
package com.pkb.knowledge;

/** type: ADD / DEL / KEEP */
public record DiffLine(String type, String text) {
}
```

```java
// knowledge/DiffService.java
package com.pkb.knowledge;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DiffService {

    public List<DiffLine> diff(String oldContent, String newContent) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);

        boolean[] oldChanged = new boolean[oldLines.size()];
        boolean[] newChanged = new boolean[newLines.size()];
        List<DiffLine> result = new ArrayList<>();

        // 先输出所有行（KEEP），再按 delta 修正；简单起见按位置重放
        int oldPos = 0;
        int newPos = 0;
        List<AbstractDelta<String>> deltas = patch.getDeltas();
        for (AbstractDelta<String> delta : deltas) {
            int oldStart = delta.getSource().getPosition();
            int newStart = delta.getTarget().getPosition();
            while (oldPos < oldStart) {
                result.add(new DiffLine("KEEP", oldLines.get(oldPos++)));
                newPos++;
            }
            for (String line : delta.getSource().getLines()) {
                result.add(new DiffLine("DEL", line));
                oldPos++;
            }
            for (String line : delta.getTarget().getLines()) {
                result.add(new DiffLine("ADD", line));
                newPos++;
            }
        }
        while (oldPos < oldLines.size()) {
            result.add(new DiffLine("KEEP", oldLines.get(oldPos++)));
        }
        return result;
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\n", -1));
    }
}
```

- [ ] **Step 2: 追加控制器端点**

在 `KnowledgeController` 类中追加（字段区加 `private final DiffService diffService;`）：

```java
    @GetMapping("/{id}/diff")
    public List<DiffLine> diff(@PathVariable UUID id,
                               @RequestParam int from,
                               @RequestParam int to) {
        String oldContent = knowledgeService.getVersion(id, from).getContent();
        String newContent = knowledgeService.getVersion(id, to).getContent();
        return diffService.diff(oldContent, newContent);
    }

    @PostMapping("/{id}/rollback/{versionNo}")
    public DocDetail rollback(@PathVariable UUID id, @PathVariable int versionNo) {
        var version = knowledgeService.getVersion(id, versionNo);
        return DocDetail.from(knowledgeService.save(
                id, version.getContent(), "回滚到 v" + versionNo));
    }
```

- [ ] **Step 3: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=DiffServiceTest`
Expected: 2 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: version diff and rollback"
```

---

### Task 10: 切分与向量化（Chunking + Index）

**Files:**
- Create: `src/main/java/com/pkb/retrieval/ChunkingService.java`
- Create: `src/main/java/com/pkb/retrieval/ChunkRepository.java`
- Create: `src/main/java/com/pkb/retrieval/IndexService.java`
- Test: `src/test/java/com/pkb/retrieval/ChunkingServiceTest.java`
- Test: `src/test/java/com/pkb/retrieval/IndexServiceTest.java`

**Interfaces:**
- Consumes: `EmbeddingProvider.embedAll(List<String>)`、`EmbeddingProvider.modelId()`（Task 3）；`DocumentSavedEvent`（Task 7）
- Produces:
  - `ChunkingService`: `List<String> split(String markdown)` — 按标题/空行切，目标块 ≤ 600 字符，合并碎块
  - `IndexService`: `void reindex(UUID documentId)` — 删旧块→切分→embedding→写库（`@Async` + `AFTER_COMMIT` 事件监听，失败保留旧块并记日志）
  - `ChunkRepository`（JdbcTemplate）:
    - `void deleteByDocumentId(UUID documentId)`
    - `void insertChunks(UUID documentId, List<String> contents, List<float[]> vectors, String modelId)`
    - `List<ChunkRow> vectorSearch(float[] queryVector, int limit, boolean confirmedOnly)`
    - `List<ChunkRow> keywordSearch(String keyword, int limit, boolean confirmedOnly)`
    - `String findDistinctEmbeddingModel()`
    - `List<UUID> findAllDocumentIds()`
  - `ChunkRow`: record，`UUID id, UUID documentId, String content, double distance`

- [ ] **Step 1: 编写切分服务（先写测试）**

```java
// ChunkingServiceTest.java
package com.pkb.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService chunking = new ChunkingService();

    @Test
    void splitsByHeadings() {
        String md = "## 第一节\n" + "内容一。".repeat(50) + "\n\n## 第二节\n" + "内容二。".repeat(50);
        List<String> chunks = chunking.split(md);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).contains("第一节");
        assertThat(chunks.get(chunks.size() - 1)).contains("第二节");
    }

    @Test
    void mergesTinyFragments() {
        List<String> chunks = chunking.split("短句一。\n\n短句二。\n\n短句三。");
        assertThat(chunks).hasSize(1);
    }

    @Test
    void splitsOversizedParagraph() {
        String longParagraph = "无换行长文本。".repeat(200); // 单段 1400 字符
        List<String> chunks = chunking.split(longParagraph);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(600));
    }
}
```

```java
// retrieval/ChunkingService.java
package com.pkb.retrieval;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 切分：先按标题/空行切段，小于合并阈值的相邻段合并，
 * 超过目标大小的段按字符硬切（优先在句号/换行处断开）。
 */
@Service
public class ChunkingService {

    static final int TARGET_SIZE = 600;

    public List<String> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> raw = splitRaw(markdown);
        List<String> merged = mergeSmall(raw);
        List<String> result = new ArrayList<>();
        for (String piece : merged) {
            if (piece.length() <= TARGET_SIZE) {
                result.add(piece);
            } else {
                result.addAll(hardSplit(piece));
            }
        }
        return result.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private List<String> splitRaw(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean isHeading = line.strip().startsWith("#");
            boolean isBlank = line.isBlank();
            if (isHeading && current.length() > 0) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
            if (isBlank && current.length() >= TARGET_SIZE) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private List<String> mergeSmall(List<String> pieces) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String piece : pieces) {
            if (buffer.length() + piece.length() <= TARGET_SIZE) {
                buffer.append(piece);
            } else {
                if (buffer.length() > 0) {
                    merged.add(buffer.toString());
                }
                buffer = new StringBuilder(piece);
            }
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }
        return merged;
    }

    private List<String> hardSplit(String piece) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        while (from < piece.length()) {
            int to = Math.min(from + TARGET_SIZE, piece.length());
            if (to < piece.length()) {
                int cut = Math.max(piece.lastIndexOf('。', to), piece.lastIndexOf('\n', to));
                if (cut > from + TARGET_SIZE / 2) {
                    to = cut + 1;
                }
            }
            parts.add(piece.substring(from, to));
            from = to;
        }
        return parts;
    }
}
```

- [ ] **Step 2: 编写 ChunkRepository（JdbcTemplate + pgvector）**

```java
// retrieval/ChunkRow.java
package com.pkb.retrieval;

import java.util.UUID;

public record ChunkRow(UUID id, UUID documentId, String content, double distance) {
}
```

```java
// retrieval/ChunkRepository.java
package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    private final JdbcTemplate jdbc;

    public void deleteByDocumentId(UUID documentId) {
        jdbc.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    }

    public void insertChunks(UUID documentId, List<String> contents, List<float[]> vectors, String modelId) {
        String sql = "INSERT INTO document_chunk (document_id, chunk_index, content, embedding, embedding_model) "
                + "VALUES (?, ?, ?, ?::vector, ?)";
        for (int i = 0; i < contents.size(); i++) {
            jdbc.update(sql, documentId, i, contents.get(i), toVectorLiteral(vectors.get(i)), modelId);
        }
    }

    public List<ChunkRow> vectorSearch(float[] queryVector, int limit, boolean confirmedOnly) {
        String sql = """
                SELECT c.id, c.document_id, c.content, (c.embedding <=> ?::vector) AS distance
                FROM document_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                %s
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """.formatted(confirmedOnly ? "WHERE d.status = 'CONFIRMED'" : "");
        return jdbc.query(sql, (rs, rowNum) -> new ChunkRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getDouble("distance")), toVectorLiteral(queryVector), toVectorLiteral(queryVector), limit);
    }

    public List<ChunkRow> keywordSearch(String keyword, int limit, boolean confirmedOnly) {
        String sql = """
                SELECT c.id, c.document_id, c.content, 0.5 AS distance
                FROM document_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                WHERE c.content ILIKE '%' || ? || '%'
                %s
                LIMIT ?
                """.formatted(confirmedOnly ? "AND d.status = 'CONFIRMED'" : "");
        return jdbc.query(sql, (rs, rowNum) -> new ChunkRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getDouble("distance")), keyword, limit);
    }

    /** 返回库中实际使用的 embedding 模型标识（无数据返回 null）。 */
    public String findDistinctEmbeddingModel() {
        List<String> models = jdbc.queryForList(
                "SELECT DISTINCT embedding_model FROM document_chunk", String.class);
        return models.isEmpty() ? null : String.join(",", models);
    }

    public List<UUID> findAllDocumentIds() {
        return jdbc.queryForList("SELECT id FROM knowledge_document ORDER BY created_at", UUID.class);
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
```

- [ ] **Step 3: 编写 IndexService（事件驱动重建）**

```java
// retrieval/IndexService.java
package com.pkb.retrieval;

import com.pkb.knowledge.DocumentSavedEvent;
import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeDocumentRepository;
import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final KnowledgeDocumentRepository documentRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final ChunkRepository chunkRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentSaved(DocumentSavedEvent event) {
        try {
            reindex(event.documentId());
        } catch (Exception e) {
            // 失败时旧块仍在（delete 与 insert 同事务回滚），文档保持旧索引可查
            log.error("文档 {} 向量化失败，保留旧索引: {}", event.documentId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reindex(UUID documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
        List<String> chunks = chunkingService.split(doc.getContent());
        if (chunks.isEmpty()) {
            log.warn("文档 {} 内容为空，跳过向量化", documentId);
            return;
        }
        List<float[]> vectors = embeddingProvider.embedAll(chunks);
        chunkRepository.deleteByDocumentId(documentId);
        chunkRepository.insertChunks(documentId, chunks, vectors, embeddingProvider.modelId());
        log.info("文档 {} 重新向量化完成，共 {} 块", documentId, chunks.size());
    }
}
```

- [ ] **Step 4: 编写 IndexService 集成测试**

```java
// retrieval/IndexServiceTest.java
package com.pkb.retrieval;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class IndexServiceTest extends AbstractIntegrationTest {

    @Autowired
    IndexService indexService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    ChunkRepository chunkRepository;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void reindexReplacesChunks() {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> fakeVector(texts.indexOf(t))).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        KnowledgeDocument doc = knowledgeService.createDraft(newSource(),
                new DistilledDraft("标题", "摘要", "## 一\n" + "第一段内容。".repeat(80) + "\n\n## 二\n" + "第二段内容。".repeat(80)));

        indexService.reindex(doc.getId());
        var rows = chunkRepository.vectorSearch(fakeVector(0), 10, false);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.documentId().equals(doc.getId()));

        // 改内容后再重建，旧块应被替换
        knowledgeService.save(doc.getId(), "## 全新\n完全不同的内容。", "重写");
        indexService.reindex(doc.getId());
        var after = chunkRepository.vectorSearch(fakeVector(0), 10, false);
        assertThat(after).allMatch(r -> r.content().contains("全新") || r.content().contains("完全不同"));
    }

    private float[] fakeVector(int seed) {
        float[] v = new float[1024];
        v[seed % 1024] = 1.0f;
        return v;
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("idx-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
```

> 注意：`createDraft` 发布的 `DocumentSavedEvent` 会在测试事务提交后异步触发 `IndexService.onDocumentSaved`——测试内显式调用 `reindex` 与此事件可能交错。因 `@TransactionalEventListener(AFTER_COMMIT)` 在测试事务（不回滚提交时）不会触发（测试事务回滚），`@Transactional` 未标注的本测试会真实提交并触发异步事件。处理：断言用 `allMatch` 而非精确计数，容忍事件引发的额外重建（幂等），测试稳定。

- [ ] **Step 5: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest='ChunkingServiceTest,IndexServiceTest'`
Expected: 4 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: chunking and event-driven reindexing"
```

---

### Task 11: 混合检索（向量 + 关键词）

**Files:**
- Create: `src/main/java/com/pkb/retrieval/SearchHit.java`
- Create: `src/main/java/com/pkb/retrieval/RetrievalService.java`
- Create: `src/main/java/com/pkb/retrieval/RetrievalController.java`
- Test: `src/test/java/com/pkb/retrieval/RetrievalServiceTest.java`

**Interfaces:**
- Consumes: `ChunkRepository.vectorSearch/keywordSearch`（Task 10）、`EmbeddingProvider.embed`（Task 3）
- Produces:
  - `SearchHit`: record，`UUID documentId, String title, String snippet, double score`
  - `RetrievalService`: `List<SearchHit> search(String query, int topK, boolean includeDraft)` — 向量分 = `1 - distance`，关键词分固定 0.5，同块取高分合并，按分排序取 topK
  - 端点：`GET /api/search?q=&topK=&includeDraft=`

- [ ] **Step 1: 编写实现**

```java
// retrieval/SearchHit.java
package com.pkb.retrieval;

import java.util.UUID;

public record SearchHit(UUID documentId, String title, String snippet, double score) {
}
```

```java
// retrieval/RetrievalService.java
package com.pkb.retrieval;

import com.pkb.common.error.BizException;
import com.pkb.knowledge.KnowledgeDocumentRepository;
import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingProvider embeddingProvider;
    private final ChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * 混合检索：向量 topK*2 + 关键词 topK*2，同块合并取高分。
     * includeDraft=false 时仅检索 CONFIRMED 文档（默认质量闸门）。
     */
    public List<SearchHit> search(String query, int topK, boolean includeDraft) {
        if (query == null || query.isBlank()) {
            throw new BizException("请输入检索内容");
        }
        boolean confirmedOnly = !includeDraft;
        float[] queryVector = embeddingProvider.embed(query.strip());

        List<ChunkRow> vectorHits = chunkRepository.vectorSearch(queryVector, topK * 2, confirmedOnly);
        List<ChunkRow> keywordHits = chunkRepository.keywordSearch(query.strip(), topK * 2, confirmedOnly);

        // 按 chunk id 合并：向量分 = 1 - cosine distance；关键词分 = 0.5
        Map<UUID, Scored> merged = new LinkedHashMap<>();
        for (ChunkRow row : vectorHits) {
            merged.put(row.id(), new Scored(row, 1.0 - row.distance()));
        }
        for (ChunkRow row : keywordHits) {
            merged.merge(row.id(), new Scored(row, 0.5),
                    (a, b) -> a.score() >= b.score() ? a : b);
        }

        List<Scored> top = merged.values().stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .toList();

        Map<UUID, String> titles = documentRepository.findAllById(
                        top.stream().map(s -> s.row().documentId()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(d -> d.getId(), d -> d.getTitle()));

        List<SearchHit> hits = new ArrayList<>();
        for (Scored s : top) {
            hits.add(new SearchHit(s.row().documentId(),
                    titles.getOrDefault(s.row().documentId(), "（未知文档）"),
                    snippet(s.row().content()),
                    s.score()));
        }
        return hits;
    }

    private String snippet(String content) {
        String oneLine = content.replace('\n', ' ').strip();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "…";
    }

    private record Scored(ChunkRow row, double score) {
    }
}
```

```java
// retrieval/RetrievalController.java
package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    @GetMapping
    public List<SearchHit> search(@RequestParam String q,
                                  @RequestParam(defaultValue = "5") int topK,
                                  @RequestParam(defaultValue = "false") boolean includeDraft) {
        return retrievalService.search(q, topK, includeDraft);
    }
}
```

- [ ] **Step 2: 编写检索测试（草稿过滤 + 排序）**

```java
// retrieval/RetrievalServiceTest.java
package com.pkb.retrieval;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RetrievalServiceTest extends AbstractIntegrationTest {

    @Autowired
    RetrievalService retrievalService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    IndexService indexService;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void draftExcludedByDefaultIncludedWhenRequested() {
        // 让 embed 返回"内容哈希"方向向量：相同内容的向量接近
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(RetrievalServiceTest::vectorOf).toList();
        });
        when(embeddingProvider.embed(anyString())).thenAnswer(inv -> vectorOf(inv.getArgument(0)));
        when(embeddingProvider.modelId()).thenReturn("test-embedding");

        KnowledgeDocument draft = knowledgeService.createDraft(newSource(),
                new DistilledDraft("草稿文档", "摘要", "信贷风控的核心是贷前审查。"));
        indexService.reindex(draft.getId());

        List<SearchHit> confirmedOnly = retrievalService.search("贷前审查", 5, false);
        assertThat(confirmedOnly).isEmpty();

        List<SearchHit> withDraft = retrievalService.search("贷前审查", 5, true);
        assertThat(withDraft).hasSize(1);
        assertThat(withDraft.get(0).title()).isEqualTo("草稿文档");

        knowledgeService.confirm(draft.getId());
        List<SearchHit> afterConfirm = retrievalService.search("贷前审查", 5, false);
        assertThat(afterConfirm).hasSize(1);
    }

    /** 把文本映射为确定性向量：按内容哈希散布，保证同内容向量一致。 */
    private static float[] vectorOf(String text) {
        float[] v = new float[1024];
        int h = text.hashCode();
        for (int i = 0; i < 8; i++) {
            v[Math.floorMod(h + i * 31, 1024)] = 1.0f / (i + 1);
        }
        return v;
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("rt-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
```

- [ ] **Step 3: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=RetrievalServiceTest`
Expected: 1 test PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: hybrid retrieval with confirmed-only default"
```

---

### Task 12: 模型管理与全量索引重建

**Files:**
- Create: `src/main/java/com/pkb/retrieval/ReindexService.java`
- Create: `src/main/java/com/pkb/retrieval/AdminController.java`
- Test: `src/test/java/com/pkb/retrieval/ReindexServiceTest.java`

**Interfaces:**
- Consumes: `ChunkRepository.findDistinctEmbeddingModel/findAllDocumentIds`（Task 10）、`ModelConfigHolder`（Task 3）、`IndexService.reindex`（Task 10）
- Produces:
  - `ReindexService`: `boolean isIndexConsistent()`；`int reindexAll()`
  - 端点（`AdminController`）：
    - `GET /api/admin/model` → `{ chat: {baseUrl, model}, embedding: {model}, indexConsistent }`（apiKey 不回显）
    - `PUT /api/admin/model/chat` → body `{baseUrl, apiKey, model}`，热切换 Chat 模型
    - `POST /api/admin/reindex` → 全量重建，返回重建文档数

- [ ] **Step 1: 编写 ReindexService**

```java
// retrieval/ReindexService.java
package com.pkb.retrieval;

import com.pkb.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

    private final ChunkRepository chunkRepository;
    private final EmbeddingProvider embeddingProvider;
    private final IndexService indexService;

    /** 库中向量的模型标识与当前配置一致则为 true；无向量数据时视为一致。 */
    public boolean isIndexConsistent() {
        String used = chunkRepository.findDistinctEmbeddingModel();
        return used == null || used.equals(embeddingProvider.modelId());
    }

    /** 全量重建（同步逐文档执行，失败单个跳过并记日志）。返回成功重建的文档数。 */
    public int reindexAll() {
        List<UUID> ids = chunkRepository.findAllDocumentIds();
        int ok = 0;
        for (UUID id : ids) {
            try {
                indexService.reindex(id);
                ok++;
            } catch (Exception e) {
                log.error("重建文档 {} 索引失败: {}", id, e.getMessage());
            }
        }
        return ok;
    }
}
```

- [ ] **Step 2: 编写 AdminController**

```java
// retrieval/AdminController.java
package com.pkb.retrieval;

import com.pkb.provider.ModelConfigHolder;
import com.pkb.provider.ModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ModelConfigHolder holder;
    private final ReindexService reindexService;

    @GetMapping("/model")
    public Map<String, Object> model() {
        ModelProperties.ModelEndpoint chat = holder.currentChat();
        ModelProperties.ModelEndpoint embedding = holder.currentEmbedding();
        return Map.of(
                "chat", Map.of("baseUrl", chat.baseUrl(), "model", chat.model()),
                "embedding", Map.of("model", embedding.model()),
                "indexConsistent", reindexService.isIndexConsistent());
    }

    @PutMapping("/model/chat")
    public Map<String, Object> switchChat(@RequestBody Map<String, String> body) {
        holder.switchChat(body.get("baseUrl"), body.get("apiKey"), body.get("model"));
        ModelProperties.ModelEndpoint chat = holder.currentChat();
        return Map.of("baseUrl", chat.baseUrl(), "model", chat.model());
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return Map.of("reindexed", reindexService.reindexAll(),
                "indexConsistent", reindexService.isIndexConsistent());
    }
}
```

- [ ] **Step 3: 编写一致性检测测试**

```java
// retrieval/ReindexServiceTest.java
package com.pkb.retrieval;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.HashUtil;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceDocumentRepository;
import com.pkb.ingestion.SourceType;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class ReindexServiceTest extends AbstractIntegrationTest {

    @Autowired
    ReindexService reindexService;
    @Autowired
    IndexService indexService;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    SourceDocumentRepository sourceRepository;

    @MockitoBean
    EmbeddingProvider embeddingProvider;

    @Test
    void detectsModelMismatchAndRebuilds() {
        when(embeddingProvider.embedAll(anyList())).thenAnswer(inv -> {
            java.util.List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> new float[1024]).toList();
        });
        when(embeddingProvider.modelId()).thenReturn("model-a");

        var doc = knowledgeService.createDraft(newSource(), new DistilledDraft("t", "s", "内容内容内容。"));
        indexService.reindex(doc.getId());
        assertThat(reindexService.isIndexConsistent()).isTrue();

        when(embeddingProvider.modelId()).thenReturn("model-b");
        assertThat(reindexService.isIndexConsistent()).isFalse();

        reindexService.reindexAll();
        assertThat(reindexService.isIndexConsistent()).isTrue();
    }

    private SourceDocument newSource() {
        SourceDocument doc = new SourceDocument();
        doc.setSourceType(SourceType.FILE);
        doc.setFileName("rx-" + System.nanoTime() + ".txt");
        doc.setContentHash(HashUtil.sha256(doc.getFileName()));
        doc.setStoragePath("/tmp/" + doc.getFileName());
        return sourceRepository.save(doc);
    }
}
```

- [ ] **Step 4: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=ReindexServiceTest`
Expected: 1 test PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: model admin endpoints and full reindex"
```

---

### Task 13: RAG 问答（SSE 流式）

**Files:**
- Create: `src/main/java/com/pkb/chat/ChatService.java`
- Create: `src/main/java/com/pkb/chat/ChatController.java`
- Test: `src/test/java/com/pkb/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: `RetrievalService.search(query, topK, includeDraft)`（Task 11）、`ChatProvider.chatStream`（Task 3）
- Produces:
  - `ChatService`: `String buildPrompt(String question, List<SearchHit> hits)`；`Flux<ServerSentEvent<Object>> ask(String question, boolean includeDraft)` — 首个事件 `event: sources`（JSON 数组），随后 `event: token`（data 为增量文本），最后 `event: done`
  - 端点：`GET /api/chat?question=&includeDraft=`（`text/event-stream`，GET 以便前端用原生 EventSource）

- [ ] **Step 1: 编写 ChatService（先写测试）**

```java
// chat/ChatServiceTest.java
package com.pkb.chat;

import com.pkb.retrieval.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    @Test
    void buildPromptNumbersSources() {
        var hits = List.of(
                new SearchHit(UUID.randomUUID(), "信贷风控", "贷前审查是核心环节。", 0.9),
                new SearchHit(UUID.randomUUID(), "向量检索", "余弦距离越小越相似。", 0.8));
        String prompt = new ChatService(null, null).buildPrompt("贷前怎么做？", hits);
        assertThat(prompt).contains("[1] 信贷风控").contains("[2] 向量检索");
        assertThat(prompt).contains("贷前审查是核心环节");
        assertThat(prompt).contains("贷前怎么做？");
    }

    @Test
    void buildPromptWithoutHits() {
        String prompt = new ChatService(null, null).buildPrompt("问题", List.of());
        assertThat(prompt).contains("知识库中没有找到相关资料");
    }
}
```

```java
// chat/ChatService.java
package com.pkb.chat;

import com.pkb.provider.ChatProvider;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final String SYSTEM_PROMPT = """
            你是个人知识库助手。仅依据下方"知识库资料"回答问题：
            - 回答中使用 [编号] 标注引用来源（如 [1][2]）。
            - 资料不足以回答时，明确说明"知识库中没有足够信息"，不要编造。
            - 使用简体中文，回答简洁。
            """;

    private final RetrievalService retrievalService;
    private final ChatProvider chatProvider;

    public Flux<ServerSentEvent<Object>> ask(String question, boolean includeDraft) {
        List<SearchHit> hits = retrievalService.search(question, 5, includeDraft);
        String prompt = buildPrompt(question, hits);

        ServerSentEvent<Object> sourcesEvent = ServerSentEvent.<Object>builder()
                .event("sources")
                .data(hits.stream().map(h -> Map.of(
                        "documentId", h.documentId().toString(),
                        "title", h.title(),
                        "snippet", h.snippet())).toList())
                .build();
        ServerSentEvent<Object> doneEvent = ServerSentEvent.<Object>builder()
                .event("done").data("{}").build();

        Flux<ServerSentEvent<Object>> tokens = chatProvider
                .chatStream(SYSTEM_PROMPT, prompt)
                .map(token -> ServerSentEvent.<Object>builder().event("token").data(token).build());

        return Flux.concat(Flux.just(sourcesEvent), tokens, Flux.just(doneEvent));
    }

    public String buildPrompt(String question, List<SearchHit> hits) {
        StringBuilder sb = new StringBuilder("知识库资料：\n");
        if (hits.isEmpty()) {
            sb.append("（知识库中没有找到相关资料）\n");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                SearchHit h = hits.get(i);
                sb.append("[").append(i + 1).append("] ").append(h.title())
                  .append("：").append(h.snippet()).append("\n");
            }
        }
        sb.append("\n用户问题：").append(question);
        return sb.toString();
    }
}
```

```java
// chat/ChatController.java
package com.pkb.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> ask(@RequestParam String question,
                                             @RequestParam(defaultValue = "false") boolean includeDraft) {
        return chatService.ask(question, includeDraft);
    }
}
```

- [ ] **Step 2: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=ChatServiceTest`
Expected: 2 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: rag chat with sse streaming and citations"
```

---

### Task 14: MCP Server（Streamable HTTP / SSE）

**Files:**
- Modify: `pom.xml`（追加 MCP starter 依赖）
- Modify: `src/main/resources/application.yml`（追加 MCP 配置）
- Create: `src/main/java/com/pkb/mcp/KnowledgeMcpTools.java`
- Test: `src/test/java/com/pkb/mcp/KnowledgeMcpToolsTest.java`

**Interfaces:**
- Consumes: `RetrievalService.search`（Task 11）、`KnowledgeService.get/list`（Task 8）
- Produces:
  - `KnowledgeMcpTools`:
    - `search_knowledge(String query, Integer topK)` → 命中列表（含 documentId/title/snippet/score），仅 CONFIRMED
    - `get_document(String id)` → 完整知识文档（标题/摘要/正文/版本/状态）
    - `list_documents(String status)` → 文档摘要列表（status 可空，默认 CONFIRMED）
  - MCP 端点：`/sse`（Spring AI webmvc starter 默认），Streamable HTTP 由 starter 版本决定（1.0.x 为 SSE，Claude Code / Kimi Code 均支持）

- [ ] **Step 1: 追加依赖与配置**

`pom.xml` 的 `<dependencies>` 中追加：

```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>
```

`application.yml` 的 `spring:` 节点下追加（与 `datasource` 平级）：

```yaml
  ai:
    mcp:
      server:
        name: pkb-knowledge
        version: 0.1.0
        instructions: "个人知识库：检索与读取已确认的知识文档"
```

- [ ] **Step 2: 编写工具类**

```java
// mcp/KnowledgeMcpTools.java
package com.pkb.mcp;

import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.knowledge.dto.DocViews.DocDetail;
import com.pkb.knowledge.dto.DocViews.DocSummary;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final RetrievalService retrievalService;
    private final KnowledgeService knowledgeService;

    @Tool(name = "search_knowledge", description = "在个人知识库中检索相关资料，返回命中的文档片段与来源（仅检索已确认文档）")
    public List<SearchHit> searchKnowledge(
            @ToolParam(description = "检索内容") String query,
            @ToolParam(description = "返回条数，默认 5", required = false) Integer topK) {
        return retrievalService.search(query, topK == null ? 5 : topK, false);
    }

    @Tool(name = "get_document", description = "按 ID 读取知识文档完整内容（含标题、摘要、正文、版本）")
    public DocDetail getDocument(@ToolParam(description = "知识文档 ID（UUID）") String id) {
        return DocDetail.from(knowledgeService.get(UUID.fromString(id)));
    }

    @Tool(name = "list_documents", description = "列出知识文档摘要，可按状态过滤（DRAFT/CONFIRMED，默认 CONFIRMED）")
    public List<DocSummary> listDocuments(
            @ToolParam(description = "状态过滤：DRAFT 或 CONFIRMED，默认 CONFIRMED", required = false) String status) {
        var docStatus = status == null || status.isBlank()
                ? com.pkb.knowledge.DocStatus.CONFIRMED
                : com.pkb.knowledge.DocStatus.valueOf(status.toUpperCase());
        return knowledgeService.list(docStatus, null, null, PageRequest.of(0, 50))
                .map(DocSummary::from).toList();
    }
}
```

```java
// mcp/McpServerConfig.java
package com.pkb.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider knowledgeTools(KnowledgeMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
```

- [ ] **Step 3: 编写工具单元测试**

```java
// mcp/KnowledgeMcpToolsTest.java
package com.pkb.mcp;

import com.pkb.knowledge.DocStatus;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeMcpToolsTest {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final KnowledgeMcpTools tools = new KnowledgeMcpTools(retrievalService, knowledgeService);

    @Test
    void searchDefaultsToTop5ConfirmedOnly() {
        when(retrievalService.search(anyString(), anyInt(), eq(false)))
                .thenReturn(List.of(new SearchHit(UUID.randomUUID(), "标题", "片段", 0.9)));
        List<SearchHit> hits = tools.searchKnowledge("风控", null);
        assertThat(hits).hasSize(1);
        verify(retrievalService).search("风控", 5, false);
    }

    @Test
    void listDefaultsToConfirmed() {
        when(knowledgeService.list(eq(DocStatus.CONFIRMED), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        tools.listDocuments(null);
        verify(knowledgeService).list(eq(DocStatus.CONFIRMED), isNull(), isNull(), any(Pageable.class));
    }
}
```

- [ ] **Step 4: 运行测试并验证端点**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=KnowledgeMcpToolsTest`
Expected: 2 tests PASS

手动验证（应用启动后）：

```bash
curl -s http://localhost:8080/sse -N --max-time 3 | head -5
```

Expected: 返回 SSE 连接（首行 `event:endpoint` 或类似），证明 MCP 端点已挂载。Claude Code 挂载方式记入 Task 20 的 README。

- [ ] **Step 5: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: mcp server with knowledge tools"
```

---

### Task 15: 全局错误处理

**Files:**
- Create: `src/main/java/com/pkb/common/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/pkb/common/error/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `BizException`（Task 3）
- Produces: 统一错误响应 JSON：`{ "message": "用户可读的中文错误" }`；BizException → 400，参数校验 → 400，其他 → 500（message 固定为"服务内部错误，请稍后重试"，详情只进日志）

- [ ] **Step 1: 编写处理器**

```java
// common/error/GlobalExceptionHandler.java
package com.pkb.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, String>> biz(BizException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "文件超过 50MB 上限"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "请求参数不完整或不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "服务内部错误，请稍后重试"));
    }
}
```

- [ ] **Step 2: 编写测试**

```java
// common/error/GlobalExceptionHandlerTest.java
package com.pkb.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bizExceptionReturns400WithMessage() {
        var resp = handler.biz(new BizException("该内容已导入过"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("message", "该内容已导入过");
    }

    @Test
    void unknownExceptionHidesDetails() {
        var resp = handler.unknown(new RuntimeException("数据库连接串 jdbc:..."));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().get("message")).doesNotContain("jdbc");
    }
}
```

- [ ] **Step 3: 运行测试并提交**

Run: `cd /Users/johnpeng/Code/pkb && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: 2 tests PASS

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: global error handling with readable messages"
```

---

### Task 16: 前端骨架（Vue 3 + Vite + Element Plus）

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/api/client.js`

**Interfaces:**
- Consumes: 后端 `8080`（Task 1-15）
- Produces: 前端 `5173`，`/api` 代理到后端；四个路由：`/import`、`/docs`、`/docs/:id`、`/chat`；`api/client.js` 导出 `http`（axios 实例，错误统一抛出 `err.response.data.message`）

- [ ] **Step 1: 编写配置文件**

```json
// frontend/package.json
{
  "name": "pkb-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build"
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.7.9",
    "element-plus": "^2.9.1",
    "md-editor-v3": "^5.1.1",
    "vue": "^3.5.13",
    "vue-router": "^4.5.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "vite": "^6.0.7"
  }
}
```

```js
// frontend/vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/sse': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
```

```html
<!-- frontend/index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>个人知识库</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

- [ ] **Step 2: 编写入口与路由**

```js
// frontend/src/main.js
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'

createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
```

```js
// frontend/src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/docs' },
    { path: '/import', component: () => import('../views/ImportView.vue') },
    { path: '/docs', component: () => import('../views/DocsView.vue') },
    { path: '/docs/:id', component: () => import('../views/DocDetailView.vue') },
    { path: '/chat', component: () => import('../views/ChatView.vue') }
  ]
})

export default router
```

```vue
<!-- frontend/src/App.vue -->
<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/docs">知识文档</el-menu-item>
        <el-menu-item index="/import">导入</el-menu-item>
        <el-menu-item index="/chat">问答</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
```

```js
// frontend/src/api/client.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

export const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.response.use(
  (resp) => resp,
  (err) => {
    const message = err.response?.data?.message || '请求失败，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)
```

- [ ] **Step 3: 安装依赖并验证启动**

```bash
cd /Users/johnpeng/Code/pkb/frontend
npm install
npm run dev &
sleep 8 && curl -s http://localhost:5173/ | head -5
kill %1
```

Expected: 返回 `index.html` 内容。浏览器访问 `http://localhost:5173` 看到左侧菜单（四个页面此时还是空组件，Task 17-19 填充——先建占位组件避免路由报错）：

```vue
<!-- frontend/src/views/ImportView.vue（占位，Task 17 替换） -->
<template><h2>导入</h2></template>
```

```vue
<!-- frontend/src/views/DocsView.vue（占位，Task 18 替换） -->
<template><h2>知识文档</h2></template>
```

```vue
<!-- frontend/src/views/DocDetailView.vue（占位，Task 18 替换） -->
<template><h2>文档详情</h2></template>
```

```vue
<!-- frontend/src/views/ChatView.vue（占位，Task 19 替换） -->
<template><h2>问答</h2></template>
```

- [ ] **Step 4: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: frontend skeleton with vue3 and element plus"
```

---

### Task 17: 前端——导入页（上传 / URL / 任务进度轮询）

**Files:**
- Create: `frontend/src/api/ingest.js`
- Modify: `frontend/src/views/ImportView.vue`（替换占位）

**Interfaces:**
- Consumes: `POST /api/ingest/file`、`POST /api/ingest/url`、`GET /api/ingest/tasks`、`POST /api/ingest/tasks/{id}/retry`（Task 4）
- Produces: 导入页：文件上传（拖拽）、URL 提交、任务表格（2s 轮询，组件卸载停止）、失败重试按钮

- [ ] **Step 1: 编写 API 封装**

```js
// frontend/src/api/ingest.js
import { http } from './client'

export function uploadFile(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/ingest/file', form)
}

export function submitUrl(url) {
  return http.post('/ingest/url', { url })
}

export function listTasks() {
  return http.get('/ingest/tasks').then((r) => r.data)
}

export function retryTask(id) {
  return http.post(`/ingest/tasks/${id}/retry`).then((r) => r.data)
}
```

- [ ] **Step 2: 编写导入页（完整替换占位）**

```vue
<!-- frontend/src/views/ImportView.vue -->
<template>
  <div>
    <h2>导入资料</h2>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>上传文件（docx / pdf / txt / md，≤50MB）</template>
          <el-upload drag :show-file-list="false" :http-request="doUpload" accept=".docx,.pdf,.txt,.md">
            <el-icon size="40"><UploadFilled /></el-icon>
            <div>拖拽文件到此处，或点击选择</div>
          </el-upload>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>导入网址</template>
          <el-input v-model="url" placeholder="https://..." @keyup.enter="doSubmitUrl">
            <template #append>
              <el-button :loading="submitting" @click="doSubmitUrl">导入</el-button>
            </template>
          </el-input>
        </el-card>
      </el-col>
    </el-row>

    <h3 style="margin-top: 24px">导入任务</h3>
    <el-table :data="tasks" v-loading="loading">
      <el-table-column prop="sourceLabel" label="来源" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="200">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="row.status === 'FAILED'" size="small" @click="doRetry(row.id)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listTasks, retryTask, submitUrl, uploadFile } from '../api/ingest'

const url = ref('')
const submitting = ref(false)
const tasks = ref([])
const loading = ref(false)
let timer = null

const STATUS_TEXT = {
  PENDING: '排队中', EXTRACTING: '解析中', DISTILLING: '提炼中',
  COMPLETED: '已完成', FAILED: '失败'
}
const statusText = (s) => STATUS_TEXT[s] || s
const statusType = (s) => ({ COMPLETED: 'success', FAILED: 'danger', PENDING: 'info' }[s] || 'warning')
const formatTime = (iso) => new Date(iso).toLocaleString('zh-CN')

async function refresh() {
  tasks.value = await listTasks()
}

async function doUpload({ file }) {
  await uploadFile(file)
  ElMessage.success('已提交导入任务')
  refresh()
}

async function doSubmitUrl() {
  if (!url.value.trim()) return
  submitting.value = true
  try {
    await submitUrl(url.value.trim())
    ElMessage.success('已提交导入任务')
    url.value = ''
    refresh()
  } finally {
    submitting.value = false
  }
}

async function doRetry(id) {
  await retryTask(id)
  refresh()
}

onMounted(() => {
  loading.value = true
  refresh().finally(() => (loading.value = false))
  timer = setInterval(refresh, 2000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>
```

- [ ] **Step 3: 联调验证**

后端启动（`mvn spring-boot:run`）+ 前端启动（`npm run dev`）后：
1. 上传一个 txt 文件 → 任务出现在表格中，状态流转 排队中→解析中→提炼中→失败（模型未配置真实 API key 时）或已完成
2. 提交重复文件 → 顶部红色提示"该内容已导入过"

- [ ] **Step 4: 提交**

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: import page with upload, url and task polling"
```

---

### Task 18: 前端——文档列表、详情编辑与版本 diff

**Files:**
- Create: `frontend/src/api/docs.js`
- Modify: `frontend/src/views/DocsView.vue`（替换占位）
- Modify: `frontend/src/views/DocDetailView.vue`（替换占位）

**Interfaces:**
- Consumes: Task 8/9 的 `/api/docs/**` 端点
- Produces: 列表页（状态/标签/关键词筛选、分页）；详情页（Markdown 查看/编辑切换、保存、确认、标签编辑、版本抽屉含 diff 着色与回滚）

- [ ] **Step 1: 编写 API 封装**

```js
// frontend/src/api/docs.js
import { http } from './client'

export function listDocs(params) {
  return http.get('/docs', { params }).then((r) => r.data)
}

export function getDoc(id) {
  return http.get(`/docs/${id}`).then((r) => r.data)
}

export function saveDoc(id, content, changeNote) {
  return http.put(`/docs/${id}`, { content, changeNote }).then((r) => r.data)
}

export function confirmDoc(id) {
  return http.post(`/docs/${id}/confirm`).then((r) => r.data)
}

export function listVersions(id) {
  return http.get(`/docs/${id}/versions`).then((r) => r.data)
}

export function diffVersions(id, from, to) {
  return http.get(`/docs/${id}/diff`, { params: { from, to } }).then((r) => r.data)
}

export function rollback(id, versionNo) {
  return http.post(`/docs/${id}/rollback/${versionNo}`).then((r) => r.data)
}

export function setTags(id, tags) {
  return http.put(`/docs/${id}/tags`, { tags }).then((r) => r.data)
}

export function allTags() {
  return http.get('/docs/tags').then((r) => r.data)
}
```

- [ ] **Step 2: 编写列表页（完整替换占位）**

```vue
<!-- frontend/src/views/DocsView.vue -->
<template>
  <div>
    <h2>知识文档</h2>
    <el-space style="margin-bottom: 12px" wrap>
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px" @change="load">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已确认" value="CONFIRMED" />
      </el-select>
      <el-select v-model="filters.tag" placeholder="标签" clearable style="width: 160px" @change="load">
        <el-option v-for="t in tags" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索标题/摘要" clearable style="width: 220px"
                @change="load" />
    </el-space>

    <el-table :data="page.content" v-loading="loading" @row-click="(row) => open(row.id)" style="cursor: pointer">
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'">
            {{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="70">
        <template #default="{ row }">v{{ row.currentVersion }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString('zh-CN') }}</template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px" layout="total, prev, pager, next"
                   :total="page.totalElements" :page-size="20"
                   :current-page="page.number + 1" @current-change="(p) => load(p - 1)" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { allTags, listDocs } from '../api/docs'

const router = useRouter()
const filters = reactive({ status: null, tag: null, keyword: null })
const tags = ref([])
const page = ref({ content: [], totalElements: 0, number: 0 })
const loading = ref(false)

async function load(pageNo = 0) {
  loading.value = true
  try {
    page.value = await listDocs({ ...filters, page: pageNo, size: 20 })
  } finally {
    loading.value = false
  }
}

function open(id) {
  router.push(`/docs/${id}`)
}

onMounted(async () => {
  tags.value = await allTags()
  load()
})
</script>
```

- [ ] **Step 3: 编写详情页（完整替换占位）**

```vue
<!-- frontend/src/views/DocDetailView.vue -->
<template>
  <div v-loading="loading">
    <el-page-header @back="$router.push('/docs')">
      <template #content>
        <el-space>
          <span>{{ doc.title }}</span>
          <el-tag :type="doc.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
            {{ doc.status === 'CONFIRMED' ? '已确认' : '草稿' }}
          </el-tag>
          <el-tag size="small" effect="plain">v{{ doc.currentVersion }}</el-tag>
        </el-space>
      </template>
      <template #extra>
        <el-space>
          <el-button v-if="doc.status === 'DRAFT'" type="success" @click="doConfirm">确认无误</el-button>
          <el-button v-if="!editing" type="primary" @click="startEdit">编辑</el-button>
          <template v-else>
            <el-button @click="editing = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
          </template>
          <el-button @click="drawerVisible = true">版本历史</el-button>
        </el-space>
      </template>
    </el-page-header>

    <div style="margin: 8px 0; color: #909399; font-size: 13px">
      来源：<el-link :href="sourceLink" target="_blank" type="info">{{ doc.sourceLabel }}</el-link>
    </div>

    <el-space style="margin-bottom: 12px" wrap>
      <el-select v-model="docTags" multiple filterable allow-create default-first-option
                 placeholder="添加标签" style="width: 360px" @change="doSetTags" />
    </el-space>

    <MdEditor v-if="editing" v-model="draftContent" style="height: 65vh" />
    <MdPreview v-else :modelValue="doc.content" style="height: 65vh" />

    <el-drawer v-model="drawerVisible" title="版本历史" size="55%">
      <el-table :data="versions" highlight-current-row @current-change="selectVersion">
        <el-table-column prop="versionNo" label="版本" width="70">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="changeNote" label="备注" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.versionNo === doc.currentVersion"
                       @click.stop="doRollback(row.versionNo)">回滚到此版</el-button>
          </template>
        </el-table-column>
      </el-table>
      <h4 v-if="diffLines.length" style="margin-top: 16px">
        与当前版本（v{{ doc.currentVersion }}）的差异
      </h4>
      <div v-if="diffLines.length" class="diff-view">
        <div v-for="(line, i) in diffLines" :key="i" :class="['diff-line', line.type.toLowerCase()]">
          <span class="diff-sign">{{ sign(line.type) }}</span>{{ line.text }}
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmDoc, diffVersions, getDoc, listVersions, rollback, saveDoc, setTags
} from '../api/docs'

const route = useRoute()
const id = route.params.id

const doc = ref({})
const docTags = ref([])
const versions = ref([])
const diffLines = ref([])
const editing = ref(false)
const saving = ref(false)
const loading = ref(false)
const drawerVisible = ref(false)
const draftContent = ref('')

const sourceLink = computed(() =>
  doc.value.sourceLabel?.startsWith('http') ? doc.value.sourceLabel : null)

const sign = (type) => ({ ADD: '+ ', DEL: '- ', KEEP: '  ' }[type])

async function load() {
  loading.value = true
  try {
    doc.value = await getDoc(id)
    versions.value = await listVersions(id)
  } finally {
    loading.value = false
  }
}

function startEdit() {
  draftContent.value = doc.value.content
  editing.value = true
}

async function doSave() {
  saving.value = true
  try {
    const { value: note } = await ElMessageBox.prompt('保存备注（可留空）', '保存版本', {
      confirmButtonText: '保存', cancelButtonText: '取消', inputValue: ''
    }).catch(() => ({ value: null }))
    if (note === null) return
    doc.value = await saveDoc(id, draftContent.value, note || '')
    editing.value = false
    versions.value = await listVersions(id)
    ElMessage.success('已保存并重新向量化')
  } finally {
    saving.value = false
  }
}

async function doConfirm() {
  doc.value = await confirmDoc(id)
  ElMessage.success('已确认，可被检索与问答引用')
}

async function doSetTags(tags) {
  await setTags(id, tags)
  ElMessage.success('标签已更新')
}

async function selectVersion(version) {
  if (!version) return
  diffLines.value = await diffVersions(id, version.versionNo, doc.value.currentVersion)
}

async function doRollback(versionNo) {
  await ElMessageBox.confirm(`将内容回滚为 v${versionNo}（会生成一个新版本，历史不丢失）`, '回滚确认', {
    confirmButtonText: '回滚', cancelButtonText: '取消'
  })
  doc.value = await rollback(id, versionNo)
  versions.value = await listVersions(id)
  diffLines.value = []
  ElMessage.success(`已回滚，当前 v${doc.value.currentVersion}`)
}

onMounted(load)
</script>

<style scoped>
.diff-view {
  font-family: monospace;
  font-size: 13px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 50vh;
  overflow: auto;
}
.diff-line { padding: 1px 8px; white-space: pre-wrap; }
.diff-line.add { background: #e8f7e8; color: #256b25; }
.diff-line.del { background: #fde8e8; color: #a82a2a; }
.diff-sign { display: inline-block; width: 20px; color: #909399; }
</style>
```

- [ ] **Step 4: 联调验证并提交**

后端 + 前端运行下：
1. 打开某文档详情 → 点"编辑" → 改几个字 → 保存（填备注）→ 版本变 v2，状态变已确认
2. 打开"版本历史" → 选中 v1 → 下方出现与 v2 的着色 diff
3. 点"回滚到此版" → 生成 v3，内容与 v1 一致

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: docs list and detail pages with version diff"
```

---

### Task 19: 前端——RAG 问答页（EventSource 流式）

**Files:**
- Modify: `frontend/src/views/ChatView.vue`（替换占位）

**Interfaces:**
- Consumes: `GET /api/chat?question=&includeDraft=`（Task 13，SSE：`sources`/`token`/`done` 事件）
- Produces: 问答页：问题输入、流式回答渲染（Markdown）、引用来源卡片（点击跳转文档）、"包含草稿"开关

- [ ] **Step 1: 编写问答页（完整替换占位）**

```vue
<!-- frontend/src/views/ChatView.vue -->
<template>
  <div class="chat-page">
    <h2>知识库问答</h2>
    <el-input v-model="question" type="textarea" :rows="2" placeholder="向你的知识库提问…"
              @keydown.enter.exact.prevent="ask" />
    <el-space style="margin: 8px 0">
      <el-button type="primary" :loading="streaming" :disabled="!question.trim()" @click="ask">
        提问（Enter）
      </el-button>
      <el-checkbox v-model="includeDraft">包含未确认草稿</el-checkbox>
    </el-space>

    <el-card v-if="answer || streaming" shadow="never" style="margin-top: 12px">
      <MdPreview :modelValue="answer" />
    </el-card>

    <div v-if="sources.length" style="margin-top: 12px">
      <h4>引用来源</h4>
      <el-card v-for="(s, i) in sources" :key="s.documentId" shadow="hover"
               style="margin-bottom: 8px; cursor: pointer" @click="$router.push(`/docs/${s.documentId}`)">
        <b>[{{ i + 1 }}] {{ s.title }}</b>
        <div style="color: #606266; font-size: 13px">{{ s.snippet }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const question = ref('')
const answer = ref('')
const sources = ref([])
const includeDraft = ref(false)
const streaming = ref(false)
let eventSource = null

function ask() {
  if (!question.value.trim() || streaming.value) return
  close()
  answer.value = ''
  sources.value = []
  streaming.value = true

  const params = new URLSearchParams({ question: question.value, includeDraft: includeDraft.value })
  eventSource = new EventSource(`/api/chat?${params}`)
  eventSource.addEventListener('sources', (e) => {
    sources.value = JSON.parse(e.data)
  })
  eventSource.addEventListener('token', (e) => {
    answer.value += e.data
  })
  eventSource.addEventListener('done', () => {
    streaming.value = false
    close()
  })
  eventSource.onerror = () => {
    streaming.value = false
    close()
  }
}

function close() {
  eventSource?.close()
  eventSource = null
}

onBeforeUnmount(close)
</script>

<style scoped>
.chat-page { max-width: 860px; }
</style>
```

- [ ] **Step 2: 联调验证并提交**

后端 + 前端运行下：提问一个知识库中有的主题 → 先出现引用来源卡片 → 回答逐字流出且带 [1] 引用标记 → 点击来源卡片跳转到文档详情。

```bash
cd /Users/johnpeng/Code/pkb
git add -A
git commit -m "feat: chat page with sse streaming and citations"
```

---

### Task 20: 容器化、开源整理与端到端验收

**Files:**
- Create: `Dockerfile`
- Modify: `docker-compose.yml`（追加 app 服务）
- Create: `README.md`
- Create: `LICENSE`
- Create: `docs/e2e-checklist.md`

**Interfaces:**
- Consumes: 全部前序 Task
- Produces: `docker compose up -d` 一键启动完整系统（app + postgres）；GitHub 可开源的仓库门面；手工验收清单

- [ ] **Step 1: 编写 Dockerfile 与完整 compose**

```dockerfile
# Dockerfile
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
WORKDIR /app
COPY --from=backend-build /build/target/pkb-0.1.0-SNAPSHOT.jar app.jar
COPY --from=frontend-build /build/dist ./static
ENV PKB_STORAGE_PATH=/data/storage
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", \
  "--spring.web.resources.static-locations=file:/app/static/", \
  "--spring.mvc.async.request-timeout=300000"]
```

> 说明：前端构建产物通过 `static-locations` 由同一个 8080 端口提供（生产模式无 Vite 代理，前端 axios baseURL `/api` 同源即可）。开发模式仍用 Vite dev server。

```yaml
# docker-compose.yml（完整替换）
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: pkb-postgres
    environment:
      POSTGRES_DB: pkb
      POSTGRES_USER: pkb
      POSTGRES_PASSWORD: pkb
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pkb"]
      interval: 5s
      timeout: 3s
      retries: 10

  app:
    build: .
    container_name: pkb-app
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pkb
      CHAT_BASE_URL: ${CHAT_BASE_URL}
      CHAT_API_KEY: ${CHAT_API_KEY}
      CHAT_MODEL: ${CHAT_MODEL}
      EMBEDDING_BASE_URL: ${EMBEDDING_BASE_URL}
      EMBEDDING_API_KEY: ${EMBEDDING_API_KEY}
      EMBEDDING_MODEL: ${EMBEDDING_MODEL}
    ports:
      - "8080:8080"
    volumes:
      - pkb-storage:/data/storage

  ollama:
    image: ollama/ollama:latest
    container_name: pkb-ollama
    profiles: ["local-llm"]
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama

volumes:
  pgdata:
  pkb-storage:
  ollama-data:
```

> postgres 的 `ports` 从开发期的 `5432:5432` 改为不暴露（app 容器内网访问）——若你仍想本地连库调试，保留端口映射即可，二选一。

- [ ] **Step 2: 编写 README.md（完整内容）**

````markdown
# PKB — 个人知识库

把本地文档（docx/PDF/txt/md）和收藏网页，用 LLM 提炼为结构化知识文档，形成可纠错、可检索的个人知识库；并以 MCP Server 形式外挂到 Claude Code / Kimi Code 等 AI 编程工具，或在内置界面中基于知识库进行 RAG 问答。

## 功能

- **多源导入**：本地文件上传、URL 抓取，异步流水线（解析 → 提炼 → 入库），失败自动重试
- **知识文档**：一源一文档，Markdown 结构化（摘要/要点/术语/待确认），可溯源回原文
- **人工纠错**：在线编辑，保存自动生成版本快照，支持版本 diff 与回滚；保存后自动重新向量化
- **混合检索**：PGvector 向量检索 + 关键词匹配；默认仅检索"已确认"文档（质量闸门）
- **RAG 问答**：流式回答，答案带 [编号] 引用，可点击跳转到来源文档
- **MCP Server**：暴露 `search_knowledge` / `get_document` / `list_documents` 三个工具
- **模型可切换**：OpenAI 兼容 API（Kimi/DeepSeek…）与本地 Ollama 通过配置切换；Embedding 切换提供一致性检测与一键重建索引

## 技术栈

Java 17 · Spring Boot 3 · Spring AI (MCP Server) · PostgreSQL 16 + PGvector · Apache Tika · jsoup · Vue 3 · Element Plus · Docker Compose

## 架构

```
上传/URL ──► 文本抽取 ──► LLM 提炼 ──► 知识文档(草稿) ──► 人工编辑/确认
                                   │                        │ 保存
                                   └──► 首次向量化 ◄── 重新切分+Embedding
                                                            │
                                              PGvector（检索默认仅 CONFIRMED）
                                                            │
                                    ┌───────────────────────┼───────────┐
                                 RAG 问答               MCP Server    文档查看
```

后端为模块化单体：`provider`（模型抽象）/ `ingestion`（导入与异步任务）/ `distillation`（LLM 提炼）/ `knowledge`（文档与版本）/ `retrieval`（切分、向量、检索）/ `chat`（RAG）/ `mcp`（MCP Server）。

## 快速开始

```bash
cp .env.example .env   # 填入你的模型 API key（默认 Kimi，可改任意 OpenAI 兼容端点）
docker compose up -d --build
# 打开 http://localhost:8080
```

使用本地模型（Ollama）：

```bash
docker compose --profile local-llm up -d ollama
docker exec -it pkb-ollama ollama pull qwen2.5:7b
docker exec -it pkb-ollama ollama pull bge-m3
# .env 中改用 .env.example 注释里的 Ollama 预设值，重启 app
```

## MCP 挂载

Claude Code：

```bash
claude mcp add --transport sse pkb http://localhost:8080/sse
```

Kimi Code / 其他 MCP 客户端：以 SSE（或客户端支持的远程方式）添加 `http://localhost:8080/sse`。

挂载后在对话中即可让 AI 调用 `search_knowledge` 检索你的知识库。

## 开发

```bash
docker compose up -d postgres        # 仅启动数据库
mvn spring-boot:run                  # 后端 :8080
cd frontend && npm install && npm run dev   # 前端 :5173（代理 /api）
mvn test                             # 测试（Testcontainers，需 Docker）
```

## License

MIT
````

```text
# LICENSE
MIT License

Copyright (c) 2026 Peng Chuan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 3: 编写端到端验收清单**

```markdown
# docs/e2e-checklist.md — 手工验收清单（发布前全部走一遍）

## 环境
- [ ] `cp .env.example .env` 填入真实 API key 后，`docker compose up -d --build` 全部 healthy
- [ ] 打开 http://localhost:8080 能看到前端界面

## 导入与提炼
- [ ] 上传一个 < 50MB 的 docx：任务状态 排队中→解析中→提炼中→已完成，文档列表出现草稿
- [ ] 上传相同文件：提示"该内容已导入过"，不产生重复
- [ ] 上传 .exe：提示"不支持的文件格式"
- [ ] 导入一篇服务端渲染的文章 URL：生成以页面标题开头的草稿
- [ ] 导入一个需要 JS 渲染的 SPA 页面：任务失败，错误信息可读，点"重试"按钮可再次执行
- [ ] 超长文档（数万字 txt）：提炼结果为整合后的单篇文档而非截断内容

## 编辑与版本
- [ ] 草稿点"确认无误"→ 状态变已确认
- [ ] 编辑内容保存（填备注）→ 版本 +1；版本历史里能看到 v1/v2 diff 着色正确
- [ ] 回滚到 v1 → 生成 v3，内容与 v1 一致
- [ ] 打标签 → 列表页按该标签过滤能搜到

## 检索与问答
- [ ] `/api/search?q=...` 默认不返回草稿；确认后可返回；`includeDraft=true` 草稿可返回
- [ ] 问答页提问：先出引用来源，再流式输出，回答带 [1] 标记，点击来源跳转详情
- [ ] 提问知识库没有的内容：回答"知识库中没有足够信息"，不编造

## MCP
- [ ] `claude mcp add --transport sse pkb http://localhost:8080/sse` 成功
- [ ] 在 Claude Code 中让其"搜索我的知识库里的 XX"→ 实际调用了 search_knowledge 并返回结果

## 模型切换
- [ ] `PUT /api/admin/model/chat` 切换 Chat 模型后，新问答使用新模型
- [ ] .env 换 embedding 模型重启后：`GET /api/admin/model` 显示 `indexConsistent=false`
- [ ] `POST /api/admin/reindex` 后恢复 `true`，检索仍正常
```

- [ ] **Step 4: 构建镜像并跑通完整 compose**

```bash
cd /Users/johnpeng/Code/pkb
cp .env.example .env   # 填入真实 key
docker compose up -d --build
docker compose ps      # postgres + app 都 healthy/running
curl -s http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`；浏览器打开 `http://localhost:8080` 进入前端。

- [ ] **Step 5: 全量测试 + 提交**

```bash
cd /Users/johnpeng/Code/pkb
mvn -q test
git add -A
git commit -m "chore: docker packaging, readme, license and e2e checklist"
git tag v0.1.0
```

> 推送到 GitHub 由你本人执行（`gh repo create` / `git push`），计划不代为推送。

---

## 执行提示（给执行者）

1. **严格按 Task 顺序执行**，每个 Task 末尾必须 commit；测试不通过不进入下一 Task。
2. 依赖版本（Spring Boot 3.4.5 / spring-ai 1.0.1 / tika 2.9.2 等）若拉取失败或 API 不兼容，升级到最新稳定版并在 commit message 中记录偏差；Spring AI MCP starter 的注解/配置以实际版本的官方文档为准。
3. 集成测试需要 Docker（Testcontainers 拉取 `pgvector/pgvector:pg16`），首次运行较慢属正常。
4. 不要把真实 API key 提交进仓库；`.env` 已在 `.gitignore` 中。
