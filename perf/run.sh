#!/usr/bin/env bash
# MoviePick 부하 테스트 실행 도우미 (Git Bash용)
#
#   bash perf/run.sh load [--yes]
#       앱을 끈 상태에서 실행. DB를 새로 만들고(down -v → up -d) 더미 데이터를 적재한다.
#       주의: 로컬 DB 데이터(회원·리뷰 등)가 모두 지워진다. --yes를 붙이면 확인 질문을 건너뛴다.
#
#   bash perf/run.sh measure <member|review|community|rehearsal>
#       앱을 측정용 설정(.run/MoviePick-perf.run.xml)으로 실행한 상태에서 실행.
#       기준 측정 전부 → 주력 한계 측정 → EXPLAIN → (쓰기가 있으면) 정합성 확인 → perf/results/<이름>-<시각>/ 저장
#
# 환경변수(선택): APP_URL(기본 http://localhost:8080), K6_BASE_URL(기본 http://host.docker.internal:8080),
#                 K6_IMAGE(기본 grafana/k6:0.54.0), SLEEP(반복 사이 대기 초, 기본 1)

set -uo pipefail

# Git Bash가 docker 인자의 /scripts 같은 경로를 Windows 경로로 바꾸지 않게 한다
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

CONTAINER=moviepick-mysql
APP_URL="${APP_URL:-http://localhost:8080}"
K6_BASE_URL="${K6_BASE_URL:-http://host.docker.internal:8080}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
EXPECTED_MOVIES=2000

step() { printf '\n[perf] ==== %s (%s)\n' "$1" "$(date +%H:%M:%S)"; }
info() { printf '[perf] %s\n' "$1"; }
die()  { printf '\n[perf] 실패: %s\n' "$1" >&2; [ -n "${2:-}" ] && printf '[perf] 조치: %s\n' "$2" >&2; exit 1; }

# docker -v 에 넘길 호스트 경로 (Git Bash면 C:/... 형식)
host_path() { (cd "$1" && (pwd -W 2>/dev/null || pwd)); }

# 컨테이너 안의 환경변수로 접속한다 (비밀번호를 명령에 쓰지 않음, MYSQL_PWD로 경고도 숨김)
mysql_file() {  # $1: SQL 파일, 나머지: mysql 추가 옵션
  local file="$1"; shift
  docker exec -i "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 "$@" "$MYSQL_DATABASE"' \
    mysql "$@" < "$file"
}
mysql_value() {  # $1: SQL 한 줄 → 첫 값
  printf '%s\n' "$1" | docker exec -i "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 -N -B "$MYSQL_DATABASE"' 2>/dev/null | head -n 1 | tr -d '\r'
}

require_docker() {
  docker info >/dev/null 2>&1 || die "Docker에 연결할 수 없습니다." "Docker Desktop을 켠 뒤 다시 실행하세요."
}

container_running() {
  [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = "true" ]
}

