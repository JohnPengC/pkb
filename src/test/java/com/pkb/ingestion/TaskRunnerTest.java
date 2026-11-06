package com.pkb.ingestion;

import com.pkb.AbstractIntegrationTest;
import com.pkb.distillation.DistillationService;
import com.pkb.distillation.DistilledDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
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
    @MockitoBean
    JsRenderer jsRenderer;

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

        taskRunner.run(task.getId()); // 内部重试，含真实 backoff sleep（数秒级）

        ImportTask failed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(3);
        assertThat(failed.getErrorMessage()).contains("模型超时");
    }

    @Test
    void spaPageFallsBackToJsRenderer() throws Exception {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "pkb-test-storage");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path shell = dir.resolve("spa-shell-" + System.nanoTime() + ".html");
        java.nio.file.Files.writeString(shell,
                "<html><head><title>SPA</title></head><body><div id=\"app\"></div>"
                        + "<script>render()</script></body></html>");

        SourceDocument source = new SourceDocument();
        source.setSourceType(SourceType.URL);
        source.setUrl("http://localhost/spa");
        source.setContentHash(HashUtil.sha256("spa-" + System.nanoTime()));
        source.setStoragePath(shell.toString());
        source = sourceRepository.save(source);

        ImportTask task = newTask(source);
        when(distillationService.distill(anyString(), anyString()))
                .thenReturn(new DistilledDraft("SPA 标题", "摘要", "正文"));
        when(jsRenderer.render("http://localhost/spa")).thenAnswer(inv -> {
            java.nio.file.Path rendered = dir.resolve("spa-rendered-" + System.nanoTime() + ".html");
            java.nio.file.Files.writeString(rendered,
                    "<html><head><title>SPA 渲染结果</title></head><body>"
                            + "<article><h1>渲染出的正文标题</h1><p>这是 JS 渲染后的正文内容。</p></article>"
                            + "</body></html>");
            return java.util.Optional.of(java.nio.file.Files.readString(rendered));
        });

        taskRunner.run(task.getId());

        ImportTask done = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        var knowledgeDoc = knowledgeDocumentRepository.findBySourceDocumentId(source.getId()).orElseThrow();
        assertThat(knowledgeDoc.getContent()).contains("正文");
        assertThat(sourceRepository.findById(source.getId()).orElseThrow().getStoragePath())
                .endsWith(".rendered.html");
    }

    @Test
    void rawModeSkipsDistillationAndKeepsVerbatimText() throws Exception {
        SourceDocument doc = newFileSource("runner-raw.txt", "原文逐字内容：甲、乙、丙。");
        ImportTask task = newTask(doc);
        task.setProcessMode(ProcessMode.RAW);
        taskRepository.save(task);

        taskRunner.run(task.getId());

        ImportTask done = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        var knowledgeDoc = knowledgeDocumentRepository.findBySourceDocumentId(doc.getId()).orElseThrow();
        assertThat(knowledgeDoc.getProcessMode()).isEqualTo(ProcessMode.RAW);
        assertThat(knowledgeDoc.getTitle()).isEqualTo("runner-raw.txt");
        assertThat(knowledgeDoc.getContent()).contains("原文逐字内容：甲、乙、丙。");
        org.mockito.Mockito.verify(distillationService, org.mockito.Mockito.never())
                .distill(anyString(), anyString());
    }

    @Autowired
    com.pkb.knowledge.KnowledgeDocumentRepository knowledgeDocumentRepository;

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
