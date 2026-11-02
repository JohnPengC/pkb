package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<KnowledgeDocumentVersion, UUID> {
    List<KnowledgeDocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersionNo(UUID documentId, int versionNo);

    /** 级联删除知识文档前，先移除其版本快照（外键无级联） */
    void deleteByDocumentId(UUID documentId);
}
