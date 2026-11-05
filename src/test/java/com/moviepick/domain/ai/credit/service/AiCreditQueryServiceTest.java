package com.moviepick.domain.ai.credit.service;

import com.moviepick.domain.ai.credit.dto.response.AiCreditBalanceResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditTransactionResponse;
import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.entity.AiCreditEventType;
import com.moviepick.domain.ai.credit.entity.AiCreditTransaction;
import com.moviepick.domain.ai.credit.entity.AiCreditWallet;
import com.moviepick.domain.ai.credit.repository.AiCreditTransactionRepository;
import com.moviepick.domain.ai.credit.repository.AiCreditWalletRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCreditQueryServiceTest {

    @Mock
    private AiCreditWalletRepository walletRepository;

    // [추가] 거래 내역 Repository Mock
    @Mock
    private AiCreditTransactionRepository transactionRepository;

    @InjectMocks
    private AiCreditQueryService creditQueryService;

    @Test
    void 무료_크레딧_잔액을_조회한다() {

        Member member = Member.createLocal(
                "ai@test.com",
                "encoded-password",
                "AI테스트"
        );

        AiCreditWallet wallet = new AiCreditWallet(member);
        wallet.grantFreeCredits(3);

        given(walletRepository.findByMember_Id(2L))
                .willReturn(Optional.of(wallet));

        AiCreditBalanceResponse response =
                creditQueryService.getBalance(2L);

        assertThat(response.freeAvailable()).isEqualTo(3);
        assertThat(response.paidAvailable()).isZero();
        assertThat(response.totalAvailable()).isEqualTo(3);

        verify(walletRepository).findByMember_Id(2L);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void 무료와_유료_크레딧의_합계를_반환한다() {

        AiCreditWallet wallet = mock(AiCreditWallet.class);

        given(wallet.getFreeAvailable()).willReturn(2);
        given(wallet.getPaidAvailable()).willReturn(5);

        given(walletRepository.findByMember_Id(2L))
                .willReturn(Optional.of(wallet));

        AiCreditBalanceResponse response =
                creditQueryService.getBalance(2L);

        assertThat(response.freeAvailable()).isEqualTo(2);
        assertThat(response.paidAvailable()).isEqualTo(5);
        assertThat(response.totalAvailable()).isEqualTo(7);
    }

    @Test
    void 지갑이_없으면_잔액조회_예외가_발생한다() {

        given(walletRepository.findByMember_Id(99L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> creditQueryService.getBalance(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.AI_WALLET_NOT_FOUND)
                );

        verify(walletRepository, never()).save(any());
    }

    // [추가] 거래 내역 조회 및 정렬 검증
    @Test
    void 거래_내역을_최신순으로_조회한다() {

        AiCreditWallet wallet = mock(AiCreditWallet.class);
        given(wallet.getId()).willReturn(1L);

        AiCreditTransaction transaction =
                AiCreditTransaction.freeGrant(
                        wallet, 3, "FREE_GRANT:MEMBER:2"
                );

        given(walletRepository.findByMember_Id(2L))
                .willReturn(Optional.of(wallet));

        given(transactionRepository.findByWallet_Id(
                eq(1L), any(Pageable.class)
        )).willReturn(
                new PageImpl<>(
                        List.of(transaction),
                        PageRequest.of(0, 20),
                        1
                )
        );

        PageResponse<AiCreditTransactionResponse> response =
                creditQueryService.getTransactions(2L, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);

        var item = response.content().get(0);

        assertThat(item.eventType())
                .isEqualTo(AiCreditEventType.FREE_GRANT);
        assertThat(item.bucket())
                .isEqualTo(AiCreditBucket.FREE);
        assertThat(item.delta()).isEqualTo(3);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository).findByWallet_Id(
                eq(1L), captor.capture()
        );

        assertThat(captor.getValue().getSort()
                .getOrderFor("createdAt").isDescending()).isTrue();

        assertThat(captor.getValue().getSort()
                .getOrderFor("id").isDescending()).isTrue();
    }

    // [추가] 거래가 없으면 빈 목록 반환
    @Test
    void 거래_내역이_없으면_빈_페이지를_반환한다() {

        AiCreditWallet wallet = mock(AiCreditWallet.class);
        given(wallet.getId()).willReturn(1L);

        given(walletRepository.findByMember_Id(2L))
                .willReturn(Optional.of(wallet));

        given(transactionRepository.findByWallet_Id(
                eq(1L), any(Pageable.class)
        )).willReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)
        );

        PageResponse<AiCreditTransactionResponse> response =
                creditQueryService.getTransactions(2L, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    // [추가] 본인 지갑이 없으면 조회 불가
    @Test
    void 지갑이_없으면_거래_내역도_조회할_수_없다() {

        given(walletRepository.findByMember_Id(99L))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                () -> creditQueryService.getTransactions(99L, 0, 20)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(transactionRepository);
    }
}
