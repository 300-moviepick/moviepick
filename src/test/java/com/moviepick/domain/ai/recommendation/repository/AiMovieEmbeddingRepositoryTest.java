package com.moviepick.domain.ai.recommendation.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingRepositoryTest {

    private static final Long MOVIE_ID = 60L;
    private static final String MODEL = "embeddinggemma:300m";
    private static final String HASH = "a".repeat(64);

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper =
            JsonMapper.builder().build();

    private AiMovieEmbeddingRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiMovieEmbeddingRepository(
                jdbcTemplate,
                jsonMapper
        );
    }

    @Test
    void 임베딩_벡터를_JSON으로_변환하여_저장한다() {

        float[] embedding = {0.1f, 0.2f, 0.3f};

        repository.upsert(
                MOVIE_ID,
                MODEL,
                HASH,
                embedding
        );

        ArgumentCaptor<String> jsonCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).update(
                contains("INSERT INTO movie_embeddings"),
                eq(MOVIE_ID),
                eq(MODEL),
                eq(HASH),
                eq(3),
                jsonCaptor.capture()
        );

        float[] savedVector = jsonMapper.readValue(
                jsonCaptor.getValue(),
                float[].class
        );

        assertThat(savedVector)
                .containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void 저장된_임베딩을_조회하고_벡터로_복원한다()
            throws Exception {

        ResultSet rs = mock(ResultSet.class);

        given(rs.next()).willReturn(true);
        given(rs.getLong("movie_id")).willReturn(MOVIE_ID);
        given(rs.getString("model_name")).willReturn(MODEL);
        given(rs.getString("content_hash")).willReturn(HASH);
        given(rs.getInt("dimensions")).willReturn(3);
        given(rs.getString("embedding_json"))
                .willReturn("[0.1,0.2,0.3]");

        given(jdbcTemplate.query(
                contains("WHERE movie_id = ?"),
                org.mockito.ArgumentMatchers
                        .<ResultSetExtractor<
                                Optional<AiMovieEmbeddingRepository.StoredEmbedding>
                                >>any(),
                eq(MOVIE_ID)
        )).willAnswer(invocation -> {

            ResultSetExtractor<
                    Optional<AiMovieEmbeddingRepository.StoredEmbedding>
                    > extractor = invocation.getArgument(1);

            return extractor.extractData(rs);
        });

        var result = repository.findByMovieId(MOVIE_ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().movieId())
                .isEqualTo(MOVIE_ID);
        assertThat(result.orElseThrow().modelName())
                .isEqualTo(MODEL);
        assertThat(result.orElseThrow().embedding())
                .containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void 잘못된_벡터는_저장하지_않는다() {

        assertThatThrownBy(() ->
                repository.upsert(
                        MOVIE_ID,
                        MODEL,
                        HASH,
                        new float[]{Float.NaN}
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                repository.upsert(
                        MOVIE_ID,
                        MODEL,
                        HASH,
                        new float[0]
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void 잘못된_영화_ID는_거부한다() {

        assertThatThrownBy(() ->
                repository.findByMovieId(-1L)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbcTemplate);
    }
}