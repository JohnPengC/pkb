package com.pkb.ingestion;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportTaskRepository extends JpaRepository<ImportTask, UUID> {

    @EntityGraph(attributePaths = "sourceDocument")
    List<ImportTask> findAllByOrderByCreatedAtDesc();
}
