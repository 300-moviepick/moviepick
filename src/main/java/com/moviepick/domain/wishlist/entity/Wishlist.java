package com.moviepick.domain.wishlist.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "wishlists",
        uniqueConstraints = @UniqueConstraint(
                name = Wishlist.UK_MEMBER_MOVIE,
                columnNames = {"member_id", "movie_id"}),
        indexes = @Index(name = "idx_wishlists_movie_id", columnList = "movie_id"))
public class Wishlist extends BaseEntity {

    // 중복 찜 판별용 (WishlistService에서 제약 이름으로 409 여부를 가린다)
    public static final String UK_MEMBER_MOVIE = "uk_wishlists_member_id_movie_id";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    public static Wishlist create(Member member, Movie movie) {
        Wishlist wishlist = new Wishlist();
        wishlist.member = member;
        wishlist.movie = movie;
        return wishlist;
    }
}
