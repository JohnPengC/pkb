package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class RetrievalController {

    private final RetrievalService retrievalService;

    @GetMapping
    public List<SearchHit> search(@RequestParam String q,
                                  @RequestParam(defaultValue = "5") int topK,
                                  @RequestParam(defaultValue = "false") boolean includeDraft) {
        return retrievalService.search(q, topK, includeDraft);
    }
}
