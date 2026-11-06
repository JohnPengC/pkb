package com.pkb.mcp;

import com.pkb.knowledge.DocStatus;
import com.pkb.knowledge.KnowledgeService;
import com.pkb.retrieval.RetrievalService;
import com.pkb.retrieval.SearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeMcpToolsTest {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final KnowledgeMcpTools tools = new KnowledgeMcpTools(retrievalService, knowledgeService);

    @Test
    void searchDefaultsToTop5ConfirmedOnly() {
        when(retrievalService.search(anyString(), anyInt(), eq(false)))
                .thenReturn(List.of(new SearchHit(UUID.randomUUID(), "标题", "片段", 0.9, "证据句")));
        List<SearchHit> hits = tools.searchKnowledge("风控", null);
        assertThat(hits).hasSize(1);
        verify(retrievalService).search("风控", 5, false);
    }

    @Test
    void listDefaultsToConfirmed() {
        when(knowledgeService.list(eq(DocStatus.CONFIRMED), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        tools.listDocuments(null);
        verify(knowledgeService).list(eq(DocStatus.CONFIRMED), isNull(), isNull(), any(Pageable.class));
    }
}
