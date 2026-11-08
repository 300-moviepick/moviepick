package com.moviepick.domain.movie.batch.controller;

import com.moviepick.domain.movie.batch.dto.MovieBatchRunResponse;
import com.moviepick.domain.movie.batch.service.MovieBatchManualService;
import com.moviepick.domain.movie.exception.MovieErrorCode;
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

        // 지금은 배치를 동기로 실행해 끝난 상태만 돌아오므로 도달하지 않는다.
        // 비동기 실행으로 바꿀 때를 대비해 남겨 둔다.
        if (result.status().isRunning()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new ApiResponse<>(
                            true,
                            HttpStatus.ACCEPTED.value(),
                            "영화 수집 배치가 실행 중입니다.",
                            result
                    ));
        }

        MovieErrorCode errorCode = MovieErrorCode.BATCH_EXECUTION_FAILED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.errorWithData(errorCode, result));
    }
}
