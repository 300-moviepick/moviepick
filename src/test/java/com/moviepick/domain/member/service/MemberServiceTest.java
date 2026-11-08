package com.moviepick.domain.member.service;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.security.RefreshTokenService;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        memberService = new MemberService(
                refreshTokenService,
                memberRepository
        );
    }

    @Test
    void 정상_회원은_조회할_수_있다() {
        Member member = createMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        Member result = memberService.getActiveMember(1L);

        assertSame(member, result);
    }

    @Test
    void 존재하지_않는_회원은_예외가_발생한다() {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberService.getActiveMember(1L)
        );

        assertEquals(
                MemberErrorCode.MEMBER_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void 정지된_회원은_예외가_발생한다() {
        Member member = createMember();
        member.suspend(LocalDateTime.now().plusDays(7));

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberService.getActiveMember(1L)
        );

        assertEquals(
                MemberErrorCode.MEMBER_SUSPENDED,
                exception.getErrorCode()
        );
    }

    @Test
    void 탈퇴한_회원은_예외가_발생한다() {
        Member member = createMember();
        member.withdraw();

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberService.getActiveMember(1L)
        );

        assertEquals(
                MemberErrorCode.MEMBER_WITHDRAWN,
                exception.getErrorCode()
        );
    }

    @Test
    void 정지_기간이_만료되면_정상_조회된다() {
        Member member = createMember();
        member.suspend(LocalDateTime.now().minusDays(1));

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        Member result = memberService.getActiveMember(1L);

        assertSame(member, result);
    }

    private Member createMember() {
        return Member.createLocal(
                "test@example.com",
                "encoded-password",
                "테스트회원"
        );
    }
}
