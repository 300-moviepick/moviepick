package com.moviepick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.moviepick.domain.ai.credit.service.AiSignupCreditService;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 가입 닉네임이 겹치면 _2, _3…을 붙인다 (AUTH-15 소셜, Q4) */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, SocialMemberRegistrar.class})
class SocialNicknameNumberTest {

    @Autowired
    private SocialMemberRegistrar socialMemberRegistrar;
    @MockitoSpyBean
    private MemberRepository memberRepository;
    // 회원가입 혜택(AI 크레딧 지급)은 이 테스트의 범위 밖이라 mock으로 대체
    @MockitoBean
    private AiSignupCreditService aiSignupCreditService;

    @AfterEach
    void tearDown() {
        Mockito.reset(memberRepository);
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("겹치지 않으면 받은 이름 그대로")
    void uniqueName() {
        assertThat(register("k-1", "홍길동")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("같은 이름이 있으면 _2")
    void secondName() {
        saveLocal("홍길동");

        assertThat(register("k-1", "홍길동")).isEqualTo("홍길동_2");
    }

    @Test
    @DisplayName("_2도 있으면 _3")
    void thirdName() {
        saveLocal("홍길동");
        saveLocal("홍길동_2");

        assertThat(register("k-1", "홍길동")).isEqualTo("홍길동_3");
    }

    @Test
    @DisplayName("번호를 붙여 50자를 넘으면 이름 앞부분은 살리고 뒤쪽을 잘라 _n을 붙인다")
    void longName() {
        String name = "가".repeat(49) + "나";   // 50자
        saveLocal(name);

        String nickname = register("k-1", name);

        assertThat(nickname).isEqualTo("가".repeat(48) + "_2");
        assertThat(nickname).hasSize(50);
    }

    @Test
    @DisplayName("동시 가입으로 고른 번호가 먼저 쓰이면 다음 번호로 다시 시도한다")
    void concurrentNumberTaken() {
        saveLocal("홍길동");
        saveLocal("홍길동_2");
        // 확인할 때는 _2가 비어 있었지만 저장 직전에 다른 요청이 먼저 쓴 상황
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> calls.incrementAndGet() > 1
                && memberRepository.findAll().stream().anyMatch(m -> m.getNickname().equals("홍길동_2")))
                .when(memberRepository).existsByNickname(eq("홍길동_2"));

        assertThat(register("k-1", "홍길동")).isEqualTo("홍길동_3");
    }

    @Test
    @DisplayName("충돌이 5회 이어지면 소셜 로그인 실패(OAuth2AuthenticationException)")
    void tooManyCollisions() {
        saveLocal("홍길동");
        // 매번 비어 있다고 보이지만 저장할 때마다 겹치는 상황
        doReturn(false).when(memberRepository).existsByNickname(anyString());

        assertThatThrownBy(() -> register("k-1", "홍길동"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    private String register(String providerId, String nickname) {
        Long memberId = socialMemberRegistrar.findOrRegister(AuthProvider.KAKAO, providerId, null, nickname);
        return memberRepository.findById(memberId).orElseThrow().getNickname();
    }

    private void saveLocal(String nickname) {
        memberRepository.save(Member.createLocal(
                "n" + System.nanoTime() + "@test.com", "encoded", nickname));
    }
}
