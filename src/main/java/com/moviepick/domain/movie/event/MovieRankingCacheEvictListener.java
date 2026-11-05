
package com.moviepick.domain.movie.event;

import com.moviepick.domain.movie.service.MovieRankingVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovieRankingCacheEvictListener {

    private final MovieRankingVersionService movieRankingVersionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRankingCacheEvict(MovieRankingCacheEvictEvent event) {
        try {
            movieRankingVersionService.increaseVersion();
            log.info("영화 랭킹 캐시 버전 증가 완료");
        } catch (Exception e) {
            log.error("영화 랭킹 캐시 버전 증가 실패. Redis 상태를 확인해야 합니다.", e);
        }
    }
}
