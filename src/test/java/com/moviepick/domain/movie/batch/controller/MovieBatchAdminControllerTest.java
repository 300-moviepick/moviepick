package com.moviepick.domain.movie.batch.controller;

import com.moviepick.domain.movie.batch.dto.MovieBatchRunResponse;
import com.moviepick.domain.movie.batch.service.MovieBatchManualService;
import com.moviepick.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 관리자 영화 수집 배치 수동 실행: 배치 상태별 HTTP 상태와 응답 JSON 전체 (R15 정리 전후 동일해야 함)
@WebMvcTest(
        controllers = MovieBatchAdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class MovieBatchAdminControllerTest {

    private static final String URL = "/api/admin/batch/movies/sync";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieBatchManualService movieBatchManualService;

    @Test
    @DisplayName("COMPLETED면 200과 실행 결과")
    void completed() throws Exception {
        given(movieBatchManualService.runSync()).willReturn(result(BatchStatus.COMPLETED));

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"code":200,"message":"요청에 성공하였습니다.",
                         "data":{"executionId":7,"jobName":"movieSyncJob","status":"COMPLETED"}}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("실행 중 상태면 202와 실행 결과")
    void running() throws Exception {
        given(movieBatchManualService.runSync()).willReturn(result(BatchStatus.STARTED));

        mockMvc.perform(post(URL))
                .andExpect(status().isAccepted())
                .andExpect(content().json("""
                        {"success":true,"code":202,"message":"영화 수집 배치가 실행 중입니다.",
                         "data":{"executionId":7,"jobName":"movieSyncJob","status":"STARTED"}}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("실패로 끝나면 500, errorCode BATCH_EXECUTION_FAILED와 실행 결과")
    void failed() throws Exception {
        given(movieBatchManualService.runSync()).willReturn(result(BatchStatus.FAILED));

        mockMvc.perform(post(URL))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"success":false,"code":500,"message":"영화 수집 배치가 정상 완료되지 않았습니다.",
                         "data":{"executionId":7,"jobName":"movieSyncJob","status":"FAILED"},
                         "errorCode":"BATCH_EXECUTION_FAILED"}
                        """, JsonCompareMode.STRICT));
    }

    private MovieBatchRunResponse result(BatchStatus status) {
        return new MovieBatchRunResponse(7L, "movieSyncJob", status);
    }
}
