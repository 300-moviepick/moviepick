package com.moviepick.global.security;

import com.moviepick.domain.member.service.MemberStatusChecker;
import com.moviepick.domain.member.controller.MemberController;
import com.moviepick.domain.member.dto.response.LoginResponse;
import com.moviepick.domain.member.dto.response.MemberResponse;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.member.entity.Role;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.member.controller.AuthController;
import com.moviepick.domain.member.service.AuthService;
import com.moviepick.domain.member.oauth.OAuth2LoginFailureHandler;
import com.moviepick.domain.member.oauth.CustomOAuth2UserService;
import com.moviepick.domain.member.oauth.CustomOidcUserService;
import com.moviepick.domain.member.dto.response.LoginResult;
import com.moviepick.domain.member.dto.response.TokenPair;
import com.moviepick.domain.member.oauth.OAuth2LoginSuccessHandler;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AUTH-01: 로그인 상태의 요청이 CSRF 쿠키를 지우거나 세션을 만들지 않는지 확인한다. */
@WebMvcTest(
        controllers = {
                MemberController.class,
                AuthController.class,
                CsrfController.class
        },
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=test-google",
                "spring.security.oauth2.client.registration.google.client-secret=test-secret",
                "spring.security.oauth2.client.registration.kakao.client-id=test-kakao",
                "spring.security.oauth2.client.registration.kakao.client-secret=test-secret"
        }
)
@Import({
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        MemberStatusChecker.class,
        OAuth2LoginFailureHandler.class
})
@ActiveProfiles("test")
class ApiCsrfSessionTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String CSRF_TOKEN = "csrf-token";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthService authService;

    // SecurityConfig의 OAuth2 관련 의존성
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private CustomOidcUserService customOidcUserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @BeforeEach
    void setUp() {
        given(jwtProvider.validateToken(ACCESS_TOKEN)).willReturn(
                Jwts.claims().subject("1").add("type", "access").build());

        Member member = mock(Member.class);
        given(member.getRole()).willReturn(Role.USER);
        given(memberService.getActiveMember(1L)).willReturn(member);

        MemberResponse response = new MemberResponse(
                1L, "user@test.com", "user", Role.USER, MemberStatus.ACTIVE);
        given(memberService.getMyInfo(anyLong())).willReturn(response);
        given(memberService.updateMember(anyLong(), any())).willReturn(response);
    }

    @Test
    @DisplayName("로그인 상태에서 같은 CSRF 토큰으로 변경 요청을 두 번 보내면 둘 다 성공하고 쿠키와 세션이 바뀌지 않는다")
    void twoAuthenticatedWrites_keepCsrfCookie() throws Exception {
        Cookie csrfCookie = new Cookie(CSRF_COOKIE, CSRF_TOKEN);

        MvcResult first = mockMvc.perform(updateMe(csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertCsrfCookieNotExpired(first);
        assertNoSession(first);

        // 첫 응답이 내려준 쿠키를 브라우저처럼 다음 요청에 반영
        MvcResult second = mockMvc.perform(updateMe(nextCsrfCookie(first, csrfCookie)))
                .andExpect(status().isOk())
                .andReturn();
        assertCsrfCookieNotExpired(second);
        assertNoSession(second);
    }

    @Test
    @DisplayName("로그인 상태의 GET 뒤에 보낸 변경 요청도 성공한다")
    void authenticatedGetThenWrite_succeeds() throws Exception {
        Cookie csrfCookie = new Cookie(CSRF_COOKIE, CSRF_TOKEN);

        MvcResult read = mockMvc.perform(get("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .cookie(csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertCsrfCookieNotExpired(read);
        assertNoSession(read);

        mockMvc.perform(updateMe(nextCsrfCookie(read, csrfCookie)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 상태라도 CSRF 토큰 없는 변경 요청은 403이다")
    void authenticatedWriteWithoutCsrf_forbidden() throws Exception {
        mockMvc.perform(patch("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"changed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인, 로그아웃, refresh는 CSRF 토큰이 있으면 성공하고 없으면 403이다")
    void authEndpoints_requireCsrf() throws Exception {
        LoginResponse loginResponse =
                new LoginResponse(1L, "user", Role.USER, ACCESS_TOKEN, "Bearer", 1800);
        given(authService.login(any()))
                .willReturn(new LoginResult(loginResponse, "refresh-token"));
        given(authService.refreshAccessToken(any()))
                .willReturn(new TokenPair(ACCESS_TOKEN, "new-refresh-token"));

        Cookie csrfCookie = new Cookie(CSRF_COOKIE, CSRF_TOKEN);
        String loginBody = "{\"email\":\"user@test.com\",\"password\":\"password1\"}";

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder updateMe(Cookie csrfCookie) {
        return patch("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .cookie(csrfCookie)
                .header(CSRF_HEADER, CSRF_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"changed\"}");
    }

    // 응답이 CSRF 쿠키를 새로 내려주면 그 값으로 바꾼다
    private Cookie nextCsrfCookie(MvcResult result, Cookie current) {
        Cookie issued = result.getResponse().getCookie(CSRF_COOKIE);
        return issued == null ? current : issued;
    }

    private void assertCsrfCookieNotExpired(MvcResult result) {
        Cookie issued = result.getResponse().getCookie(CSRF_COOKIE);
        if (issued != null) {
            assertThat(issued.getMaxAge()).isNotZero();
            assertThat(issued.getValue()).isNotEmpty();
        }
    }

    private void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
