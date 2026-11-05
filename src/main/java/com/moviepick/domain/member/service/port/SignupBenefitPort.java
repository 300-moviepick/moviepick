package com.moviepick.domain.member.service.port;

import com.moviepick.domain.member.entity.Member;

public interface SignupBenefitPort {

    // 신규 회원가입 혜택 지급
    void grantTo(Member member);
}