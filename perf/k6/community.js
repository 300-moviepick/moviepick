// 커뮤니티 (perf-part1.md 4장)
//   postsPopular     [주력] GET /api/community/posts?sort=popular (page 0~2, 공개)
//   postDetailHot           GET /api/community/posts/{HOT_POST_ID} — 같은 게시글 집중 (조회수 UPDATE 행 락 경합)
//   postDetailSpread        GET /api/community/posts/{POST_MIN~POST_MAX 중 무작위} — 분산
//   commentWrite     [쓰기] POST /api/community/posts/{HOT_POST_ID}/comments — 같은 게시글에 댓글 작성 (VU별 회원)
//   -e HOT_POST_ID, POST_MIN, POST_MAX 는 run.sh가 DB에서 조회해 넘긴다.
import { buildOptions, getCsrf, loginUsers, makeSummary, maxVus, measureGet, measureWrite, pause, requireEnv,
  vuToken } from './common.js';

const SCENARIO = __ENV.SCENARIO || 'postsPopular';
export const options = buildOptions(SCENARIO);
export const handleSummary = makeSummary('community');

export function setup() {
  const csrf = getCsrf();
  const tokens = SCENARIO === 'commentWrite' ? loginUsers(csrf, maxVus()) : [];
  return { csrf, tokens };
}

export function postsPopular() {
  measureGet(`/api/community/posts?sort=popular&page=${__ITER % 3}&size=20`, 'posts_popular');
  pause();
}

export function postDetailHot() {
  measureGet(`/api/community/posts/${requireEnv('HOT_POST_ID')}`, 'post_detail_hot');
  pause();
}

export function postDetailSpread() {
  const min = Number(requireEnv('POST_MIN'));
  const max = Number(requireEnv('POST_MAX'));
  const postId = min + Math.floor(Math.random() * (max - min + 1));
  measureGet(`/api/community/posts/${postId}`, 'post_detail_spread');
  pause();
}

export function commentWrite(data) {
  const body = { content: `perf 부하 테스트 댓글 VU ${__VU} 반복 ${__ITER}` };
  measureWrite('POST', `/api/community/posts/${requireEnv('HOT_POST_ID')}/comments`, body, 'comment_create',
    vuToken(data), data.csrf);
  pause();
}
