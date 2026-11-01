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
