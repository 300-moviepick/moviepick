package com.moviepick.domain.member.oauth;

import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.SocialMemberRegistrar;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;
    private final SocialMemberRegistrar socialMemberRegistrar;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        if (!"kakao".equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    "지원하지 않는 소셜 로그인입니다."
            );
        }

        Map<String, Object> attributes = oauth2User.getAttributes();

        Object id = attributes.get("id");

        if (id == null) {
            throw new OAuth2AuthenticationException(
                    "카카오 사용자 ID를 찾을 수 없습니다."
            );
        }

        String providerId = String.valueOf(id);

        Map<String, Object> kakaoAccount =
                getMap(attributes.get("kakao_account"));

        String email = getString(kakaoAccount.get("email"));

        Map<String, Object> profile =
                getMap(kakaoAccount.get("profile"));

        String nickname = getString(profile.get("nickname"));

        // 이름이 없거나 탈퇴 회원용 값(withdrawn_)이면 기본 이름을 쓴다
        if (nickname == null || nickname.isBlank()
                || Member.isReservedNickname(nickname)) {
            nickname = "카카오회원_" + providerId;
        }

        // 기존 회원 조회, 없으면 신규 회원 생성 (동시 첫 로그인은 먼저 만들어진 회원 사용)
        Long memberId = socialMemberRegistrar.findOrRegister(
                AuthProvider.KAKAO,
                providerId,
                email,
                nickname
        );

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        "카카오 회원을 찾을 수 없습니다."
                ));

        // 정지 기간이 만료되었다면 자동 해제
        member.releaseSuspensionIfExpired();

        // 아직 정지 상태이거나 탈퇴한 회원은 로그인 차단
        // 실패 처리기가 정지·탈퇴를 구분할 수 있도록 오류 코드를 붙인다
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw OAuth2LoginFailureHandler.inactiveMemberException(
                    member.getStatus()
            );
        }

        return oauth2User;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String getString(Object value) {
        return value instanceof String str ? str : null;
    }
}
