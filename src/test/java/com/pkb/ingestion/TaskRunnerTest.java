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
