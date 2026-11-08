package com.moviepick.domain.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moviepick.domain.ai.credit.service.AiSignupCreditService;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.domain.member.service.SocialMemberRegistrar;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestOperations;

/**
 * 같은 소셜 계정의 첫 로그인이 동시에 들어온 상황(AUTH-06)을 흉내 낸다.
 * 첫 조회만 "없음"이 나오게 해서, 저장이 (provider, provider_id) 제약에 걸린 뒤 이미 만들어진 회원으로 로그인하는지 본다.
 * 카카오는 사용자 정보 요청을 가짜 RestOperations로, 구글은 사용자 정보 요청 없이 ID 토큰만으로 loadUser를 실제 호출한다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, CustomOAuth2UserService.class, CustomOidcUserService.class,
        SocialMemberRegistrar.class})
class SocialMemberRaceTest {

    private static final String KAKAO_ID = "1001";
    private static final String GOOGLE_SUB = "google-1001";

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;
    @Autowired
    private CustomOidcUserService customOidcUserService;
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
    @DisplayName("카카오 첫 로그인: 회원이 없으면 새로 만든다")
    void kakaoNewMember() {
        customOAuth2UserService.loadUser(kakaoRequest());

        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, KAKAO_ID)).isPresent();
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("카카오 동시 첫 로그인(이메일 있음): 저장이 제약에 걸리면 이미 만들어진 회원으로 로그인하고 회원은 1명")
    void kakaoConcurrentFirstLogin() {
        Member existing = memberRepository.save(
                Member.createSocial(AuthProvider.KAKAO, KAKAO_ID, "kakao@test.com", "카카오"));
        firstLookupMisses(AuthProvider.KAKAO, KAKAO_ID);

        customOAuth2UserService.loadUser(kakaoRequest());

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, KAKAO_ID))
                .get().extracting(Member::getId).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("구글 동시 첫 로그인(이메일 없음): 저장이 제약에 걸리면 이미 만들어진 회원으로 로그인하고 회원은 1명")
    void googleConcurrentFirstLogin() {
        Member existing = memberRepository.save(
                Member.createSocial(AuthProvider.GOOGLE, GOOGLE_SUB, null, "구글"));
        firstLookupMisses(AuthProvider.GOOGLE, GOOGLE_SUB);

        customOidcUserService.loadUser(googleRequest());

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, GOOGLE_SUB))
                .get().extracting(Member::getId).isEqualTo(existing.getId());
    }

    // 다른 요청이 아직 커밋하지 않아 첫 조회에서는 회원이 보이지 않은 상황.
    // 저장소 spy는 인터페이스라 실제 메서드를 부를 수 없어서, 두 번째부터는 전체 조회로 같은 결과를 만든다.
    private void firstLookupMisses(AuthProvider provider, String providerId) {
        doReturn(Optional.empty())
                .doAnswer(invocation -> memberRepository.findAll().stream()
                        .filter(m -> m.getProvider() == provider && providerId.equals(m.getProviderId()))
                        .findFirst())
                .when(memberRepository)
                .findByProviderAndProviderId(eq(provider), eq(providerId));
    }

    @Test
    @DisplayName("소셜 이름이 탈퇴 예약값(withdrawn_)으로 시작하면 기본 이름으로 가입한다 (카카오·구글)")
    void reservedSocialNickname() {
        customOAuth2UserService.loadUser(kakaoRequest("Withdrawn_7"));
        customOidcUserService.loadUser(googleRequest("withdrawn_8"));

        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, KAKAO_ID))
                .get().extracting(Member::getNickname).isEqualTo("카카오회원_" + KAKAO_ID);
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, GOOGLE_SUB))
                .get().extracting(Member::getNickname).isEqualTo("구글회원_" + GOOGLE_SUB);
    }

    private OAuth2UserRequest kakaoRequest() {
        return kakaoRequest("카카오");
    }

    @SuppressWarnings("unchecked")
    private OAuth2UserRequest kakaoRequest(String nickname) {
        RestOperations restOperations = mock(RestOperations.class);
        Map<String, Object> attributes = Map.of(
                "id", Long.valueOf(KAKAO_ID),
                "kakao_account", Map.of(
                        "email", "kakao@test.com",
                        "profile", Map.of("nickname", nickname)));
        when(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(attributes));
        // setRestOperations는 final이라 트랜잭션 프록시가 아닌 실제 객체에 넣어야 한다
        CustomOAuth2UserService target = AopTestUtils.getTargetObject(customOAuth2UserService);
        target.setRestOperations(restOperations);

        ClientRegistration registration = ClientRegistration.withRegistrationId("kakao")
                .clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.example/authorize")
                .tokenUri("https://kauth.example/token")
                .userInfoUri("https://kapi.example/user/me")
                .userNameAttributeName("id")
                .build();
        return new OAuth2UserRequest(registration, accessToken());
    }

    private OidcUserRequest googleRequest() {
        return googleRequest("구글");
    }

    private OidcUserRequest googleRequest(String name) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile")
                .authorizationUri("https://accounts.example/authorize")
                .tokenUri("https://accounts.example/token")
                .jwkSetUri("https://accounts.example/certs")
                .userNameAttributeName("sub")
                .build();
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of(
                "iss", "https://accounts.example",
                "sub", GOOGLE_SUB,
                "name", name));
        return new OidcUserRequest(registration, accessToken(), idToken);
    }

    private OAuth2AccessToken accessToken() {
        Instant now = Instant.now();
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60));
    }
}
