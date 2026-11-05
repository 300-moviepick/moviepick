package com.moviepick.domain.admin.controller;

import com.moviepick.domain.community.exception.CommentErrorCode;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.community.service.CommentLikeService;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.ErrorCode;
import com.moviepick.global.security.RefreshTokenService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// ADMIN-01: 관리자 정지 해제·댓글 숨김의 오류가 500이 아니라 404·409로 나간다
class AdminErrorResponseTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);

    // 관리자 정지 해제·댓글 숨김은 회원·댓글 서비스가 처리한다
    private final MemberService memberService =
            new MemberService(mock(RefreshTokenService.class), memberRepository);
    private final CommentService commentService = new CommentService(
            commentRepository, mock(PostRepository.class), memberRepository, mock(CommentLikeService.class));

    private void assertError(ThrowingCallable call, ErrorCode expected, HttpStatus status) {
        Throwable thrown = catchThrowable(call);

        assertThat(thrown).isInstanceOf(BusinessException.class);
        ErrorCode actual = ((BusinessException) thrown).getErrorCode();
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("없는 회원 정지 해제 → 404 MEMBER_NOT_FOUND")
    void release_unknownMember() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertError(() -> memberService.releaseSuspension(99L),
                MemberErrorCode.MEMBER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("정지되지 않은 회원 정지 해제 → 409 MEMBER_NOT_SUSPENDED")
    void release_activeMember() {
        given(memberRepository.findById(1L))
                .willReturn(Optional.of(Member.createLocal("a@test.com", "encoded", "a")));

        assertError(() -> memberService.releaseSuspension(1L),
                MemberErrorCode.MEMBER_NOT_SUSPENDED, HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("없는 댓글 관리자 숨김 → 404 COMMENT_NOT_FOUND")
    void blindComment_unknown() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        assertError(() -> commentService.blindComment(99L),
                CommentErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    // 이미 삭제·숨김된 댓글의 관리자 숨김은 오류 없이 변화 없음 → CommentBlindTest에서 DB 값으로 검증
}
