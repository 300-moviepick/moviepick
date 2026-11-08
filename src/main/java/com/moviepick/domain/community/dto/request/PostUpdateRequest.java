package com.moviepick.domain.community.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(

        @Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "제목은 공백일 수 없습니다.")
        String title,

        @Size(min = 1, max = 5000, message = "내용은 1~5000자여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "내용은 공백일 수 없습니다.")
        String content

) {
}