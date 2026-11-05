package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;

import java.util.List;

public interface AiMovieReasonGenerator {

    // 후보 영화의 추천 이유 생성
    List<AiMovieReason> generate(
            String userRequest,
            List<AiMovieCandidate> candidates
    );
}