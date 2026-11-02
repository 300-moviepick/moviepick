package com.moviepick.domain.wishlist.service;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.wishlist.dto.response.WishlistMovieResponse;
import com.moviepick.domain.wishlist.dto.response.WishlistResponse;
import com.moviepick.domain.wishlist.entity.Wishlist;
import com.moviepick.domain.wishlist.exception.WishlistErrorCode;
import com.moviepick.domain.wishlist.repository.WishlistRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final MovieRepository movieRepository;
    private final MemberService memberService;

    @Transactional
    public WishlistResponse addWishlist(Long memberId, Long movieId) {
        Movie movie = movieRepository.findByIdAndActiveTrue(movieId)
                .orElseThrow(() -> new BusinessException(MovieErrorCode.MOVIE_NOT_FOUND));

        if (wishlistRepository.existsByMemberIdAndMovieId(memberId, movieId)) {
            throw new BusinessException(WishlistErrorCode.ALREADY_WISHLISTED);
        }

        Member member = memberService.getMember(memberId);

        try {
            return WishlistResponse.from(wishlistRepository.saveAndFlush(Wishlist.create(member, movie)));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 영화를 찜하면 UNIQUE 제약에 걸린다 → 409
            if (isDuplicateWishlist(e)) {
                throw new BusinessException(WishlistErrorCode.ALREADY_WISHLISTED);
            }
            throw e;
        }
    }

    private boolean isDuplicateWishlist(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.toLowerCase().contains(Wishlist.UK_MEMBER_MOVIE);
            }
        }
        return false;
    }

    @Transactional
    public void removeWishlist(Long memberId, Long movieId) {
        // 지운 행이 없으면 찜 기록이 없는 것
        int deleted = wishlistRepository.deleteByMemberIdAndMovieId(memberId, movieId);
        if (deleted == 0) {
            throw new BusinessException(WishlistErrorCode.WISHLIST_NOT_FOUND);
        }
    }

    // 최근 찜한 순
    public PageResponse<WishlistMovieResponse> getMyWishlist(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        return PageResponse.from(wishlistRepository
                .findByMemberIdAndMovieActiveTrue(memberId, pageable)
                .map(WishlistMovieResponse::from));
    }
}
