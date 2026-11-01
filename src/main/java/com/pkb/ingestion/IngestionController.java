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
