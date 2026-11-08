package com.moviepick.domain.report.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class CommunityReportRequest {

    private static final Set<String> ALLOWED_REASONS =
            Set.of("SPAM", "ABUSE", "SPOILER", "ADULT", "ETC");

    @NotBlank(message = "신고 유형은 필수입니다.")
    private String reason;

    @Size(max = 200, message = "상세 사유는 200자 이하여야 합니다.")
    private String detail;

    @AssertTrue(message = "올바르지 않은 신고 유형입니다.")
    public boolean isReasonValid() {
        return reason == null || ALLOWED_REASONS.contains(reason);
    }

    @AssertTrue(message = "기타 신고는 상세 사유가 필수입니다.")
    public boolean isDetailValidForEtc() {
        return !"ETC".equals(reason)
                || (detail != null && !detail.isBlank());
    }
}
