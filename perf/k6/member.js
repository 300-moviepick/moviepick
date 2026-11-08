// 회원·관리자·찜 (perf-part1.md 4장)
//   adminReports   [주력] GET /api/admin/reports (전체 / status=PENDING 번갈아, page 0~4) — 관리자 토큰 1개 공유
//   myWishlist            GET /api/members/me/wishlist — VU별 회원 perf{__VU} 토큰
//   wishlistWrite  [쓰기] POST → DELETE /api/movies/{WISH_MOVIE_ID}/wishlist 반복
//                         WISH_MOVIE_ID: 더미 찜에서 제외한 popularity 최하위 영화 (run.sh가 -e로 전달)
import { buildOptions, getCsrf, loginAdmin, loginUsers, makeSummary, maxVus, measureGet, measureWrite,
  pause, prepRequest, requireEnv, vuToken } from './common.js';

const SCENARIO = __ENV.SCENARIO || 'adminReports';
export const options = buildOptions(SCENARIO);
export const handleSummary = makeSummary('member');

export function setup() {
  const csrf = getCsrf();
  if (SCENARIO === 'adminReports') {
    return { csrf, admin: loginAdmin(csrf), tokens: [] };
  }
  return { csrf, admin: null, tokens: loginUsers(csrf, maxVus()) };
}

export function adminReports(data) {
  const withStatus = __ITER % 2 === 0;
  const page = __ITER % 5;
  const path = `/api/admin/reports?${withStatus ? 'status=PENDING&' : ''}page=${page}&size=20`;
  measureGet(path, withStatus ? 'admin_reports_pending' : 'admin_reports_all', data.admin);
  pause();
}

export function myWishlist(data) {
  measureGet('/api/members/me/wishlist?page=0&size=20', 'my_wishlist', vuToken(data));
  pause();
}

let cleaned = false;

export function wishlistWrite(data) {
  const movieId = requireEnv('WISH_MOVIE_ID');
  const token = vuToken(data);
  const path = `/api/movies/${movieId}/wishlist`;
  if (!cleaned) {
    // 이전 실행이 중간에 끊겨 찜이 남아 있으면 지운다. 보통은 찜이 없어 404가 정상이므로
    // 200·404를 기대 상태로 지정해 http_req_failed에 잡히지 않게 한다 (리허설의 측정 외 실패 50건 = VU 수의 원인)
    prepRequest('DELETE', path, undefined, 'wishlist_cleanup', token, data.csrf, [200, 204, 404]);
    cleaned = true;
  }
  measureWrite('POST', path, undefined, 'wishlist_add', token, data.csrf);
  measureWrite('DELETE', path, undefined, 'wishlist_remove', token, data.csrf);
  pause();
}
