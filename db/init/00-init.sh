#!/bin/bash
# Docker MySQL 컨테이너가 처음 만들어질 때(데이터 볼륨이 비어 있을 때) 한 번만 실행된다.
# 순서: /db/schema.sql → /db/batch-schema.sql → /db/data.sql → /db/seed/*.sql (seed 폴더에 파일이 있을 때만)
# 다시 실행하려면: docker compose down -v → docker compose up -d
#
# MySQL 이미지는 실행 권한이 없는 .sh를 자기 셸에 포함(source)해서 돌리므로,
# 설정이 새지 않게 전체를 서브셸 ( ) 안에서 실행한다. 실패하면 초기화가 멈춘다.
(
  set -euo pipefail

  run_sql() {
    echo "[moviepick-init] 실행: $1"
    mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" \
      --default-character-set=utf8mb4 "${MYSQL_DATABASE}" < "$1"
  }

  for file in \
      /db/schema.sql \
      /db/ai-credit-wallet-schema.sql \
      /db/ai-credit-transactions-schema.sql \
      /db/ai-recommendation-feedback-schema.sql \
      /db/batch-schema.sql \
      /db/data.sql; do
    if [ -f "$file" ]; then
      run_sql "$file"
    else
      echo "[moviepick-init] 없음, 건너뜀: $file"
    fi
  done

  shopt -s nullglob
  seed_files=(/db/seed/*.sql)
  if [ ${#seed_files[@]} -eq 0 ]; then
    echo "[moviepick-init] db/seed 폴더에 .sql 파일이 없어 영화 데이터 없이 시작합니다."
  fi
  for file in "${seed_files[@]}"; do
    run_sql "$file"
  done
)
