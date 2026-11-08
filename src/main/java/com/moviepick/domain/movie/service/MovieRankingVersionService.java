package com.moviepick.domain.movie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MovieRankingVersionService {

    private static final String RANKING_VERSION_KEY = "movieRanking:version";

    private final StringRedisTemplate redisTemplate;

    // 현재 랭킹 캐시 버전 조회
    public long getVersion() {
        String version = redisTemplate.opsForValue().get(RANKING_VERSION_KEY);

        return version == null ? 0L : Long.parseLong(version);
    }

    // 랭킹 데이터 변경 시 버전 증가
    public void increaseVersion() {
        redisTemplate.opsForValue().increment(RANKING_VERSION_KEY);
    }
}
