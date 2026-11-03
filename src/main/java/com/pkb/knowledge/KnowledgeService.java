package com.pkb.knowledge;

import com.pkb.common.error.BizException;
import com.pkb.distillation.DistilledDraft;
import com.pkb.ingestion.SourceDocument;
import com.pkb.ingestion.SourceType;
import com.pkb.ingestion.TextExtractor;
import com.pkb.ingestion.UrlFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VersionRepository versionRepository;
    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TextExtractor textExtractor;
    private final UrlFetcher urlFetcher;

    /** 原文只读视图：按来源类型从磁盘重新抽取，不额外存储。 */
    public record SourceText(String content, String sourceLabel, String sourceType) {
    }

    /** 由提炼结果创建 v1 草稿，并触发首次向量化事件。 */
    @Transactional
    public KnowledgeDocument createDraft(SourceDocument source, DistilledDraft draft) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setSourceDocument(source);
        doc.setTitle(draft.title());
        doc.setSummary(draft.summary());
        doc.setContent(draft.content());
        documentRepository.save(doc);

        KnowledgeDocumentVersion v1 = new KnowledgeDocumentVersion();
        v1.setDocument(doc);
        v1.setVersionNo(1);
        v1.setContent(draft.content());
        v1.setChangeNote("AI 提炼生成");
        versionRepository.save(v1);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeDocument> list(DocStatus status, String tag, String keyword, Pageable pageable) {
        return documentRepository.search(
                status == null ? null : status.name(),
                keyword == null || keyword.isBlank() ? null : keyword.strip(),
                tag == null || tag.isBlank() ? null : tag.strip(),
                pageable);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument get(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BizException("知识文档不存在"));
    }

    /** 保存编辑内容：版本 +1；首次保存自动确认。 */
    @Transactional
    public KnowledgeDocument save(UUID id, String content, String changeNote) {
        KnowledgeDocument doc = get(id);
        int nextVersion = doc.getCurrentVersion() + 1;
        doc.setContent(content);
        doc.setCurrentVersion(nextVersion);
        doc.setStatus(DocStatus.CONFIRMED);
        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocument(doc);
        version.setVersionNo(nextVersion);
        version.setContent(content);
        version.setChangeNote(changeNote);
        versionRepository.save(version);

        eventPublisher.publishEvent(new DocumentSavedEvent(doc.getId()));
        return doc;
    }

    /** 不改正文，直接确认草稿。 */
    @Transactional
    public KnowledgeDocument confirm(UUID id) {
        KnowledgeDocument doc = get(id);
        doc.setStatus(DocStatus.CONFIRMED);
        doc.setUpdatedAt(OffsetDateTime.now());
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentVersion> listVersions(UUID id) {
        get(id);
        return versionRepository.findByDocumentIdOrderByVersionNoDesc(id);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentVersion getVersion(UUID id, int versionNo) {
        return versionRepository.findByDocumentIdAndVersionNo(id, versionNo)
                .orElseThrow(() -> new BizException("版本不存在：v" + versionNo));
    }

    @Transactional
    public KnowledgeDocument setTags(UUID id, List<String> tagNames) {
        KnowledgeDocument doc = get(id);
        tagRepository.deleteDocumentTags(id);
        for (String name : tagNames) {
            String clean = name.strip();
            if (clean.isEmpty()) {
                continue;
            }
            Tag tag = tagRepository.findByName(clean).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(clean);
                return tagRepository.save(t);
            });
            tagRepository.link(id, tag.getId());
        }
        return doc;
    }

    @Transactional
    public void delete(UUID id) {
        get(id);
        versionRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        // document_chunk / document_tag 由外键 ON DELETE CASCADE 清理
    }

    /** 查看原文（只读）：FILE 来源走文本抽取（MarkItDown 优先），URL 来源走正文提取。 */
    @Transactional(readOnly = true)
    public SourceText getSourceText(UUID id) {
        KnowledgeDocument doc = get(id);
        SourceDocument source = doc.getSourceDocument();
        String text = source.getSourceType() == SourceType.FILE
                ? textExtractor.extract(Path.of(source.getStoragePath()))
                : urlFetcher.extract(Path.of(source.getStoragePath()));
        String label = source.getSourceType() == SourceType.FILE
                ? source.getFileName() : source.getUrl();
        return new SourceText(text, label, source.getSourceType().name());
    }
}
