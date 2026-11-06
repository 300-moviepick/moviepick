package com.moviepick.domain.ai.recommendation.service;

import org.springframework.stereotype.Service;

@Service
public class AiCosineSimilarityService {

    // 두 임베딩 벡터의 코사인 유사도 계산
    public double calculate(float[] a, float[] b) {

        if (a == null || b == null
                || a.length == 0
                || a.length != b.length) {
            throw new IllegalArgumentException(
                    "벡터의 길이가 올바르지 않습니다."
            );
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {

            if (!Float.isFinite(a[i])
                    || !Float.isFinite(b[i])) {
                throw new IllegalArgumentException(
                        "벡터에 유효하지 않은 숫자가 있습니다."
                );
            }

            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        // 모든 값이 0인 벡터는 비교할 수 없다.
        if (normA == 0.0 || normB == 0.0) {
            throw new IllegalArgumentException(
                    "영벡터는 유사도를 계산할 수 없습니다."
            );
        }

        double similarity =
                dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

        // 부동소수점 오차로 범위가 벗어나는 것을 방지
        return Math.max(-1.0, Math.min(1.0, similarity));
    }
}