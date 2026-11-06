# 부하 테스트 기록표 (복사해서 perf/results/<이름>-<시각>/record.md 로 작성)

- 담당:
- 측정 시각 / 커밋: (meta.txt)
- 환경: 로컬 PC (CPU·메모리), Docker MySQL 8.0.46 / Redis 7.2, 앱 실행 설정 MoviePick-perf (Hikari 10, Tomcat 200 기본값)
- 데이터 규모: 회원 30,000 / 리뷰 100,000 / 리뷰 좋아요 200,000 / 게시글 50,000 / 게시글 좋아요 100,000 / 댓글 100,000 / 댓글 좋아요 50,000 / 찜 100,000 / 신고 5,000 / 영화 2,000
- 통과 기준: p95 < 500ms, 실패율 < 1% (kind=measure 요청만)

## 1. 결과
| 시나리오 | 모드 | 최대 VU | p95 (ms) | 평균 (ms) | RPS | 실패율 | 통과 |
|---|---|---|---|---|---|---|---|
| | base | 50 | | | | | |
| | stress | 500 | | | | | |

- 값 위치: `k6-<시나리오>-<모드>.log` 또는 `<담당>-<시나리오>-<모드>.txt`
  - p95·평균: `http_req_duration{kind:measure}`
  - 실패율: `http_req_failed{kind:measure}`
  - RPS: `http_reqs`
- 한계 측정: 기준을 처음 넘은 단계(VU)와 그때의 p95·실패율

## 2. EXPLAIN 요약 (explain.txt)
| 쿼리 | type / key | rows | Extra (filesort, temporary 등) | ANALYZE 실제 시간 |
|---|---|---|---|---|
| | | | | |

## 3. 정합성 (check.txt, 쓰기 시나리오만)
| 항목 | 기대 | 실제 |
|---|---|---|
| 영화 파생 컬럼 불일치 | 0 | |
| 게시글 comment_count 불일치 | 0 | |

## 4. 판단
- 통과 / 미달:
- 원인 추정 (쿼리·인덱스·N+1·락·커넥션 풀·캐시 등, 근거 파일:줄 또는 EXPLAIN):
- 개선 아이디어 (우선순위 순):
  1.
  2.
