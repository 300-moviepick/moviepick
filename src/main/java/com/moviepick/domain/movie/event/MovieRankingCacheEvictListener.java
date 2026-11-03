
package com.moviepick.domain.movie.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieRankingCacheEvictListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRankingCacheEvict(MovieRankingCacheEvictEvent event) {
        try {
            Cache cache = cacheManager.getCache("movieRanking");

            if (cache != null) {
                cache.clear();
                log.info("영화 랭킹 캐시 삭제 완료");
            }
        } catch (Exception e) {
            log.error("영화 랭킹 캐시 삭제 실패. Redis 상태를 확인해야 합니다.", e);
        }
    }
}
