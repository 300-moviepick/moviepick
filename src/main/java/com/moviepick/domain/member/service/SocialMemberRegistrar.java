package com.moviepick.domain.member.service;

import com.moviepick.domain.ai.credit.service.AiSignupCreditService;
import com.moviepick.domain.member.entity.AuthProvider;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberConstraints;
import com.moviepick.domain.member.repository.MemberRepository;
import java.util.Optional;

import com.moviepick.domain.member.service.port.SignupBenefitPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소셜 회원을 찾고, 없으면 만든다. 카카오·구글 로그인이 함께 쓴다.
 * 조회와 저장을 각각 새 트랜잭션으로 실행한다. 같은 계정의 첫 로그인이 동시에 들어오면 한쪽 저장이
 * DB 유니크 제약에 걸리는데, 그때 새 트랜잭션에서 다시 조회해야 다른 요청이 커밋한 회원이 보인다
 * (MySQL REPEATABLE READ는 한 트랜잭션 안에서 처음 읽은 시점의 값을 계속 보여 준다).
 * 닉네임이 겹치면 _2, _3…을 붙인다 (Q4).
 */
@Component
public class SocialMemberRegistrar {

    // 동시 가입으로 고른 닉네임이 먼저 쓰였을 때 다시 시도하는 최대 횟수
    private static final int MAX_REGISTER_ATTEMPTS = 5;
    private static final int NICKNAME_MAX_LENGTH = 50;

    private final MemberRepository memberRepository;
    private final TransactionTemplate newTransaction;
    // [변경 ②] Port 인터페이스에 의존
    private final SignupBenefitPort signupBenefitPort;


    public SocialMemberRegistrar(
            MemberRepository memberRepository,
            // [AI 추가 ③] 생성자 주입
            // [변경 ③] 생성자 매개변수 변경
            SignupBenefitPort signupBenefitPort,
            PlatformTransactionManager transactionManager
    ) {
        this.memberRepository = memberRepository;
        // [AI 추가 ④] 지급 서비스 연결
        this.signupBenefitPort = signupBenefitPort;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    // 소셜 회원 번호를 돌려준다. 없으면 새로 만든다.
    public Long findOrRegister(
            AuthProvider provider,
            String providerId,
            String email,
            String nickname
    ) {
        Optional<Long> found = findMemberId(provider, providerId);

        if (found.isPresent()) {
            return found.get();
        }

        for (int attempt = 0; attempt < MAX_REGISTER_ATTEMPTS; attempt++) {
            String candidate = findAvailableNickname(nickname);

            try {
                // [AI 변경 ⑤] 회원 생성과 지급을 동일 트랜잭션에서 처리
                return newTransaction.execute(status -> {

                    Member saved = memberRepository.saveAndFlush(
                            Member.createSocial(
                                    provider,
                                    providerId,
                                    email,
                                    candidate
                            )
                    );

                    // [변경 ④] 같은 트랜잭션에서 Port 호출
                    signupBenefitPort.grantTo(saved);

                    return saved.getId();
                });
            } catch (DataIntegrityViolationException exception) {
                // 동시 첫 로그인: 다른 요청이 먼저 만든 회원이 있으면 그 회원으로 로그인한다.
                Optional<Long> created = findMemberId(provider, providerId);

                if (created.isPresent()) {
                    return created.get();
                }

                // 고른 닉네임을 다른 가입이 먼저 쓴 경우만 다음 번호로 다시 시도한다.
                // 다른 이유의 제약 위반이면 그대로 던진다.
                if (!MemberConstraints.isDuplicateNickname(exception)) {
                    throw exception;
                }
            }
        }

        throw new OAuth2AuthenticationException("소셜 회원 닉네임을 정하지 못했습니다.");
    }

    // 받은 이름이 비어 있으면 그대로, 겹치면 _2, _3… 중 처음으로 비어 있는 이름
    private String findAvailableNickname(String nickname) {
        return newTransaction.execute(status -> {
            String candidate = withNumber(nickname, 1);

            for (int number = 2; memberRepository.existsByNickname(candidate); number++) {
                candidate = withNumber(nickname, number);
            }

            return candidate;
        });
    }

    // 1은 받은 이름 그대로, 2부터는 _n을 붙인다. 50자를 넘으면 이름 뒤쪽을 잘라 _n이 들어가게 한다.
    private static String withNumber(String nickname, int number) {
        String suffix = number == 1 ? "" : "_" + number;
        int nameLength = Math.min(nickname.length(), NICKNAME_MAX_LENGTH - suffix.length());

        return nickname.substring(0, nameLength) + suffix;
    }

    private Optional<Long> findMemberId(AuthProvider provider, String providerId) {
        return newTransaction.execute(status -> memberRepository
                .findByProviderAndProviderId(provider, providerId)
                .map(Member::getId));
    }
}
