-- =====================================================================
-- 건수·분포·정합성 확인 (perf/data/03_check.sql)
-- 적재 직후(run.sh load)와 쓰기 측정 후(run.sh measure)에 실행한다.
-- "기대" 열과 "실제" 열이 다르면 결과 문서에 기록한다.
-- 측정 후에는 쓰기 시나리오 때문에 리뷰·댓글·찜 건수가 조금 달라질 수 있다 (정합성 0행은 그대로여야 함).
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 건수
SELECT '영화(활성)'      AS 항목, 2000   AS 기대, (SELECT COUNT(*) FROM movies WHERE is_active = 1) AS 실제
UNION ALL SELECT '회원(perf)',      30000,  (SELECT COUNT(*) FROM members WHERE email LIKE 'perf%@perf.local' AND role = 'USER')
UNION ALL SELECT '관리자 테스트',   1,      (SELECT COUNT(*) FROM members WHERE email = 'perf-admin@perf.local' AND role = 'ADMIN')
UNION ALL SELECT '리뷰',            100000, (SELECT COUNT(*) FROM reviews)
UNION ALL SELECT '리뷰 좋아요',     200000, (SELECT COUNT(*) FROM review_likes)
UNION ALL SELECT '게시글',          50000,  (SELECT COUNT(*) FROM posts)
UNION ALL SELECT '게시글 좋아요',   100000, (SELECT COUNT(*) FROM post_likes)
UNION ALL SELECT '댓글',            100000, (SELECT COUNT(*) FROM comments)
UNION ALL SELECT '댓글 좋아요',     50000,  (SELECT COUNT(*) FROM comment_likes)
UNION ALL SELECT '찜',              100000, (SELECT COUNT(*) FROM wishlists)
UNION ALL SELECT '신고',            5000,   (SELECT COUNT(*) FROM community_reports)
UNION ALL SELECT '알림(생성 안 함)', 0,     (SELECT COUNT(*) FROM notifications);

-- 2. 분포
--    리뷰·찜: popularity 상위 100편 비율 약 50% / 댓글: 상위 1% 게시글(가장 앞 500개) 비율 약 20%
WITH top_movies AS (
    SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 100
), top_posts AS (
    SELECT id FROM posts ORDER BY id ASC LIMIT 500
)
SELECT '리뷰 상위100편 비율(%)' AS 항목, 50 AS 기대,
       ROUND(100 * (SELECT COUNT(*) FROM reviews WHERE movie_id IN (SELECT id FROM top_movies)) / (SELECT COUNT(*) FROM reviews), 1) AS 실제
UNION ALL
SELECT '찜 상위100편 비율(%)', 50,
       ROUND(100 * (SELECT COUNT(*) FROM wishlists WHERE movie_id IN (SELECT id FROM top_movies)) / (SELECT COUNT(*) FROM wishlists), 1)
UNION ALL
SELECT '댓글 상위1%게시글 비율(%)', 20,
       ROUND(100 * (SELECT COUNT(*) FROM comments WHERE post_id IN (SELECT id FROM top_posts)) / (SELECT COUNT(*) FROM comments), 1)
UNION ALL
SELECT '신고 POST/REVIEW/COMMENT', NULL,
       (SELECT CONCAT(SUM(target_type = 'POST'), '/', SUM(target_type = 'REVIEW'), '/', SUM(target_type = 'COMMENT')) FROM community_reports)
UNION ALL
SELECT '신고 PENDING/REJECTED/ACCEPTED', NULL,
       (SELECT CONCAT(SUM(status = 'PENDING'), '/', SUM(status = 'REJECTED'), '/', SUM(status = 'ACCEPTED')) FROM community_reports)
UNION ALL
SELECT 'created_at 범위(최근 180일)', NULL,
       (SELECT CONCAT(MIN(created_at), ' ~ ', MAX(created_at)) FROM reviews);

-- 3. 쓰기 시나리오 예약 확인
SELECT '찜이 없는 VU 계정(perf1~500) 수' AS 항목, 0 AS 기대,
       (SELECT COUNT(*) FROM members m
         WHERE m.email REGEXP '^perf([1-9]|[1-9][0-9]|[1-4][0-9][0-9]|500)@perf\\.local$'
           AND NOT EXISTS (SELECT 1 FROM wishlists w WHERE w.member_id = m.id)) AS 실제
UNION ALL
SELECT '최하위 영화(찜 예약)의 VU 찜 수', 0,
       (SELECT COUNT(*) FROM wishlists w
          JOIN members m ON m.id = w.member_id
         WHERE w.movie_id = (SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC
                             LIMIT 1 OFFSET 1999)
           AND m.email REGEXP '^perf([1-9]|[1-9][0-9]|[1-4][0-9][0-9]|500)@perf\\.local$');

-- 4. 정합성 (모두 0행·0이어야 한다, perf-part1.md 5장)
--    4-1. 영화 평점 합계·리뷰 수 ≠ VISIBLE 리뷰 집계
SELECT '영화 파생 컬럼 불일치 영화 수' AS 항목, 0 AS 기대, COUNT(*) AS 실제
FROM (
    SELECT m.id
    FROM movies m
    LEFT JOIN reviews r ON r.movie_id = m.id AND r.status = 'VISIBLE'
    GROUP BY m.id, m.rating_sum, m.review_count
    HAVING m.review_count <> COUNT(r.id) OR m.rating_sum <> COALESCE(SUM(r.rating), 0)
) bad
UNION ALL
--    4-2. 게시글 댓글 수 ≠ VISIBLE 댓글 수
SELECT '게시글 comment_count 불일치 게시글 수', 0, COUNT(*)
FROM (
    SELECT p.id
    FROM posts p
    LEFT JOIN comments c ON c.post_id = p.id AND c.status = 'VISIBLE'
    GROUP BY p.id, p.comment_count
    HAVING p.comment_count <> COUNT(c.id)
) bad
UNION ALL
--    4-3. 고유키 대상 중복 (DB 고유키가 막지만 확인용)
SELECT '리뷰 (회원,영화) 중복', 0,
       (SELECT COUNT(*) FROM (SELECT 1 FROM reviews GROUP BY member_id, movie_id HAVING COUNT(*) > 1) d)
UNION ALL
SELECT '리뷰 좋아요 (회원,리뷰) 중복', 0,
       (SELECT COUNT(*) FROM (SELECT 1 FROM review_likes GROUP BY member_id, review_id HAVING COUNT(*) > 1) d)
UNION ALL
SELECT '찜 (회원,영화) 중복', 0,
       (SELECT COUNT(*) FROM (SELECT 1 FROM wishlists GROUP BY member_id, movie_id HAVING COUNT(*) > 1) d);

-- 5. 쓰기 측정 흔적 (측정 후 참고용)
SELECT 'VU 계정이 쓴 리뷰(상태별)' AS 항목,
       (SELECT CONCAT('VISIBLE ', SUM(r.status = 'VISIBLE'), ' / DELETED ', SUM(r.status = 'DELETED'))
          FROM reviews r JOIN members m ON m.id = r.member_id
         WHERE m.email REGEXP '^perf([1-9]|[1-9][0-9]|[1-4][0-9][0-9]|500)@perf\\.local$') AS 값
UNION ALL
SELECT '부하 테스트 댓글 수',
       (SELECT COUNT(*) FROM comments WHERE content LIKE 'perf 부하 테스트 댓글%');
