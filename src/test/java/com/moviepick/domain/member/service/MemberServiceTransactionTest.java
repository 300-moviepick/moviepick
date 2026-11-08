package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.security.RefreshTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MemberService 트랜잭션 경계: 정지 해제가 실제로 DB에 저장되는지 (P9) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, MemberService.class})
class MemberServiceTransactionTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @AfterEach
    void tearDown() {
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("내 정보 조회: 정지 기간이 지난 회원은 ACTIVE로 응답하고 DB에도 해제가 저장된다")
    void getMyInfoPersistsExpiredSuspensionRelease() {
        Member member = Member.createLocal("expired@test.com", "encoded-password", "정지만료회원");
        member.suspend(LocalDateTime.now().minusDays(1));
        Long memberId = memberRepository.saveAndFlush(member).getId();

        var response = memberService.getMyInfo(memberId);

        Member saved = memberRepository.findById(memberId).orElseThrow();
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(saved.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(saved.getSuspendedUntil()).isNull();
    }
}
