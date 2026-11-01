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
