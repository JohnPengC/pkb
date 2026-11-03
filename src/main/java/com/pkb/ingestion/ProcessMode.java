package com.pkb.ingestion;

/** 导入处理方式：DISTILL = LLM 提炼生成结构化知识文档；RAW = 仅转 Markdown 原文入库。 */
public enum ProcessMode {
    DISTILL, RAW
}
