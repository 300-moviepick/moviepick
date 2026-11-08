package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.domain.movie.repository.MovieRankingRepository;
import com.moviepick.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 랭킹 조회: 캐시 사용 여부와 관계없이 같은 페이지 요청으로 저장소를 조회해 같은 모양으로 응답한다
// (캐시 동작 자체는 프록시가 담당하므로 여기서는 메서드 본문만 확인한다)
class MovieRankingCacheServiceTest {

    private final MovieRankingRepository repository = mock(MovieRankingRepository.class);
    private final MovieRankingCacheService service = new MovieRankingCacheService(repository);

    @Test
    void 캐시_경로와_비캐시_경로가_같은_페이지를_조회해_같은_응답을_만든다() {
        PageRequest pageable = PageRequest.of(2, 10);
        when(repository.findRanking(pageable))
                .thenReturn(new PageImpl<MovieRankingResponse>(List.of(), pageable, 35));

        PageResponse<MovieRankingResponse> cached = service.getRanking(1L, 2, 10);
        PageResponse<MovieRankingResponse> direct = service.getRankingWithoutCache(2, 10);

        assertThat(cached).isEqualTo(direct);
        assertThat(cached.page()).isEqualTo(2);
        assertThat(cached.size()).isEqualTo(10);
        assertThat(cached.totalElements()).isEqualTo(35);
        verify(repository, times(2)).findRanking(pageable);
    }
}
