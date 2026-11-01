package com.pkb.mcp;

import com.pkb.knowledge.KnowledgeDocument;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.knowledge.dto.DocViews.DocDetail;
import com.pkb.knowledge.dto.DocViews.DocSummary;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KnowledgeMcpTools {

    private final RetrievalService retrievalService;
    private final KnowledgeService knowledgeService;

    @Tool(name = "search_knowledge", description = "在个人知识库中检索相关资料，返回命中的文档片段与来源（仅检索已确认文档）")
    public List<SearchHit> searchKnowledge(
            @ToolParam(description = "检索内容") String query,
            @ToolParam(description = "返回条数，默认 5", required = false) Integer topK) {
        return retrievalService.search(query, topK == null ? 5 : topK, false);
    }

    @Tool(name = "get_document", description = "按 ID 读取知识文档完整内容（含标题、摘要、正文、版本）")
    public DocDetail getDocument(@ToolParam(description = "知识文档 ID（UUID）") String id) {
        return DocDetail.from(knowledgeService.get(UUID.fromString(id)));
    }

    @Tool(name = "list_documents", description = "列出知识文档摘要，可按状态过滤（DRAFT/CONFIRMED，默认 CONFIRMED）")
    public List<DocSummary> listDocuments(
            @ToolParam(description = "状态过滤：DRAFT 或 CONFIRMED，默认 CONFIRMED", required = false) String status) {
        var docStatus = status == null || status.isBlank()
                ? com.pkb.knowledge.DocStatus.CONFIRMED
                : com.pkb.knowledge.DocStatus.valueOf(status.toUpperCase());
        return knowledgeService.list(docStatus, null, null, PageRequest.of(0, 50))
                .map(DocSummary::from).toList();
    }
}
