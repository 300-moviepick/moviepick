// MoviePick 부하 테스트 공통 모듈 (perf/k6/common.js)
//
// 실행 옵션 (-e)
//   BASE_URL  서버 주소 (기본 http://host.docker.internal:8080)
//   MODE      base(기준: 30초 상승 → 1분 50 VU 유지 → 30초 종료) | stress(한계: 50→100→200→500 단계)
//   SCENARIO  담당 스크립트 안의 시나리오 함수 이름
//   PASSWORD  더미 계정 비밀번호 (기본 password, perf/data/01_generate.sql 참고)
//   SLEEP     반복 사이 대기 초 (기본 1)
//   OUT_DIR   결과 저장 폴더 (기본 /results)
//
// 규칙
// - 측정 요청에만 태그 kind=measure를 붙이고, 임계값(p95<500ms, 실패율<1%)은 이 태그 기준이다.
//   CSRF·로그인·준비 요청은 setup() 또는 kind=prep로 보내 지표에서 뺀다.
// - 로그인은 setup()에서 필요한 계정을 미리 전부 한다 (측정 중 BCrypt 부하가 섞이지 않게).
//   VU는 tokens[__VU - 1]을 쓴다. refresh는 쓰지 않는다 (access 토큰 30분 안에 1회 실행이 끝남).
// - 공개 조회는 토큰 없이, 인증 API만 토큰을 붙인다.
// - CSRF: 쿠키 XSRF-TOKEN과 헤더 X-XSRF-TOKEN에 같은 값을 넣는다 (CsrfTokenRequestAttributeHandler, 마스킹 없음).
// - 한계 측정(stress)은 요청마다 VU 태그 stage(ramping-vus 단계 번호)를 붙여 유지 단계(50/100/200/500 VU)별로
//   p95·평균·실패율·RPS를 따로 내고, 기준을 처음 넘는 단계를 "한계 단계"로 표시한다 (handleSummary).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
import { tagWithCurrentStageIndex } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
export const MODE = __ENV.MODE === 'stress' ? 'stress' : 'base';
export const PASSWORD = __ENV.PASSWORD || 'password';
export const ADMIN_EMAIL = 'perf-admin@perf.local';
const SLEEP_SEC = Number(__ENV.SLEEP || 1);
const LOGIN_BATCH = 20;

const STAGES = {
  base: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  stress: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 500 },
    { duration: '1m', target: 500 },
    { duration: '30s', target: 0 },
  ],
};

// 한계 측정에서 판정에 쓰는 "유지" 단계 (STAGES.stress의 순번 → VU 수). 상승·종료 구간은 판정에서 뺀다.
const HOLD_STAGES = [
  { index: 1, vus: 50 },
  { index: 3, vus: 100 },
  { index: 5, vus: 200 },
  { index: 7, vus: 500 },
];
const HOLD_SECONDS = 60;
const P95_LIMIT_MS = 500;
const FAIL_LIMIT = 0.01;

// 이번 실행의 최대 VU 수 (setup에서 로그인할 계정 수)
export function maxVus() {
  return Math.max(...STAGES[MODE].map((s) => s.target));
}

function stageThresholds() {
  if (MODE !== 'stress') return {};
  const t = {};
  HOLD_STAGES.forEach(({ index }) => {
    t[`http_req_duration{kind:measure,stage:${index}}`] = [`p(95)<${P95_LIMIT_MS}`];
    t[`http_req_failed{kind:measure,stage:${index}}`] = [`rate<${FAIL_LIMIT}`];
    t[`http_reqs{kind:measure,stage:${index}}`] = ['count>=0'];   // RPS 계산용 하위 지표 출력
  });
  return t;
}

// scenarioName은 담당 스크립트에서 export한 함수 이름과 같아야 한다
export function buildOptions(scenarioName) {
  return {
    setupTimeout: '5m',
    scenarios: {
      [scenarioName]: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: STAGES[MODE],
        gracefulRampDown: '10s',
        exec: scenarioName,
      },
    },
    thresholds: {
      'http_req_duration{kind:measure}': [`p(95)<${P95_LIMIT_MS}`],
      'http_req_failed{kind:measure}': [`rate<${FAIL_LIMIT}`],
      ...stageThresholds(),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  };
}

export function pause() {
  if (SLEEP_SEC > 0) sleep(SLEEP_SEC);
}

