# API 명세

> 엔드포인트는 각 Controller의 `@RequestMapping`과 `@Get/Post/Put/Patch/DeleteMapping`을 모두 모은 것입니다. 근거 파일은 `moviepick-be/src/main/java/com/moviepick/` 기준 경로입니다.

## 1. 공통 규칙

| 항목 | 내용 | 근거 |
|---|---|---|
| Base 경로 | 모든 API는 `/api`로 시작합니다. 운영에서는 화면과 같은 주소(`https://www.moviepick.kr/api/...`)로 호출합니다 | `Caddyfile`, `moviepick-fe/src/api/client.js` |
| Access 토큰 | 로그인·재발급 응답 본문으로 받고, `Authorization: Bearer {토큰}` 헤더로 보냅니다. 유효 30분. 프론트는 메모리에만 보관합니다 | `global/security/JwtProvider.java`, `global/security/JwtAuthenticationFilter.java`, `moviepick-fe/src/api/client.js` |
| Refresh 토큰 | `refreshToken` 쿠키(HttpOnly, SameSite=Strict, Path=/api/auth, 운영 Secure)로 받습니다. 유효 14일. 재발급할 때마다 새 토큰으로 바뀝니다 | `global/security/RefreshTokenCookieFactory.java`, `JwtProvider.java`, `application-prod.yml` |
| CSRF | `GET /api/auth/csrf`로 토큰을 받고, GET이 아닌 요청에 `X-XSRF-TOKEN` 헤더로 보냅니다. PortOne 웹훅만 제외합니다 | `global/security/SecurityConfig.java`, `moviepick-fe/src/api/client.js` |
| 공통 응답 | `{ success, code, message, data, errorCode }`. `errorCode`는 오류일 때만 들어갑니다 | `global/response/ApiResponse.java` |
| 오류 응답 | `{ success:false, code:HTTP상태, message, data:null, errorCode:"오류코드" }` | `global/exception/GlobalExceptionHandler.java` |
| 페이지 응답 | `data`가 `{ content, page, size, totalElements, totalPages }` | `global/response/PageResponse.java` |
| 권한 | **공개**: `PublicEndpoints` 목록 / **관리자**: `/api/admin/**`는 ADMIN 역할 / **회원**: 나머지 전부 로그인 필요 | `global/security/PublicEndpoints.java`, `SecurityConfig.java` |
| 정지·탈퇴 회원 | 토큰이 있어도 요청마다 DB에서 상태를 확인해 403으로 막습니다 | `JwtAuthenticationFilter.java` |

## 2. 엔드포인트 목록

권한 표기: 공개 / 회원 / 관리자

### 인증 · 회원 (8)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/auth/csrf | CSRF 토큰 발급 | 공개 |
| POST | /api/auth/members | 회원가입(무료 크레딧 3개 지급) | 공개 |
| POST | /api/auth/login | 로그인(access 토큰 + refresh 쿠키) | 공개 |
| POST | /api/auth/logout | 로그아웃(refresh 삭제·쿠키 만료) | 공개 |
| POST | /api/auth/refresh | 토큰 재발급(refresh 1회용 교체) | 공개(refresh 쿠키 필요) |
| GET | /api/members/me | 내 정보 | 회원 |
| PATCH | /api/members/me | 내 정보 수정 | 회원 |
| DELETE | /api/members/me | 회원 탈퇴 | 회원 |

근거: `global/security/CsrfController.java`, `domain/member/controller/AuthController.java`, `domain/member/controller/MemberController.java`
※ 소셜 로그인 시작(`/oauth2/authorization/{kakao|google}`)과 콜백(`/login/oauth2/code/{id}`)은 Spring Security가 처리하는 경로라 위 표에 넣지 않았습니다(`SecurityConfig.java`).

### 영화 · 랭킹 (4)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/movies | 영화 목록(keyword 검색, sort=releaseDate 최근 개봉순) | 공개 |
| GET | /api/movies/{movieId} | 영화 상세 | 공개 |
| GET | /api/movies/{movieId}/extras | 예고편·출연진·OTT(12시간 캐시) | 공개 |
| GET | /api/movies/ranking | 베이지안 평균 랭킹(5분 캐시) | 공개 |

