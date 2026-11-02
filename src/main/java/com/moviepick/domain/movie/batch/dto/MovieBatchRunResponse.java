package com.moviepick.domain.movie.batch.dto;

import org.springframework.batch.core.BatchStatus;

public record MovieBatchRunResponse(
        Long executionId,
        String jobName,
        BatchStatus status
) {
}
