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
