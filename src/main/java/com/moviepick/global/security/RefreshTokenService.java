package com.moviepick.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    // Redis에 Refresh Token 저장
    public void save(Long memberId, String refreshToken) {

        String key = createKey(refreshToken);

        redisTemplate.opsForValue().set(
                key,
                memberId.toString(),
                JwtProvider.REFRESH_TOKEN_TTL
        );
    }

    // Refresh Token 삭제
    public void delete(String refreshToken) {

        String key = createKey(refreshToken);

        redisTemplate.delete(key);
    }

    // Refresh Token을 SHA-256으로 해싱하여 Redis Key 생성
    private String createKey(String refreshToken) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return "refresh:" + HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.", e
            );
        }
    }

    // 회원별 현재 인증 버전 조회
    public long getAuthVersion(Long memberId) {
        String key = "auth:version:" + memberId;
        String version = redisTemplate.opsForValue().get(key);

        return version == null ? 0L : Long.parseLong(version);
    }

    // 해당 회원의 기존 Refresh Token 전체 무효화
    public void invalidateAll(Long memberId) {
        String key = "auth:version:" + memberId;
        redisTemplate.opsForValue().increment(key);
    }

    // Refresh Token을 꺼내면서 삭제한다 (한 번만 사용 가능)
    public boolean consume(Long memberId, String refreshToken) {
        String savedMemberId = redisTemplate.opsForValue().getAndDelete(createKey(refreshToken));
        return memberId.toString().equals(savedMemberId);
    }
}