# ---------------------------------------------------------------------
# load
# ---------------------------------------------------------------------
cmd_load() {
  local yes="${1:-}"
  require_docker

  step "1/6 사전 확인"
  ls db/seed/*.sql >/dev/null 2>&1 || die "db/seed/ 에 영화 덤프(.sql)가 없습니다." "팀에서 받은 영화 덤프를 db/seed/ 에 넣으세요 (README 참고)."
  info "영화 덤프: $(ls db/seed/*.sql | tr '\n' ' ')"
  if curl -fsS --max-time 2 "$APP_URL/api/auth/csrf" >/dev/null 2>/dev/null; then
    die "앱이 실행 중입니다 ($APP_URL)." "앱을 종료한 뒤 다시 실행하세요 (DB를 새로 만들기 때문)."
  fi
  if [ "$yes" != "--yes" ]; then
    printf '[perf] 로컬 DB 데이터(회원·리뷰·게시글 등)가 모두 지워집니다. 계속할까요? (y/N) '
    read -r answer
    [ "$answer" = "y" ] || [ "$answer" = "Y" ] || die "사용자가 취소했습니다."
  fi

  step "2/6 DB 새로 만들기 (docker compose down -v → up -d)"
  docker compose down -v || die "docker compose down -v 실패" "docker compose ps 로 상태를 확인하세요."
  docker compose up -d || die "docker compose up -d 실패" "docker compose logs mysql 을 확인하세요."

  step "3/6 MySQL 초기화 대기 (영화 덤프 적재 포함, 보통 1~3분)"
  local waited=0
  while :; do
    container_running || die "MySQL 컨테이너가 멈췄습니다 (초기화 SQL 오류 가능)." "docker compose logs mysql 에서 [moviepick-init] 줄과 ERROR를 확인하세요."
    # 초기화용 임시 서버는 port: 0, 초기화가 끝난 뒤 실제 서버만 port: 3306 으로 뜬다
    if docker logs "$CONTAINER" 2>&1 | grep -q "ready for connections.*port: 3306"; then
      [ "$(mysql_value 'SELECT 1')" = "1" ] && break
    fi
    [ "$waited" -ge 600 ] && die "10분이 지나도 MySQL이 준비되지 않았습니다." "docker compose logs mysql 을 확인하세요."
    sleep 5; waited=$((waited + 5))
    [ $((waited % 30)) -eq 0 ] && info "대기 중... ${waited}초"
  done
  local movies
  movies="$(mysql_value 'SELECT COUNT(*) FROM movies WHERE is_active = 1')"
  info "활성 영화 ${movies}편"
  [ "$movies" = "$EXPECTED_MOVIES" ] || die "활성 영화가 ${EXPECTED_MOVIES}편이 아닙니다 (${movies:-조회 실패})." "db/seed/ 에 배치 덤프 하나만 있는지, 초기화 로그에 오류가 없는지 확인하세요."

  step "4/6 더미 데이터 생성 (01_generate.sql, 약 70만 행, 보통 2~6분)"
  local t0=$SECONDS
  mysql_file perf/data/01_generate.sql || die "01_generate.sql 실행 중 오류" "위 ERROR 메시지를 확인하세요. 중간에 멈췄으면 load를 처음부터 다시 실행합니다."
  info "생성 완료 ($((SECONDS - t0))초)"

  step "5/6 파생 컬럼 재계산 (02_recalc.sql, 보통 1분 이내)"
  mysql_file perf/data/02_recalc.sql || die "02_recalc.sql 실행 중 오류" "위 ERROR 메시지를 확인하세요."

  step "6/6 건수·정합성 확인 (03_check.sql)"
  local out="perf/results/load-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$out"
  mysql_file perf/data/03_check.sql -t | tee "$out/check.txt" || die "03_check.sql 실행 중 오류"
  info "확인 결과 저장: $out/check.txt"
  info "적재 끝. 다음: 앱을 측정용 설정(MoviePick-perf)으로 실행 → bash perf/run.sh measure <이름>"
}

# ---------------------------------------------------------------------
# measure
# ---------------------------------------------------------------------
scenarios_of() {
  case "$1" in
    member)    echo "adminReports myWishlist wishlistWrite" ;;
    review)    echo "allReviewsLikes movieReviews reviewWrite" ;;
    community) echo "postsPopular postDetailHot postDetailSpread commentWrite" ;;
    rehearsal) echo "moviesRecent ranking" ;;
  esac
}
main_of() {
  case "$1" in
    member) echo adminReports ;; review) echo allReviewsLikes ;; community) echo postsPopular ;; rehearsal) echo moviesRecent ;;
  esac
}
has_write() { [ "$1" != "rehearsal" ]; }

run_k6() {  # $1 이름, $2 시나리오, $3 base|stress, $4 결과 폴더
  local name="$1" scenario="$2" mode="$3" out="$4"
  step "k6: ${name} / ${scenario} / ${mode} (base 약 2분, stress 약 7분 + 로그인 준비)"
  docker run --rm \
    -v "$(host_path perf/k6):/scripts" \
    -v "$(host_path "$out"):/results" \
    -e BASE_URL="$K6_BASE_URL" -e MODE="$mode" -e SCENARIO="$scenario" -e SLEEP="${SLEEP:-1}" \
    -e OUT_DIR=/results \
    -e HOT_MOVIE_ID="$HOT_MOVIE_ID" -e NORMAL_MOVIE_ID="$NORMAL_MOVIE_ID" -e WISH_MOVIE_ID="$WISH_MOVIE_ID" \
    -e HOT_POST_ID="$HOT_POST_ID" -e POST_MIN="$POST_MIN" -e POST_MAX="$POST_MAX" \
    "$K6_IMAGE" run --quiet "/scripts/${name}.js" 2>&1 | tee "$out/k6-${scenario}-${mode}.log"
  local code=${PIPESTATUS[0]}
  LAST_K6_CODE=$code
  # handleSummary가 만든 숫자 요약(<담당>-<시나리오>-<모드>.summary.txt)을 summary.txt에 모은다
  local result_file="$out/${name}-${scenario}-${mode}.summary.txt"
  case "$code" in
    0)  info "통과 (임계값 충족)" ;;
    99) info "임계값 미달 (p95 500ms 또는 실패율 1% 초과) — 결과는 저장됨" ;;
    *)  info "k6 오류 종료 (코드 ${code}) — 로그: $out/k6-${scenario}-${mode}.log"; K6_ERROR=1 ;;
  esac
  if [ -s "$result_file" ]; then
    tr -d '\r' < "$result_file" >> "$out/summary.txt"
  else
    echo "${scenario} ${mode}: k6 오류(코드 ${code}), 숫자 요약 없음 — k6-${scenario}-${mode}.log 확인" >> "$out/summary.txt"
  fi
}

cmd_measure() {
  local name="${1:-}"
  [ -n "$(scenarios_of "$name")" ] || die "측정 이름이 필요합니다: member | review | community | rehearsal" "예: bash perf/run.sh measure rehearsal"
  require_docker

  step "1/6 사전 확인"
  container_running || die "MySQL 컨테이너가 실행 중이 아닙니다." "docker compose up -d 후 다시 실행하세요."
  curl -fsS --max-time 5 "$APP_URL/api/auth/csrf" >/dev/null \
    || die "앱이 응답하지 않습니다 ($APP_URL)." "IntelliJ에서 MoviePick-perf 실행 설정으로 앱을 켠 뒤 다시 실행하세요."
  [ "$(mysql_value "SELECT COUNT(*) FROM members WHERE email = 'perf1@perf.local'")" = "1" ] \
    || die "더미 데이터가 없습니다." "bash perf/run.sh load 를 먼저 실행하세요."

  step "2/6 측정 대상 id 조회 (DB)"
  local cnt
  cnt="$(mysql_value 'SELECT COUNT(*) FROM movies WHERE is_active = 1')"
  HOT_MOVIE_ID="$(mysql_value 'SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 1')"
  NORMAL_MOVIE_ID="$(mysql_value 'SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 1 OFFSET 999')"
  WISH_MOVIE_ID="$(mysql_value "SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 1 OFFSET $((cnt - 1))")"
  HOT_POST_ID="$(mysql_value 'SELECT MIN(id) FROM posts')"
  POST_MIN="$HOT_POST_ID"
  POST_MAX="$(mysql_value 'SELECT MAX(id) FROM posts')"
  for v in HOT_MOVIE_ID NORMAL_MOVIE_ID WISH_MOVIE_ID HOT_POST_ID POST_MAX; do
    [ -n "${!v}" ] || die "${v} 조회 실패" "bash perf/run.sh load 결과를 확인하세요."
  done
  info "HOT_MOVIE_ID=$HOT_MOVIE_ID NORMAL_MOVIE_ID=$NORMAL_MOVIE_ID WISH_MOVIE_ID=$WISH_MOVIE_ID HOT_POST_ID=$HOT_POST_ID POST=${POST_MIN}~${POST_MAX}"

  local out="perf/results/${name}-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$out"
  {
    echo "측정: $name"
    echo "시각: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "커밋: $(git rev-parse --short HEAD 2>/dev/null || echo '-')"
    echo "서버: $K6_BASE_URL / k6: $K6_IMAGE / SLEEP=${SLEEP:-1}"
    echo "대상 id: HOT_MOVIE_ID=$HOT_MOVIE_ID NORMAL_MOVIE_ID=$NORMAL_MOVIE_ID WISH_MOVIE_ID=$WISH_MOVIE_ID HOT_POST_ID=$HOT_POST_ID POST=${POST_MIN}~${POST_MAX}"
  } > "$out/meta.txt"
  : > "$out/summary.txt"
  K6_ERROR=0

  step "3/6 기준 측정 (50 VU, 시나리오마다 약 2분)"
  local main main_base_code=0
  main="$(main_of "$name")"
  for s in $(scenarios_of "$name"); do
    run_k6 "$name" "$s" base "$out"
    [ "$s" = "$main" ] && main_base_code=$LAST_K6_CODE
  done

  if [ "$main_base_code" -eq 99 ]; then
    step "4/6 한계 측정 생략 (주력: ${main})"
    info "기준 측정에서 이미 기준 초과 → 한계 측정 생략"
    echo "${main} stress: 기준 측정에서 이미 기준 초과 → 한계 측정 생략" >> "$out/summary.txt"
  else
    step "4/6 한계 측정 (주력 1개: ${main}, 50→100→200→500, 단계별 결과는 ${name}-${main}-stress.stages.txt)"
    info "기준을 크게 넘는 API는 타임아웃 경고(dial: i/o timeout, request timeout)가 반복될 수 있으며 오류가 아님, 중단하지 말 것"
    run_k6 "$name" "$main" stress "$out"
  fi

  step "5/6 EXPLAIN (perf/explain.sql 의 @common + @${name})"
  awk -v want="@${name}" '
    /^-- @/ { section = $2; next }
    section == "@common" || section == want { print }
  ' perf/explain.sql > "$out/explain-input.sql"
  mysql_file "$out/explain-input.sql" -t > "$out/explain.txt" 2>&1 \
    && info "저장: $out/explain.txt" || info "EXPLAIN 일부 실패 — $out/explain.txt 확인"

  if has_write "$name"; then
    step "6/6 쓰기 후 정합성 확인 (03_check.sql)"
    mysql_file perf/data/03_check.sql -t > "$out/check.txt" 2>&1 \
      && info "저장: $out/check.txt" || info "정합성 확인 실패 — $out/check.txt 확인"
  else
    step "6/6 정합성 확인 생략 (쓰기 시나리오 없음)"
  fi

  step "완료: $out"
  cat "$out/summary.txt"
  info "기록표: perf/results/_template.md 를 복사해 $out/ 에 작성하세요."
  [ "$K6_ERROR" -eq 0 ] || die "k6 오류로 끝난 측정이 있습니다." "$out/k6-*.log 를 확인하세요."
}

case "${1:-}" in
  load)    cmd_load "${2:-}" ;;
  measure) cmd_measure "${2:-}" ;;
  *)
    echo "사용법:"
    echo "  bash perf/run.sh load [--yes]                                  (앱 종료 상태) DB 재생성 + 더미 적재"
    echo "  bash perf/run.sh measure <member|review|community|rehearsal>      (앱 실행 상태) 측정 + EXPLAIN + 확인"
    exit 1
    ;;
esac
