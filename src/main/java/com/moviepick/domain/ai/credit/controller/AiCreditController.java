package com.moviepick.domain.ai.credit.controller;

import com.moviepick.domain.ai.credit.dto.response.AiCreditBalanceResponse;
import com.moviepick.domain.ai.credit.dto.response.AiCreditTransactionResponse;
import com.moviepick.domain.ai.credit.service.AiCreditQueryService;
import com.moviepick.domain.ai.credit.service.AiCreditWalletProvisioner;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/credits")
public class AiCreditController {

    private final AiCreditQueryService creditQueryService;
    private final AiCreditWalletProvisioner walletProvisioner;

    // 로그인 회원의 무료·유료 크레딧 조회
    // 지갑 생성은 조회 트랜잭션(readOnly)이 시작되기 전에 끝낸다. 같은 트랜잭션에서 만들면 스냅샷에 안 보일 수 있다
    @GetMapping
    public ResponseEntity<ApiResponse<AiCreditBalanceResponse>> getMyCredits(
            @LoginMember Long memberId
    ) {
        walletProvisioner.ensureWallet(memberId);

        AiCreditBalanceResponse response =
                creditQueryService.getBalance(memberId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // [추가] 로그인 회원의 거래 내역 조회
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<AiCreditTransactionResponse>>>
    getMyTransactions(
            @LoginMember Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        walletProvisioner.ensureWallet(memberId);

        PageResponse<AiCreditTransactionResponse> response =
                creditQueryService.getTransactions(
                        memberId, page, size
                );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}