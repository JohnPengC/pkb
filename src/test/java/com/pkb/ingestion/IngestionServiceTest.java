package com.pkb.ingestion;

import com.pkb.AbstractIntegrationTest;
import com.pkb.common.error.BizException;
import com.pkb.distillation.DistilledDraft;
import com.pkb.knowledge.KnowledgeDocumentRepository;
import com.pkb.knowledge.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class IngestionServiceTest extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;
    @Autowired
    SourceDocumentRepository sourceRepository;
    @Autowired
    ImportTaskRepository taskRepository;
    @Autowired
    KnowledgeService knowledgeService;
    @Autowired
    KnowledgeDocumentRepository knowledgeDocumentRepository;

    private UUID newTaskId(String name, String content) {
        var file = new MockMultipartFile("file", name, "text/plain", content.getBytes());
        return ingestionService.createFromFile(file).getId();
    }

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

    @Test
    void acceptsStructuredFormats() {
        for (String name : new String[]{"d.pptx", "e.xlsx", "f.csv", "g.json", "h.html", "i.png"}) {
            var file = new MockMultipartFile("file", name, "application/octet-stream",
                    ("内容-" + name).getBytes());
            ImportTask task = ingestionService.createFromFile(file);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
        }
    }

    @Test
    void deleteTaskPurgesSourceAndTask() {
        UUID taskId = newTaskId("del-a.txt", "删除链A内容");
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        UUID sourceId = task.getSourceDocument().getId();
        task.fail("测试失败");
        taskRepository.save(task);

        ingestionService.deleteTask(taskId);

        assertThat(taskRepository.findById(taskId)).isEmpty();
        assertThat(sourceRepository.findById(sourceId)).isEmpty();
    }

    @Test
    void deleteTaskAlsoRemovesKnowledgeDoc() {
        UUID taskId = newTaskId("del-b.txt", "删除链B内容");
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        var doc = knowledgeService.createDraft(task.getSourceDocument(),
                new DistilledDraft("标题", "摘要", "内容"));
        task.markStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);

        ingestionService.deleteTask(taskId);

        assertThat(knowledgeDocumentRepository.findById(doc.getId())).isEmpty();
        assertThat(taskRepository.findById(taskId)).isEmpty();
    }

    @Test
    void rejectsDeletingInProgressTask() {
        UUID taskId = newTaskId("del-c.txt", "删除链C内容");
        assertThatThrownBy(() -> ingestionService.deleteTask(taskId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行中");
    }

    @Test
    void createFromFileAcceptsProcessMode() {
        var file = new MockMultipartFile("file", "mode.txt", "text/plain", "模式选择内容".getBytes());
        ImportTask task = ingestionService.createFromFile(file, ProcessMode.RAW);
        assertThat(task.getProcessMode()).isEqualTo(ProcessMode.RAW);
    }

    @Test
    void redistillSwitchesModeAndClearsOldDoc() {
        UUID taskId = newTaskId("re-a.txt", "重新提炼测试内容");
        ImportTask task = taskRepository.findById(taskId).orElseThrow();
        SourceDocument source = task.getSourceDocument();
        var doc = knowledgeService.createDraft(source,
                new DistilledDraft("旧标题", "摘要", "旧内容"), ProcessMode.DISTILL);
        task.markStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);

        // 同模式守卫：已完成且方式不变 → 拒绝
        assertThatThrownBy(() -> ingestionService.redistill(taskId, ProcessMode.DISTILL))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已是");

        // 切换模式：旧文档清除、任务回到排队
        ImportTask updated = ingestionService.redistill(taskId, ProcessMode.RAW);

        assertThat(updated.getProcessMode()).isEqualTo(ProcessMode.RAW);
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(knowledgeDocumentRepository.findById(doc.getId())).isEmpty();
    }
}
