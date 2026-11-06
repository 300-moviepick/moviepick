// 팀장 리허설: 영화 (담당 미지정, 공개 조회만 — 토큰·쓰기 없음)
//   moviesRecent [주력] GET /api/movies?sort=releaseDate (page 0~4)
//   ranking             GET /api/movies/ranking?page=0&size=10 (Redis 캐시 5분)
import { buildOptions, getCsrf, makeSummary, measureGet, pause } from './common.js';

const SCENARIO = __ENV.SCENARIO || 'moviesRecent';
export const options = buildOptions(SCENARIO);
export const handleSummary = makeSummary('rehearsal');

// 로그인은 없지만 서버 연결을 먼저 확인한다
export function setup() {
  getCsrf();
  return {};
}

export function moviesRecent() {
  measureGet(`/api/movies?sort=releaseDate&page=${__ITER % 5}&size=20`, 'movies_recent');
  pause();
}

export function ranking() {
  measureGet('/api/movies/ranking?page=0&size=10', 'movies_ranking');
  pause();
}
