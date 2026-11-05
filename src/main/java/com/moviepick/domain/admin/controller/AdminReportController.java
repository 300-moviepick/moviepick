package com.moviepick.domain.admin.controller;

import com.moviepick.domain.report.dto.request.AdminReportProcessRequest;
import com.moviepick.domain.report.dto.response.AdminReportResponse;
import com.moviepick.domain.report.entity.ReportStatus;
import com.moviepick.domain.report.service.AdminReportService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    // 관리자: 신고 목록 조회 (status 생략 시 전체, 최신순)
    // 잘못된 status 값은 회원 목록과 같이 enum 변환 실패로 400 INVALID_INPUT
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminReportResponse>>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            // 범위(1~100)는 공통 페이지 규칙(PageRequestConfig)이 검사
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(adminReportService.getReports(status, page, size))
        ));
    }

    // 관리자: 신고 승인 또는 반려
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminReportResponse>> processReport(
            @PathVariable Long id,
            @RequestBody AdminReportProcessRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminReportService.processReport(id, request)
        ));
    }
}
