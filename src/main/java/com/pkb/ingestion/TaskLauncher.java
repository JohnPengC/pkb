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
