package com.moviepick.domain.ai;

import com.moviepick.domain.ai.credit.controller.AiCreditPurchaseController;
import com.moviepick.domain.ai.credit.controller.AiCreditStoreController;
import com.moviepick.domain.ai.credit.service.AiCreditPurchaseService;
import com.moviepick.domain.ai.recommendation.controller.AiRecommendationController;
import com.moviepick.domain.payment.entity.ProductType;
import com.moviepick.domain.payment.service.port.PaidPaymentHandler;
import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingBulkSyncService;
import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingService;
import com.moviepick.domain.ai.recommendation.service.AiMovieSemanticSimilarityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// AI_CHAT_PROVIDER × AI_EMBEDDING_PROVIDER 네 조합에서 앱이 뜨는지, 어떤 AI 빈이 등록되는지 확인한다.
// API 키는 빈 값이고 외부 호출은 하지 않는다 (빈 생성만 확인).
@SpringBootTest(properties = {
        "JWT_SECRET=bW92aWVwaWNrLXRlc3Qtb25seS1qd3Qtc2VjcmV0LWtleS1mb3ItY2khIQ==",
        "TMDB_ACCESS_TOKEN=test-dummy-tmdb-token",
        "spring.ai.openai.api-key="
})
class AiProviderSwitchContextTest {

    @Autowired
    ApplicationContext context;

    boolean has(Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }

    // 크레딧 결제: 구매 확정·웹훅 지급은 스위치와 관계없이 항상, 상품 목록·구매 생성은 chat=openai일 때만
    void assertCreditPurchase(boolean storeExpected) {
        assertThat(has(AiCreditPurchaseController.class)).isTrue();
        assertThat(has(AiCreditPurchaseService.class)).isTrue();
        assertThat(context.getBeansOfType(PaidPaymentHandler.class).values())
                .extracting(PaidPaymentHandler::productType)
                .containsExactlyInAnyOrder(ProductType.SITE_SUPPORT, ProductType.AI_CREDIT);
        assertThat(has(AiCreditStoreController.class)).isEqualTo(storeExpected);
    }

    @Nested
    @DisplayName("chat=none, embedding=none (기본값)")
    @TestPropertySource(properties = {
            "spring.ai.model.chat=none",
            "spring.ai.model.embedding=none"
    })
    class ChatNoneEmbeddingNone {

        @Test
        void AI_빈이_모두_없다() {
            assertThat(has(AiRecommendationController.class)).isFalse();
            assertThat(has(AiMovieEmbeddingService.class)).isFalse();
            assertThat(has(AiMovieSemanticSimilarityService.class)).isFalse();
            assertThat(has(AiMovieEmbeddingBulkSyncService.class)).isFalse();
            assertCreditPurchase(false);
        }
    }

    @Nested
    @DisplayName("chat=none, embedding=openai")
    @TestPropertySource(properties = {
            "spring.ai.model.chat=none",
            "spring.ai.model.embedding=openai"
    })
    class ChatNoneEmbeddingOpenai {

        @Test
        void 추천_API는_없고_임베딩_빈만_있다() {
            assertThat(has(AiRecommendationController.class)).isFalse();
            assertThat(has(AiMovieEmbeddingService.class)).isTrue();
            assertThat(has(AiMovieSemanticSimilarityService.class)).isTrue();
            assertThat(has(AiMovieEmbeddingBulkSyncService.class)).isTrue();
            assertCreditPurchase(false);
        }
    }

    @Nested
    @DisplayName("chat=openai, embedding=none")
    @TestPropertySource(properties = {
            "spring.ai.model.chat=openai",
            "spring.ai.model.embedding=none"
    })
    class ChatOpenaiEmbeddingNone {

        @Test
        void 추천_API는_있고_의미_검색은_없다() {
            assertThat(has(AiRecommendationController.class)).isTrue();
            assertThat(has(AiMovieEmbeddingService.class)).isFalse();
            assertThat(has(AiMovieSemanticSimilarityService.class)).isFalse();
            assertThat(has(AiMovieEmbeddingBulkSyncService.class)).isFalse();
            assertCreditPurchase(true);
        }
    }

    @Nested
    @DisplayName("chat=openai, embedding=openai")
    @TestPropertySource(properties = {
            "spring.ai.model.chat=openai",
            "spring.ai.model.embedding=openai"
    })
    class ChatOpenaiEmbeddingOpenai {

        @Test
        void 추천_API와_의미_검색이_모두_있다() {
            assertThat(has(AiRecommendationController.class)).isTrue();
            assertThat(has(AiMovieEmbeddingService.class)).isTrue();
            assertThat(has(AiMovieSemanticSimilarityService.class)).isTrue();
            assertThat(has(AiMovieEmbeddingBulkSyncService.class)).isTrue();
            assertCreditPurchase(true);
        }
    }
}