function jsonData(res) {
  try {
    return res.json('data');
  } catch {
    return null;
  }
}

// ---------- setup 전용 ----------

export function getCsrf() {
  const res = http.get(`${BASE_URL}/api/auth/csrf`, { tags: { kind: 'prep', name: 'csrf' } });
  const token = jsonData(res)?.token;
  if (res.status !== 200 || !token) {
    throw new Error(`CSRF 토큰 발급 실패: status=${res.status} (서버 주소 ${BASE_URL} 확인)`);
  }
  return token;
}

function csrfParams(csrf, extraHeaders, tags) {
  return {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf, ...(extraHeaders || {}) },
    cookies: { 'XSRF-TOKEN': { value: csrf, replace: true } },
    tags,
  };
}

function loginRequests(csrf, emails) {
  return emails.map((email) => ({
    method: 'POST',
    url: `${BASE_URL}/api/auth/login`,
    body: JSON.stringify({ email, password: PASSWORD }),
    params: csrfParams(csrf, null, { kind: 'prep', name: 'login' }),
  }));
}

// perf1 ~ perf{count} 계정을 20개씩 병렬로 로그인해 accessToken 배열을 돌려준다
export function loginUsers(csrf, count) {
  const tokens = [];
  for (let start = 1; start <= count; start += LOGIN_BATCH) {
    const emails = [];
    for (let n = start; n < start + LOGIN_BATCH && n <= count; n++) emails.push(`perf${n}@perf.local`);
    const responses = http.batch(loginRequests(csrf, emails));
    responses.forEach((res, i) => {
      const token = jsonData(res)?.accessToken;
      if (res.status !== 200 || !token) {
        throw new Error(
          `로그인 실패: ${emails[i]} status=${res.status}. ` +
            '더미 데이터 적재 여부와 비밀번호 해시(01_generate.sql @pw)를 확인하세요.',
        );
      }
      tokens.push(token);
    });
  }
  return tokens;
}

// 관리자 테스트 계정은 1회만 로그인하고 모든 VU가 토큰을 함께 쓴다
export function loginAdmin(csrf) {
  const [res] = http.batch(loginRequests(csrf, [ADMIN_EMAIL]));
  const token = jsonData(res)?.accessToken;
  if (res.status !== 200 || !token) {
    throw new Error(`관리자 테스트 계정 로그인 실패: status=${res.status}`);
  }
  return token;
}

export function vuToken(data) {
  return data.tokens[(__VU - 1) % data.tokens.length];
}

export function requireEnv(name) {
  const value = __ENV[name];
  if (!value) throw new Error(`-e ${name} 값이 필요합니다 (bash perf/run.sh measure가 자동으로 넘깁니다).`);
  return value;
}

// ---------- 요청 ----------

