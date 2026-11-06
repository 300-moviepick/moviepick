package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieCandidatePoolTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieGenreRepository movieGenreRepository;

    private AiMovieCandidateService candidateService;

    @BeforeEach
    void setUp() {
        candidateService = new AiMovieCandidateService(
                movieRepository,
                movieGenreRepository
        );
    }

    @Test
    void AI_랭킹은_최대_5000편을_조회한다() {

        when(movieRepository
                .findByActiveTrueAndAdultFalseAndOverviewIsNotNull(
                        any(Pageable.class)
                ))
                .thenReturn(Page.empty());

        candidateService.findCandidatesForRanking();

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(movieRepository)
                .findByActiveTrueAndAdultFalseAndOverviewIsNotNull(
                        captor.capture()
                );

        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageSize()).isEqualTo(5000);
        assertThat(pageable.getPageNumber()).isZero();

        assertThat(pageable.getSort()
                .getOrderFor("popularity")
                .isDescending()).isTrue();

        verifyNoInteractions(movieGenreRepository);
    }

    @Test
    void 일반_후보_조회는_기존_100편_조회범위를_유지한다() {

        when(movieRepository
                .findByActiveTrueAndAdultFalseAndOverviewIsNotNull(
                        any(Pageable.class)
                ))
                .thenReturn(Page.empty());

        candidateService.findCandidates(20);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(movieRepository)
                .findByActiveTrueAndAdultFalseAndOverviewIsNotNull(
                        captor.capture()
                );

        assertThat(captor.getValue().getPageSize())
                .isEqualTo(100);
    }

    @Test
    void 일반_후보_개수가_20편을_초과하면_거부한다() {

        assertThatThrownBy(() ->
                candidateService.findCandidates(21)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                movieRepository,
                movieGenreRepository
        );
    }

    @Test
    void 일반_후보_조회에_0을_전달하면_거부한다() {

        assertThatThrownBy(() ->
                candidateService.findCandidates(0)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                movieRepository,
                movieGenreRepository
        );
    }
}