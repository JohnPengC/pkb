package com.pkb.ingestion;

import com.pkb.common.error.BizException;
import com.pkb.distillation.DistillationService;
import com.pkb.distillation.DistilledDraft;
import com.pkb.knowledge.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRunner {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {2_000, 4_000, 8_000};

    private final ImportTaskRepository taskRepository;
    private final SourceDocumentRepository sourceRepository;
    private final TextExtractor textExtractor;
    private final UrlFetcher urlFetcher;
    private final JsRenderer jsRenderer;
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
        String rawText;
        if (source.getSourceType() == SourceType.FILE) {
            rawText = textExtractor.extract(stored);
        } else {
            try {
                rawText = urlFetcher.extract(stored);
            } catch (BizException e) {
                // SPA 页面正文为空：无头浏览器渲染兜底，渲染结果落盘替换存储
                rawText = jsRenderer.render(source.getUrl())
                        .map(html -> {
                            try {
                                Path rendered = stored.resolveSibling(
                                        java.util.UUID.randomUUID() + ".rendered.html");
                                java.nio.file.Files.writeString(rendered, html);
                                java.nio.file.Files.deleteIfExists(stored);
                                source.setStoragePath(rendered.toString());
                                sourceRepository.save(source);
                                return urlFetcher.extract(rendered);
                            } catch (java.io.IOException io) {
                                throw new UncheckedIOException(io);
                            }
                        })
                        .orElseThrow(() -> e);
            }
        }

        String hint = source.getSourceType() == SourceType.FILE ? source.getFileName() : source.getUrl();

        // 处理方式：AI 提炼生成结构化知识文档；或仅转 Markdown（原文即文档，零损耗零 LLM 调用）
        DistilledDraft draft;
        if (task.getProcessMode() == ProcessMode.RAW) {
            draft = new DistilledDraft(rawTitle(hint, rawText), "", rawText);
        } else {
            task.markStatus(TaskStatus.DISTILLING);
            taskRepository.save(task);
            draft = distillationService.distill(rawText, hint);
        }

        // createDraft 内部发布 DocumentSavedEvent 触发首次向量化（加入当前事务）
        transactionTemplate.executeWithoutResult(
                tx -> knowledgeService.createDraft(source, draft, task.getProcessMode()));

        task.markStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
    }

    /** URL 抽取结果以 "# 标题" 开头时取其为标题；否则回退文件名/URL。 */
    private String rawTitle(String hint, String rawText) {
        if (rawText.startsWith("# ")) {
            int nl = rawText.indexOf('\n');
            String title = rawText.substring(2, nl < 0 ? rawText.length() : nl).strip();
            if (!title.isEmpty()) {
                return title;
            }
        }
        return hint;
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