// 현재 ramping-vus 단계 번호를 VU 태그 stage로 붙인다 (setup 등 시나리오 밖에서는 건너뜀)
function markStage() {
  try {
    tagWithCurrentStageIndex();
  } catch {
    // 시나리오 밖 호출
  }
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function verify(res, name, kind, okStatuses) {
  const ok = okStatuses ? okStatuses.includes(res.status) : res.status >= 200 && res.status < 300;
  check(res, { [`${name} 성공`]: () => ok }, { kind, name });
  return ok;
}

// 측정용 GET (token이 없으면 공개 조회)
export function measureGet(path, name, token) {
  markStage();
  const res = http.get(`${BASE_URL}${path}`, { headers: authHeaders(token), tags: { kind: 'measure', name } });
  verify(res, name, 'measure');
  return res;
}

// 측정용 쓰기 (POST/PATCH/DELETE, CSRF 포함)
export function measureWrite(method, path, body, name, token, csrf) {
  markStage();
  const params = csrfParams(csrf, authHeaders(token), { kind: 'measure', name });
  const res = http.request(method, `${BASE_URL}${path}`, body === undefined ? null : JSON.stringify(body), params);
  verify(res, name, 'measure');
  return res;
}

// 지표에서 뺄 준비·정리 요청 (kind=prep라 측정 임계값에 들어가지 않음)
// expected: 의도된 응답 상태 코드 목록 (예: 정리 DELETE의 404). 지정하면 http_req_failed에도 잡히지 않는다.
export function prepRequest(method, path, body, name, token, csrf, expected) {
  markStage();
  const params = csrf
    ? csrfParams(csrf, authHeaders(token), { kind: 'prep', name })
    : { headers: authHeaders(token), tags: { kind: 'prep', name } };
  if (expected && expected.length) {
    params.responseCallback = http.expectedStatuses(...expected);
  }
  return http.request(method, `${BASE_URL}${path}`, body === undefined ? null : JSON.stringify(body), params);
}

export function dataOf(res) {
  return jsonData(res);
}

// ---------- 결과 저장 ----------

function metricValue(data, key, field) {
  const metric = data.metrics[key];
  return metric && metric.values ? metric.values[field] : undefined;
}

function fmtMs(v) {
  return v === undefined ? '-' : `${Math.round(v)}ms`;
}

function fmtPct(v) {
  return v === undefined ? '-' : `${(v * 100).toFixed(2)}%`;
}

function judge(p95, failRate) {
  if (p95 === undefined) return '데이터 없음';
  return p95 < P95_LIMIT_MS && (failRate || 0) < FAIL_LIMIT ? '통과' : '미달';
}

// 완료 요약 줄과 단계별 표를 만든다 (run.sh가 summary.txt에 모은다)
function buildResultLines(data, scenario) {
  const p95 = metricValue(data, 'http_req_duration{kind:measure}', 'p(95)');
  const fail = metricValue(data, 'http_req_failed{kind:measure}', 'rate');
  const lines = [];
  let table = '';

  if (MODE === 'base') {
    lines.push(`${scenario} base: ${judge(p95, fail)} (p95 ${fmtMs(p95)}, 실패율 ${fmtPct(fail)})`);
    return { lines, table };
  }

  lines.push(`${scenario} stress 전체: ${judge(p95, fail)} (p95 ${fmtMs(p95)}, 실패율 ${fmtPct(fail)})`);
  const rows = ['| VU | 판정 | p95 | 평균 | 실패율 | RPS | 요청 수 |', '|---|---|---|---|---|---|---|'];
  let limitStage = null;
  HOLD_STAGES.forEach(({ index, vus }) => {
    const sp95 = metricValue(data, `http_req_duration{kind:measure,stage:${index}}`, 'p(95)');
    const savg = metricValue(data, `http_req_duration{kind:measure,stage:${index}}`, 'avg');
    const sfail = metricValue(data, `http_req_failed{kind:measure,stage:${index}}`, 'rate');
    const count = metricValue(data, `http_reqs{kind:measure,stage:${index}}`, 'count');
    const rps = count === undefined ? '-' : (count / HOLD_SECONDS).toFixed(1);
    const verdict = judge(sp95, sfail);
    if (verdict === '미달' && limitStage === null) limitStage = vus;
    lines.push(`${scenario} stress ${vus} VU: ${verdict} (p95 ${fmtMs(sp95)}, 평균 ${fmtMs(savg)}, 실패율 ${fmtPct(sfail)}, RPS ${rps})`);
    rows.push(`| ${vus} | ${verdict} | ${fmtMs(sp95)} | ${fmtMs(savg)} | ${fmtPct(sfail)} | ${rps} | ${count === undefined ? '-' : count} |`);
  });
  lines.push(limitStage === null
    ? `${scenario} 한계 단계: 없음 (500 VU까지 통과)`
    : `${scenario} 한계 단계: ${limitStage} VU (기준을 처음 넘은 유지 단계)`);
  table = `${rows.join('\n')}\n\n기준: p95 < ${P95_LIMIT_MS}ms, 실패율 < ${FAIL_LIMIT * 100}% (유지 구간 ${HOLD_SECONDS}초 기준, 상승 구간 제외)\n`;
  return { lines, table };
}

export function makeSummary(owner) {
  return function handleSummary(data) {
    const outDir = __ENV.OUT_DIR || '/results';
    const scenario = __ENV.SCENARIO || 'default';
    const label = `${owner}-${scenario}-${MODE}`;
    const text = textSummary(data, { indent: ' ', enableColors: false });
    const { lines, table } = buildResultLines(data, scenario);
    const files = {
      stdout: `${text}\n${lines.join('\n')}\n${table}`,
      [`${outDir}/${label}.txt`]: text,
      [`${outDir}/${label}.json`]: JSON.stringify(data, null, 2),
      [`${outDir}/${label}.summary.txt`]: `${lines.join('\n')}\n`,
    };
    if (table) files[`${outDir}/${label}.stages.txt`] = table;
    return files;
  };
}