근거: `domain/movie/controller/MovieController.java`, `domain/movie/controller/MovieRankingController.java`

### 리뷰 (8)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/movies/{movieId}/reviews | 리뷰 작성 | 회원 |
| GET | /api/movies/{movieId}/reviews | 영화별 리뷰 목록 | 공개 |
| GET | /api/movies/reviews | 전체 리뷰 목록(sort=likes면 앞 5페이지 캐시) | 공개 |
| GET | /api/members/me/reviews | 내 리뷰 목록 | 회원 |
| PATCH | /api/reviews/{reviewId} | 리뷰 수정 | 회원 |
| DELETE | /api/reviews/{reviewId} | 리뷰 삭제 | 회원 |
| POST | /api/reviews/{reviewId}/likes | 리뷰 좋아요 | 회원 |
| DELETE | /api/reviews/{reviewId}/likes | 리뷰 좋아요 취소 | 회원 |

근거: `domain/review/controller/ReviewController.java`

### 커뮤니티 (13)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/community/posts | 게시글 작성 | 회원 |
| GET | /api/community/posts | 게시글 목록(sort=latest/popular, q 검색) | 공개 |
| GET | /api/community/posts/{postId} | 게시글 상세(조회 수 +1) | 공개 |
| PATCH | /api/community/posts/{postId} | 게시글 수정 | 회원 |
| DELETE | /api/community/posts/{postId} | 게시글 삭제 | 회원 |
| POST | /api/community/posts/{postId}/likes | 게시글 좋아요 | 회원 |
| DELETE | /api/community/posts/{postId}/likes | 게시글 좋아요 취소 | 회원 |
| POST | /api/community/posts/{postId}/comments | 댓글 작성 | 회원 |
| GET | /api/community/posts/{postId}/comments | 댓글 목록 | 공개 |
| PATCH | /api/community/comments/{commentId} | 댓글 수정 | 회원 |
| DELETE | /api/community/comments/{commentId} | 댓글 삭제 | 회원 |
| POST | /api/community/comments/{commentId}/likes | 댓글 좋아요 | 회원 |
| DELETE | /api/community/comments/{commentId}/likes | 댓글 좋아요 취소 | 회원 |

근거: `domain/community/controller/PostController.java`, `PostLikeController.java`, `CommentController.java`, `CommentLikeController.java`

### 신고 (3)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/community/posts/{postId}/reports | 게시글 신고 | 회원 |
| POST | /api/community/comments/{commentId}/reports | 댓글 신고 | 회원 |
| POST | /api/reviews/{reviewId}/reports | 리뷰 신고 | 회원 |

근거: `domain/report/controller/CommunityReportController.java`

### 찜 · 알림 (7)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/movies/{movieId}/wishlist | 찜 추가 | 회원 |
| DELETE | /api/movies/{movieId}/wishlist | 찜 삭제 | 회원 |
| GET | /api/members/me/wishlist | 내 찜 목록 | 회원 |
| GET | /api/notifications | 알림 목록 | 회원 |
| GET | /api/notifications/unread-count | 안 읽은 알림 수 | 회원 |
| PATCH | /api/notifications/{notificationId}/read | 알림 읽음 | 회원 |
| PATCH | /api/notifications/read-all | 모두 읽음 | 회원 |

근거: `domain/wishlist/controller/WishlistController.java`, `domain/notification/controller/NotificationController.java`

### AI 추천 · 크레딧 (8)
| 메서드 | 경로 | 설명 | 권한 | 비고 |
|---|---|---|---|---|
| POST | /api/ai/recommendations | AI 추천(크레딧 1개 사용, 영화 3편 + 이유) | 회원 | **조건부** |
| PUT | /api/ai/feedback/{movieId} | 추천 영화 좋아요·싫어요 저장 | 회원 | |
| DELETE | /api/ai/feedback/{movieId} | 피드백 삭제 | 회원 | |
| GET | /api/ai/credits | 내 크레딧 잔액 | 회원 | |
| GET | /api/ai/credits/transactions | 크레딧 사용 내역(원장) | 회원 | |
| GET | /api/ai/credits/products | 크레딧 상품 목록 | 회원 | **조건부** |
| POST | /api/ai/credits/purchases | 크레딧 결제 생성 | 회원 | **조건부** |
| POST | /api/ai/credits/purchases/{paymentId}/confirm | 크레딧 결제 확인·지급 | 회원 | 항상 등록 |

