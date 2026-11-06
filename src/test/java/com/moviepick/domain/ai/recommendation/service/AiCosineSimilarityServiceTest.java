package com.moviepick.domain.ai.recommendation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AiCosineSimilarityServiceTest {

    private final AiCosineSimilarityService service =
            new AiCosineSimilarityService();

    @Test
    void 같은_방향의_벡터는_유사도_1이다() {

        float[] a = {1.0f, 0.0f};
        float[] b = {2.0f, 0.0f};

        double result = service.calculate(a, b);

        assertThat(result).isCloseTo(
                1.0, within(0.000001)
        );
    }

    @Test
    void 직교하는_벡터는_유사도_0이다() {

        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};

        double result = service.calculate(a, b);

        assertThat(result).isCloseTo(
                0.0, within(0.000001)
        );
    }

    @Test
    void 반대_방향의_벡터는_유사도_마이너스_1이다() {

        float[] a = {1.0f, 0.0f};
        float[] b = {-1.0f, 0.0f};

        double result = service.calculate(a, b);

        assertThat(result).isCloseTo(
                -1.0, within(0.000001)
        );
    }

    @Test
    void 유사한_벡터의_코사인_점수를_계산한다() {

        float[] a = {1.0f, 0.0f};
        float[] b = {0.6f, 0.8f};

        double result = service.calculate(a, b);

        assertThat(result).isCloseTo(
                0.6, within(0.000001)
        );
    }

    @Test
    void 벡터_차원이_다르면_예외가_발생한다() {

        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f, 2.0f, 3.0f};

        assertThatThrownBy(() ->
                service.calculate(a, b)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 영벡터는_계산할_수_없다() {

        float[] a = {0.0f, 0.0f};
        float[] b = {1.0f, 2.0f};

        assertThatThrownBy(() ->
                service.calculate(a, b)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_벡터는_계산할_수_없다() {

        assertThatThrownBy(() ->
                service.calculate(new float[0], new float[0])
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_벡터는_계산할_수_없다() {

        assertThatThrownBy(() ->
                service.calculate(null, new float[]{1.0f})
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 유효하지_않은_숫자를_거부한다() {

        float[] a = {Float.NaN, 1.0f};
        float[] b = {1.0f, 2.0f};

        assertThatThrownBy(() ->
                service.calculate(a, b)
        ).isInstanceOf(IllegalArgumentException.class);

        float[] c = {Float.POSITIVE_INFINITY, 1.0f};

        assertThatThrownBy(() ->
                service.calculate(c, b)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}