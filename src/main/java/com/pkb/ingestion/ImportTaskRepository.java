package com.pkb.ingestion;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportTaskRepository extends JpaRepository<ImportTask, UUID> {

    /** TaskView 需要 sourceDocument（retry 端点在事务外转换视图，open-in-view 已关） */
    @EntityGraph(attributePaths = "sourceDocument")
    Optional<ImportTask> findById(UUID id);

    @EntityGraph(attributePaths = "sourceDocument")
    List<ImportTask> findAllByOrderByCreatedAtDesc();

    /** 级联删除导入记录：先移除来源文档的全部任务 */
    void deleteAllBySourceDocumentId(UUID sourceDocumentId);
}
