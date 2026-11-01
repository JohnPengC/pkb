package com.pkb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    @Query(value = "SELECT t.* FROM tag t JOIN document_tag dt ON dt.tag_id = t.id WHERE dt.document_id = :documentId",
           nativeQuery = true)
    List<Tag> findByDocumentId(@Param("documentId") UUID documentId);

    @Query(value = "SELECT DISTINCT t.* FROM tag t JOIN document_tag dt ON dt.tag_id = t.id ORDER BY t.name",
           nativeQuery = true)
    List<Tag> findAllInUse();

    @Modifying
    @Query(value = "DELETE FROM document_tag WHERE document_id = :documentId", nativeQuery = true)
    void deleteDocumentTags(@Param("documentId") UUID documentId);

    @Modifying
    @Query(value = "INSERT INTO document_tag (document_id, tag_id) VALUES (:documentId, :tagId) ON CONFLICT DO NOTHING",
           nativeQuery = true)
    void link(@Param("documentId") UUID documentId, @Param("tagId") UUID tagId);
}
