package com.moviepick.global.security;

import com.moviepick.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    // React에서 사용할 CSRF 토큰 발급
    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<Map<String, String>>> getCsrfToken(
            CsrfToken csrfToken
    ) {
        // 지연 생성된 CSRF 토큰을 실제로 생성
        String token = csrfToken.getToken();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", token
        )));
    }
}
