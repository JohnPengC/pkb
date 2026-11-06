package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    public static final String TYPE_DISTILLED = "DISTILLED";
    public static final String TYPE_RAW = "RAW";

    private final JdbcTemplate jdbc;

    public void deleteByDocumentIdAndChunkType(UUID documentId, String chunkType) {
        jdbc.update("DELETE FROM document_chunk WHERE document_id = ? AND chunk_type = ?", documentId, chunkType);
    }

    public void insertChunks(UUID documentId, List<String> contents, List<float[]> vectors,
                             String modelId, String chunkType) {
        String sql = "INSERT INTO document_chunk (document_id, chunk_index, content, embedding, embedding_model, chunk_type) "
                + "VALUES (?, ?, ?, ?::vector, ?, ?)";
        for (int i = 0; i < contents.size(); i++) {
            jdbc.update(sql, documentId, i, contents.get(i), toVectorLiteral(vectors.get(i)), modelId, chunkType);
        }
    }

    public List<ChunkRow> vectorSearch(float[] queryVector, int limit, boolean confirmedOnly) {
        String sql = """
                SELECT c.id, c.document_id, c.content, c.chunk_type, (c.embedding <=> ?::vector) AS distance
                FROM document_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                %s
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """.formatted(confirmedOnly ? "WHERE d.status = 'CONFIRMED'" : "");
        return jdbc.query(sql, (rs, rowNum) -> new ChunkRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getDouble("distance"),
                rs.getString("chunk_type")), toVectorLiteral(queryVector), toVectorLiteral(queryVector), limit);
    }

    public List<ChunkRow> keywordSearch(String keyword, int limit, boolean confirmedOnly) {
        // 查询按空白拆成多个词项，全部命中（AND）才入选——容忍词序与间隔，符合中文检索习惯
        List<String> terms = java.util.Arrays.stream(keyword.split("\\s+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        StringBuilder where = new StringBuilder("WHERE ");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            where.append("c.content ILIKE '%' || ? || '%'");
        }
        if (confirmedOnly) {
            where.append(" AND d.status = 'CONFIRMED'");
        }
        String sql = "SELECT c.id, c.document_id, c.content, c.chunk_type, 0.5 AS distance "
                + "FROM document_chunk c JOIN knowledge_document d ON d.id = c.document_id "
                + where + " LIMIT ?";
        List<Object> args = new java.util.ArrayList<>(terms);
        args.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> new ChunkRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getDouble("distance"),
                rs.getString("chunk_type")), args.toArray());
    }

    /** 返回库中实际使用的 embedding 模型标识（无数据返回 null）。 */
    public String findDistinctEmbeddingModel() {
        List<String> models = jdbc.queryForList(
                "SELECT DISTINCT embedding_model FROM document_chunk", String.class);
        return models.isEmpty() ? null : String.join(",", models);
    }

    public List<UUID> findAllDocumentIds() {
        return jdbc.queryForList("SELECT id FROM knowledge_document ORDER BY created_at", UUID.class);
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
