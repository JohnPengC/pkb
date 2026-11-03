package com.pkb.knowledge;

import com.pkb.ingestion.SourceDocument;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false, unique = true)
    private SourceDocument sourceDocument;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DocStatus status = DocStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_mode", nullable = false, length = 16)
    private com.pkb.ingestion.ProcessMode processMode = com.pkb.ingestion.ProcessMode.DISTILL;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
