package com.moviepick.domain.report.dto.request;

import com.moviepick.domain.report.entity.ReportStatus;

public record AdminReportProcessRequest(
        ReportStatus status
) {
}
