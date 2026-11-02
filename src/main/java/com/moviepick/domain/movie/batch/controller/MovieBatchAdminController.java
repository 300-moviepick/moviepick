package com.moviepick.domain.movie.batch.controller;

import com.moviepick.domain.movie.batch.dto.MovieBatchRunResponse;
import com.moviepick.domain.movie.batch.service.MovieBatchManualService;
import com.moviepick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/batch")
public class MovieBatchAdminController {

    private final MovieBatchManualService movieBatchManualService;

    @PostMapping("/movies/sync")
    public ResponseEntity<ApiResponse<MovieBatchRunResponse>> syncMovies() {

        MovieBatchRunResponse result = movieBatchManualService.runSync();

        if (result.status() == BatchStatus.COMPLETED) {
            return ResponseEntity.ok(ApiResponse.ok(result));
        }

        if (result.status().isRunning()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new ApiResponse<>(
                            true,
                            202,
                            "영화 수집 배치가 실행 중입니다.",
                            result
                    ));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(
                        false,
                        500,
                        "영화 수집 배치가 정상 완료되지 않았습니다.",
                        result,
                        "BATCH_EXECUTION_FAILED"
                ));
    }
}