근거: `domain/ai/recommendation/controller/AiRecommendationController.java`, `AiRecommendationFeedbackController.java`, `domain/ai/credit/controller/AiCreditController.java`, `AiCreditStoreController.java`, `AiCreditPurchaseController.java`

### 후원 결제 (2)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/supports | 후원 결제 생성(3,000·6,000·9,000원) | 회원 |
| POST | /api/supports/{paymentId}/confirm | 후원 결제 확인 | 회원 |

근거: `domain/support/controller/SupportController.java`

### 관리자 (9)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | /api/admin/members | 회원 목록(상태 필터) | 관리자 |
| PATCH | /api/admin/members/{memberId}/status | 회원 상태 변경 | 관리자 |
| GET | /api/admin/reports | 신고 목록(상태 필터) | 관리자 |
| PATCH | /api/admin/reports/{id} | 신고 승인·거절(승인 시 숨김·제재) | 관리자 |
| PATCH | /api/admin/posts/{postId}/blind | 게시글 숨김 | 관리자 |
| PATCH | /api/admin/comments/{commentId}/blind | 댓글 숨김 | 관리자 |
| PATCH | /api/admin/reviews/{reviewId}/blind | 리뷰 숨김 | 관리자 |
| POST | /api/admin/movies/sync | 영화 동기화(pages 1~5 페이지 즉시 수집) | 관리자 |
| POST | /api/admin/batch/movies/sync | 영화 수집 배치 수동 실행 | 관리자 |

근거: `domain/admin/controller/AdminMemberController.java`, `AdminReportController.java`, `AdminPostController.java`, `AdminCommentController.java`, `AdminReviewController.java`, `AdminMovieController.java`, `domain/movie/batch/controller/MovieBatchAdminController.java`

### 웹훅 (1)
| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | /api/webhooks/portone | PortOne 결제 웹훅(후원·크레딧 공통 주소) | 공개 + 서명 검증, CSRF 제외 |

근거: `domain/payment/controller/SupportWebhookController.java`, `PublicEndpoints.java`, `SecurityConfig.java`
- 서명은 Standard Webhooks 방식(HMAC-SHA256), 시각 허용 범위 ±5분입니다(`domain/payment/client/webhook/PortOneWebhookVerifier.java`).
- 웹훅은 PortOne 콘솔에 운영 주소(`api.moviepick.kr`)로 등록되어 있고, 서명 검증과 지급 처리를 운영에서 확인했습니다(PortOne V2 테스트 모드).

## 3. 조건부 등록 API

`spring.ai.model.chat` 값이 `openai`일 때만 아래 3개가 등록됩니다. 기본값은 `none`이라, AI를 끄면 이 경로는 **404**가 됩니다(`src/main/resources/application.yml`).

| API | 근거 |
|---|---|
| POST /api/ai/recommendations | `AiRecommendationController.java` |
| GET /api/ai/credits/products | `AiCreditStoreController.java` |
| POST /api/ai/credits/purchases | `AiCreditStoreController.java` |

크레딧 결제 확인(`/purchases/{paymentId}/confirm`)은 AI를 꺼도 항상 등록됩니다. 돈을 낸 결제는 반드시 지급하기 위해서입니다(`AiCreditPurchaseController.java` 주석).

## 4. 엔드포인트 총수

| 구분 | 수 |
|---|---|
| 인증·회원 | 8 |
| 영화·랭킹 | 4 |
| 리뷰 | 8 |
| 커뮤니티 | 13 |
| 신고 | 3 |
| 찜·알림 | 7 |
| AI 추천·크레딧 | 8 (조건부 3 포함) |
| 후원 | 2 |
| 관리자 | 9 |
| 웹훅 | 1 |
| **합계** | **63** (AI를 끄면 60) |

※ Spring Security가 처리하는 소셜 로그인 경로 2개(`/oauth2/authorization/**`, `/login/oauth2/code/**`)는 합계에 넣지 않았습니다.
