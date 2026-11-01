package com.pkb.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    private final JdbcTemplate jdbc;

    public void deleteByDocumentId(UUID documentId) {
        jdbc.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    }

    public void insertChunks(UUID documentId, List<String> contents, List<float[]> vectors, String modelId) {
        String sql = "INSERT INTO document_chunk (document_id, chunk_index, content, embedding, embedding_model) "
                + "VALUES (?, ?, ?, ?::vector, ?)";
        for (int i = 0; i < contents.size(); i++) {
            jdbc.update(sql, documentId, i, contents.get(i), toVectorLiteral(vectors.get(i)), modelId);
        }
    }

    public List<ChunkRow> vectorSearch(float[] queryVector, int limit, boolean confirmedOnly) {
        String sql = """
                SELECT c.id, c.document_id, c.content, (c.embedding <=> ?::vector) AS distance
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
                rs.getDouble("distance")), toVectorLiteral(queryVector), toVectorLiteral(queryVector), limit);
    }

    public List<ChunkRow> keywordSearch(String keyword, int limit, boolean confirmedOnly) {
        String sql = """
                SELECT c.id, c.document_id, c.content, 0.5 AS distance
                FROM document_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                WHERE c.content ILIKE '%' || ? || '%'
                %s
                LIMIT ?
                """.formatted(confirmedOnly ? "AND d.status = 'CONFIRMED'" : "");
        return jdbc.query(sql, (rs, rowNum) -> new ChunkRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getDouble("distance")), keyword, limit);
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
