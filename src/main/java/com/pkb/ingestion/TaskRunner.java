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
        // 每次尝试运行在独立事务中：懒加载关联在会话内可用，失败仅回滚本次尝试
        transactionTemplate.executeWithoutResult(tx -> doExecute(taskId));
    }

    private void doExecute(UUID taskId) {
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

        // createDraft 内部发布 DocumentSavedEvent 触发首次向量化（加入当前事务）
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
