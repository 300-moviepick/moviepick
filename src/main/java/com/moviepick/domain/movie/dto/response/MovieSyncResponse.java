package com.moviepick.domain.movie.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovieSyncResponse(
        int requestedPages,
        int savedCount,
        int failedCount,
        // 수집이 중단된 페이지
        Integer stoppedPage,
        // 수집 중단 이유
        String stopReason
) {
    // 일부 영화 저장에 실패했거나 수집이 중간에 멈췄는지 (수집 배치는 이 경우 실패로 끝낸다)
    public boolean hasFailure() {
        return failedCount > 0
                || stoppedPage != null
                || stopReason != null;
    }

    // 기존 3개 인자 호출부 및 테스트 코드와 호환
    public MovieSyncResponse(
            int requestedPages,
            int savedCount,
            int failedCount
    ) {
        this(
                requestedPages,
                savedCount,
                failedCount,
                null,
                null
        );
    }
}
