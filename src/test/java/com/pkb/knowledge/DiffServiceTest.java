package com.pkb.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffServiceTest {

    private final DiffService diffService = new DiffService();

    @Test
    void marksAddedDeletedKeptLines() {
        List<DiffLine> lines = diffService.diff("保留行\n删除行", "保留行\n新增行");
        assertThat(lines).containsExactly(
                new DiffLine("KEEP", "保留行"),
                new DiffLine("DEL", "删除行"),
                new DiffLine("ADD", "新增行"));
    }

    @Test
    void identicalContentAllKept() {
        List<DiffLine> lines = diffService.diff("一样\n内容", "一样\n内容");
        assertThat(lines).allMatch(l -> l.type().equals("KEEP"));
    }
}
