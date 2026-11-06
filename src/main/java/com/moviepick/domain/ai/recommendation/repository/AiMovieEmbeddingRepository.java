package com.moviepick.domain.ai.recommendation.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AiMovieEmbeddingRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO movie_embeddings (
                movie_id,
                model_name,
                content_hash,
                dimensions,
                embedding_json
            )
            VALUES (?, ?, ?, ?, ?) AS incoming
            ON DUPLICATE KEY UPDATE
                model_name = incoming.model_name,
                content_hash = incoming.content_hash,
                dimensions = incoming.dimensions,
                embedding_json = incoming.embedding_json,
                updated_at = CURRENT_TIMESTAMP(6)
            """;

    private static final String FIND_BY_MOVIE_ID_SQL = """
            SELECT
                movie_id,
                model_name,
                content_hash,
                dimensions,
                embedding_json
            FROM movie_embeddings
            WHERE movie_id = ?
            """;

    private static final String FIND_ALL_BY_MODEL_SQL = """
            SELECT
                movie_id,
                model_name,
                content_hash,
                dimensions,
                embedding_json
            FROM movie_embeddings
            WHERE model_name = ?
            ORDER BY movie_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    // 영화 임베딩 최초 저장 또는 갱신
    public void upsert(
            Long movieId,
            String modelName,
            String contentHash,
            float[] embedding
    ) {
        validateMovieId(movieId);
        validateModelName(modelName);

        if (contentHash == null
                || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "SHA-256 해시 형식이 올바르지 않습니다."
            );
        }

        validateVector(embedding);

        String embeddingJson =
                jsonMapper.writeValueAsString(embedding);

        jdbcTemplate.update(
                UPSERT_SQL,
                movieId,
                modelName,
                contentHash,
                embedding.length,
                embeddingJson
        );
    }

    // 특정 영화의 임베딩 조회
    public Optional<StoredEmbedding> findByMovieId(Long movieId) {
        validateMovieId(movieId);

        return jdbcTemplate.query(
                FIND_BY_MOVIE_ID_SQL,
                (ResultSetExtractor<Optional<StoredEmbedding>>) rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapRow(rs));
                },
                movieId
        );
    }

    // 같은 모델로 생성한 영화 임베딩 전체 조회
    public List<StoredEmbedding> findAllByModelName(
            String modelName
    ) {
        validateModelName(modelName);

        return jdbcTemplate.query(
                FIND_ALL_BY_MODEL_SQL,
                (rs, rowNum) -> mapRow(rs),
                modelName
        );
    }

    private StoredEmbedding mapRow(ResultSet rs)
            throws SQLException {

        float[] embedding;

        try {
            embedding = jsonMapper.readValue(
                    rs.getString("embedding_json"),
                    float[].class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "저장된 영화 임베딩 JSON 변환 실패",
                    e
            );
        }

        int dimensions = rs.getInt("dimensions");
        validateVector(embedding);

        if (embedding.length != dimensions) {
            throw new IllegalStateException(
                    "저장된 임베딩 차원이 일치하지 않습니다."
            );
        }

        return new StoredEmbedding(
                rs.getLong("movie_id"),
                rs.getString("model_name"),
                rs.getString("content_hash"),
                embedding
        );
    }

    private void validateMovieId(Long movieId) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException(
                    "영화 ID가 올바르지 않습니다."
            );
        }
    }

    private void validateModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException(
                    "임베딩 모델명이 필요합니다."
            );
        }
    }

    private void validateVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException(
                    "임베딩 벡터가 비어 있습니다."
            );
        }

        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "임베딩에 유효하지 않은 값이 있습니다."
                );
            }
        }
    }

    public record StoredEmbedding(
            Long movieId,
            String modelName,
            String contentHash,
            float[] embedding
    ) {
        public StoredEmbedding {
            embedding = embedding.clone();
        }

        @Override
        public float[] embedding() {
            return embedding.clone();
        }
    }
}
