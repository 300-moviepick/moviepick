// 안우성: 리뷰 (perf-part1.md 4장)
//   allReviewsLikes [주력] GET /api/movies/reviews?sort=likes (page 0~2, 공개)
//   movieReviews           GET /api/movies/{id}/reviews?sort=latest (인기 1위 / 일반 영화 번갈아, 공개)
//   reviewWrite     [쓰기] 같은 영화(인기 1위)에 리뷰 작성 → 삭제 반복 (VU별 회원 perf{__VU})
//                          더미 리뷰는 회원 501번부터라 VU 계정은 리뷰가 없다. 두 번째부터는 삭제된 리뷰 재작성 경로.
//   -e HOT_MOVIE_ID, NORMAL_MOVIE_ID 는 run.sh가 DB에서 조회해 넘긴다.
import { buildOptions, dataOf, getCsrf, loginUsers, makeSummary, maxVus, measureGet, measureWrite, pause,
  prepRequest, requireEnv, vuToken } from './common.js';

const SCENARIO = __ENV.SCENARIO || 'allReviewsLikes';
export const options = buildOptions(SCENARIO);
export const handleSummary = makeSummary('woosung');

export function setup() {
  const csrf = getCsrf();
  const tokens = SCENARIO === 'reviewWrite' ? loginUsers(csrf, maxVus()) : [];
  return { csrf, tokens };
}

export function allReviewsLikes() {
  measureGet(`/api/movies/reviews?sort=likes&page=${__ITER % 3}&size=20`, 'reviews_all_likes');
  pause();
}

export function movieReviews() {
  const hot = __ITER % 2 === 0;
  const movieId = hot ? requireEnv('HOT_MOVIE_ID') : requireEnv('NORMAL_MOVIE_ID');
  measureGet(`/api/movies/${movieId}/reviews?sort=latest&page=0&size=20`, hot ? 'movie_reviews_hot' : 'movie_reviews_normal');
  pause();
}

let cleaned = false;

export function reviewWrite(data) {
  const movieId = requireEnv('HOT_MOVIE_ID');
  const token = vuToken(data);
  if (!cleaned) {
    // 이전 실행이 끊겨 공개 리뷰가 남아 있으면 지운다 (상세 응답의 myReviewId 사용, 지표 제외)
    // myReviewId가 있을 때만 DELETE하므로 404가 날 일은 없다. 기대 상태는 명시만 한다.
    const detail = dataOf(prepRequest('GET', `/api/movies/${movieId}`, undefined, 'review_cleanup_detail', token,
      undefined, [200]));
    if (detail && detail.myReviewId) {
      prepRequest('DELETE', `/api/reviews/${detail.myReviewId}`, undefined, 'review_cleanup_delete', token, data.csrf,
        [200, 204]);
    }
    cleaned = true;
  }
  const body = { rating: 4.0, content: `perf 부하 테스트 리뷰 VU ${__VU}`, spoiler: false };
  const created = measureWrite('POST', `/api/movies/${movieId}/reviews`, body, 'review_create', token, data.csrf);
  const reviewId = dataOf(created)?.reviewId;
  if (reviewId) {
    measureWrite('DELETE', `/api/reviews/${reviewId}`, undefined, 'review_delete', token, data.csrf);
  }
  pause();
}
