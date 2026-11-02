package com.moviepick.domain.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.wishlist.dto.response.WishlistMovieResponse;
import com.moviepick.domain.wishlist.dto.response.WishlistResponse;
import com.moviepick.domain.wishlist.exception.WishlistErrorCode;
import com.moviepick.domain.wishlist.repository.WishlistRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.ErrorCode;
import com.moviepick.global.response.PageResponse;
import com.moviepick.global.security.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 트랜잭션이 실제로 커밋되는지 보려고 테스트 트랜잭션을 끄고 H2로 확인한다. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, WishlistService.class, MemberService.class})
class WishlistServiceTest {

    @Autowired
    private WishlistService wishlistService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private WishlistRepository wishlistRepository;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private Long memberId;
    private Long movieId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(Member.createLocal("member@test.com", "encoded", "member")).getId();
        movieId = movieRepository.save(new Movie(1L, "movie")).getId();
    }

    @AfterEach
    void tearDown() {
        wishlistRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("영화를 찜하면 movieId와 찜한 시각을 돌려준다")
    void addWishlist() {
        WishlistResponse response = wishlistService.addWishlist(memberId, movieId);

        assertThat(response.movieId()).isEqualTo(movieId);
        assertThat(response.wishlistedAt()).isNotNull();
        assertThat(wishlistRepository.existsByMemberIdAndMovieId(memberId, movieId)).isTrue();
    }

    @Test
    @DisplayName("같은 영화를 두 번 찜하면 409(ALREADY_WISHLISTED)다")
    void addWishlist_duplicate() {
        wishlistService.addWishlist(memberId, movieId);

        assertErrorCode(() -> wishlistService.addWishlist(memberId, movieId),
                WishlistErrorCode.ALREADY_WISHLISTED);
        assertThat(wishlistRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 영화나 비활성 영화를 찜하면 404(MOVIE_NOT_FOUND)다")
    void addWishlist_movieNotFound() {
        Movie inactive = new Movie(2L, "inactive");
        ReflectionTestUtils.setField(inactive, "active", false);
        Long inactiveId = movieRepository.save(inactive).getId();

        assertErrorCode(() -> wishlistService.addWishlist(memberId, 9999L), MovieErrorCode.MOVIE_NOT_FOUND);
        assertErrorCode(() -> wishlistService.addWishlist(memberId, inactiveId), MovieErrorCode.MOVIE_NOT_FOUND);
    }

    @Test
    @DisplayName("찜을 취소하면 지워지고, 찜하지 않은 영화를 취소하면 404(WISHLIST_NOT_FOUND)다")
    void removeWishlist() {
        wishlistService.addWishlist(memberId, movieId);

        wishlistService.removeWishlist(memberId, movieId);

        assertThat(wishlistRepository.count()).isZero();
        assertErrorCode(() -> wishlistService.removeWishlist(memberId, movieId),
                WishlistErrorCode.WISHLIST_NOT_FOUND);
    }

    @Test
    @DisplayName("내 찜 목록은 최근 찜한 순이고 다른 회원의 찜은 보이지 않는다")
    void getMyWishlist() {
        Long secondMovieId = movieRepository.save(new Movie(2L, "second")).getId();
        Long otherId = memberRepository.save(Member.createLocal("other@test.com", "encoded", "other")).getId();
        wishlistService.addWishlist(memberId, movieId);
        wishlistService.addWishlist(memberId, secondMovieId);
        wishlistService.addWishlist(otherId, movieId);

        PageResponse<WishlistMovieResponse> response = wishlistService.getMyWishlist(memberId, 0, 20);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content())
                .extracting(WishlistMovieResponse::movieId)
                .containsExactly(secondMovieId, movieId);
        assertThat(response.content().get(0).title()).isEqualTo("second");
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
