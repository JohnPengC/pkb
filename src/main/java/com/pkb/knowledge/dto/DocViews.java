package com.pkb.knowledge.dto;

import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeDocumentVersion;

import java.util.UUID;

public final class DocViews {

    public record DocSummary(UUID id, String title, String summary, String status,
                             int currentVersion, String updatedAt) {
        public static DocSummary from(KnowledgeDocument d) {
            return new DocSummary(d.getId(), d.getTitle(), d.getSummary(),
                    d.getStatus().name(), d.getCurrentVersion(), d.getUpdatedAt().toString());
        }
    }

    public record DocDetail(UUID id, String title, String summary, String content, String status,
                            int currentVersion, String sourceLabel, String updatedAt) {
        public static DocDetail from(KnowledgeDocument d) {
            var src = d.getSourceDocument();
            String label = src.getSourceType() == com.pkb.ingestion.SourceType.FILE
                    ? src.getFileName() : src.getUrl();
            return new DocDetail(d.getId(), d.getTitle(), d.getSummary(), d.getContent(),
                    d.getStatus().name(), d.getCurrentVersion(), label, d.getUpdatedAt().toString());
        }
    }

    public record VersionView(int versionNo, String changeNote, String createdAt) {
        public static VersionView from(KnowledgeDocumentVersion v) {
            return new VersionView(v.getVersionNo(), v.getChangeNote(), v.getCreatedAt().toString());
        }
    }

    private DocViews() {
    }
}
