package com.pkb.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    @Query(value = """
            SELECT d.* FROM knowledge_document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:keyword IS NULL OR d.title ILIKE '%' || :keyword || '%' OR d.summary ILIKE '%' || :keyword || '%')
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM document_tag dt JOIN tag t ON t.id = dt.tag_id
                    WHERE dt.document_id = d.id AND t.name = :tag))
            ORDER BY d.updated_at DESC
            """,
           countQuery = """
            SELECT count(*) FROM knowledge_document d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:keyword IS NULL OR d.title ILIKE '%' || :keyword || '%' OR d.summary ILIKE '%' || :keyword || '%')
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM document_tag dt JOIN tag t ON t.id = dt.tag_id
                    WHERE dt.document_id = d.id AND t.name = :tag))
            """,
           nativeQuery = true)
    Page<KnowledgeDocument> search(@Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("tag") String tag,
                                   Pageable pageable);
}
