package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<KnowledgeDocumentVersion, UUID> {
    List<KnowledgeDocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);
    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersionNo(UUID documentId, int versionNo);
}
