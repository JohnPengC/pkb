package com.pkb.knowledge;

import com.pkb.knowledge.dto.DocViews.DocDetail;
import com.pkb.knowledge.dto.DocViews.DocSummary;
import com.pkb.knowledge.dto.DocViews.VersionView;
import com.pkb.knowledge.KnowledgeService.SourceText;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final TagRepository tagRepository;
    private final DiffService diffService;

    @GetMapping
    public Page<DocSummary> list(@RequestParam(required = false) DocStatus status,
                                 @RequestParam(required = false) String tag,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return knowledgeService.list(status, tag, keyword, PageRequest.of(page, size))
                .map(DocSummary::from);
    }

    @GetMapping("/{id}")
    public DocDetail detail(@PathVariable UUID id) {
        return toDetail(knowledgeService.get(id));
    }

    @PutMapping("/{id}")
    public DocDetail save(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return toDetail(knowledgeService.save(id, body.get("content"), body.get("changeNote")));
    }

    @PostMapping("/{id}/confirm")
    public DocDetail confirm(@PathVariable UUID id) {
        return toDetail(knowledgeService.confirm(id));
    }

    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export() {
        byte[] zip = knowledgeService.exportAll();
        String filename = "pkb-export-" + java.time.LocalDate.now() + ".zip";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    @GetMapping("/{id}/source")
    public SourceText source(@PathVariable UUID id) {
        return knowledgeService.getSourceText(id);
    }

    @GetMapping("/{id}/versions")
    public List<VersionView> versions(@PathVariable UUID id) {
        return knowledgeService.listVersions(id).stream().map(VersionView::from).toList();
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public Map<String, Object> version(@PathVariable UUID id, @PathVariable int versionNo) {
        var v = knowledgeService.getVersion(id, versionNo);
        return Map.of("versionNo", v.getVersionNo(), "content", v.getContent(),
                "changeNote", v.getChangeNote() == null ? "" : v.getChangeNote());
    }

    @PutMapping("/{id}/tags")
    public DocDetail setTags(@PathVariable UUID id, @RequestBody Map<String, List<String>> body) {
        return toDetail(knowledgeService.setTags(id, body.getOrDefault("tags", List.of())));
    }

    @GetMapping("/tags")
    public List<String> allTags() {
        return tagRepository.findAllInUse().stream().map(Tag::getName).toList();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        knowledgeService.delete(id);
    }

    @GetMapping("/{id}/diff")
    public List<DiffLine> diff(@PathVariable UUID id,
                               @RequestParam int from,
                               @RequestParam int to) {
        String oldContent = knowledgeService.getVersion(id, from).getContent();
        String newContent = knowledgeService.getVersion(id, to).getContent();
        return diffService.diff(oldContent, newContent);
    }

    @PostMapping("/{id}/rollback/{versionNo}")
    public DocDetail rollback(@PathVariable UUID id, @PathVariable int versionNo) {
        var version = knowledgeService.getVersion(id, versionNo);
        return toDetail(knowledgeService.save(
                id, version.getContent(), "回滚到 v" + versionNo));
    }

    private DocDetail toDetail(KnowledgeDocument doc) {
        List<String> tags = tagRepository.findByDocumentId(doc.getId()).stream()
                .map(Tag::getName).toList();
        return DocDetail.from(doc, tags);
    }
}
