-- =====================================================================
-- 담당별 후보 쿼리 EXPLAIN (perf/explain.sql)
-- run.sh measure <이름>이 "-- @common" 구간과 "-- @<이름>" 구간만 잘라 실행한다.
-- 쿼리는 Hibernate가 만드는 SQL을 손으로 옮긴 것이다 (perf-part1.md 3장). 실제 SQL과 다를 수 있으니
-- 판단이 애매하면 앱을 SQL 로그(debug)로 실행해 실제 쿼리로 다시 확인한다.
-- EXPLAIN ANALYZE는 쿼리를 실제로 실행한다 (주력 쿼리만 사용).
-- =====================================================================

-- @common
SET NAMES utf8mb4;
SET @hot_movie    := (SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 1);
SET @normal_movie := (SELECT id FROM movies WHERE is_active = 1 ORDER BY popularity DESC, id ASC LIMIT 1 OFFSET 999);
SET @vu1          := (SELECT id FROM members WHERE email = 'perf1@perf.local');
SET @hot_post     := (SELECT MIN(id) FROM posts);
SET @today        := CURDATE();

-- @jimin
-- [jimin] 관리자 신고 목록 (주력): 정렬 created_at DESC, id DESC — 받쳐 주는 인덱스 없음
EXPLAIN SELECT * FROM community_reports ORDER BY created_at DESC, id DESC LIMIT 0, 20;
EXPLAIN SELECT * FROM community_reports WHERE status = 'PENDING' ORDER BY created_at DESC, id DESC LIMIT 0, 20;
EXPLAIN SELECT COUNT(*) FROM community_reports WHERE status = 'PENDING';
EXPLAIN ANALYZE SELECT * FROM community_reports WHERE status = 'PENDING' ORDER BY created_at DESC, id DESC LIMIT 0, 20;
-- [jimin] 신고 행마다 대상 조회 (N+1, 행당 1~2회)
EXPLAIN SELECT * FROM posts WHERE id = @hot_post;
EXPLAIN SELECT * FROM members WHERE id = @vu1;
-- [jimin] 내 찜 목록
EXPLAIN SELECT w.*, m.* FROM wishlists w JOIN movies m ON m.id = w.movie_id
         WHERE w.member_id = @vu1 AND m.is_active = 1 ORDER BY w.created_at DESC, w.id DESC LIMIT 0, 20;
EXPLAIN SELECT COUNT(*) FROM wishlists w JOIN movies m ON m.id = w.movie_id WHERE w.member_id = @vu1 AND m.is_active = 1;
-- [jimin] 찜 추가 전 중복 확인
EXPLAIN SELECT 1 FROM wishlists WHERE member_id = @vu1 AND movie_id = @hot_movie LIMIT 1;

-- @woosung
-- [woosung] 전체 리뷰 sort=likes (주력): 리뷰마다 좋아요 COUNT 상관 서브쿼리 후 전체 정렬
EXPLAIN SELECT r.*, u.*, m.* FROM reviews r
          JOIN movies m ON m.id = r.movie_id
          LEFT JOIN members u ON u.id = r.member_id
         WHERE r.status IN ('VISIBLE', 'BLINDED') AND m.is_active = 1
         ORDER BY (SELECT COUNT(*) FROM review_likes rl WHERE rl.review_id = r.id) DESC, r.created_at DESC, r.id DESC
         LIMIT 0, 20;
EXPLAIN SELECT COUNT(*) FROM reviews r JOIN movies m ON m.id = r.movie_id
         WHERE r.status IN ('VISIBLE', 'BLINDED') AND m.is_active = 1;
EXPLAIN ANALYZE SELECT r.id FROM reviews r
          JOIN movies m ON m.id = r.movie_id
         WHERE r.status IN ('VISIBLE', 'BLINDED') AND m.is_active = 1
         ORDER BY (SELECT COUNT(*) FROM review_likes rl WHERE rl.review_id = r.id) DESC, r.created_at DESC, r.id DESC
         LIMIT 0, 20;
-- [woosung] 영화별 리뷰 latest (인기 1위 / 일반 영화)
EXPLAIN SELECT r.*, u.* FROM reviews r LEFT JOIN members u ON u.id = r.member_id
         WHERE r.movie_id = @hot_movie AND r.status IN ('VISIBLE', 'BLINDED')
         ORDER BY r.created_at DESC, r.id DESC LIMIT 0, 20;
EXPLAIN SELECT r.*, u.* FROM reviews r LEFT JOIN members u ON u.id = r.member_id
         WHERE r.movie_id = @normal_movie AND r.status IN ('VISIBLE', 'BLINDED')
         ORDER BY r.created_at DESC, r.id DESC LIMIT 0, 20;
-- [woosung] 페이지의 좋아요 수 (IN 배치)
EXPLAIN SELECT rl.review_id, COUNT(*) FROM review_likes rl
         WHERE rl.review_id IN (SELECT id FROM (SELECT id FROM reviews WHERE movie_id = @hot_movie LIMIT 20) t)
         GROUP BY rl.review_id;
-- [woosung] 리뷰 작성: 영화 행 잠금, (회원, 영화) 리뷰 잠금
EXPLAIN SELECT * FROM movies WHERE id = @hot_movie FOR UPDATE;
EXPLAIN SELECT * FROM reviews WHERE member_id = @vu1 AND movie_id = @hot_movie FOR UPDATE;

-- @gunhee
-- [gunhee] 게시글 popular (주력): 공개 게시글 전체 × 좋아요 집계 후 정렬
EXPLAIN SELECT p.*, COUNT(pl.id) FROM posts p LEFT JOIN post_likes pl ON pl.post_id = p.id
         WHERE p.status = 'VISIBLE' GROUP BY p.id ORDER BY COUNT(pl.id) DESC, p.created_at DESC LIMIT 0, 20;
EXPLAIN SELECT COUNT(*) FROM posts WHERE status = 'VISIBLE';
EXPLAIN ANALYZE SELECT p.id, COUNT(pl.id) FROM posts p LEFT JOIN post_likes pl ON pl.post_id = p.id
         WHERE p.status = 'VISIBLE' GROUP BY p.id ORDER BY COUNT(pl.id) DESC, p.created_at DESC LIMIT 0, 20;
-- [gunhee] 게시글 latest (비교용)
EXPLAIN SELECT * FROM posts WHERE status = 'VISIBLE' ORDER BY created_at DESC, id DESC LIMIT 0, 20;
-- [gunhee] 게시글 상세: 조회, 조회수 UPDATE(행 X락), 좋아요 수
EXPLAIN SELECT * FROM posts WHERE id = @hot_post AND status = 'VISIBLE';
EXPLAIN UPDATE posts SET view_count = view_count + 1 WHERE id = @hot_post;
EXPLAIN SELECT COUNT(*) FROM post_likes WHERE post_id = @hot_post;
-- [gunhee] 댓글 목록
EXPLAIN SELECT * FROM comments WHERE post_id = @hot_post AND status IN ('VISIBLE', 'BLINDED')
         ORDER BY created_at ASC, id ASC LIMIT 0, 20;
-- [gunhee] 댓글 작성: 게시글 comment_count 원자적 증가
EXPLAIN UPDATE posts SET comment_count = comment_count + 1 WHERE id = @hot_post;

-- @rehearsal
-- [rehearsal] 영화 목록 sort=releaseDate (주력): 표현식 정렬
EXPLAIN SELECT * FROM movies
         WHERE is_active = 1 AND COALESCE(release_date_kr, release_date) IS NOT NULL
           AND COALESCE(release_date_kr, release_date) <= @today
         ORDER BY COALESCE(release_date_kr, release_date) DESC, id DESC LIMIT 0, 20;
EXPLAIN ANALYZE SELECT id FROM movies
         WHERE is_active = 1 AND COALESCE(release_date_kr, release_date) IS NOT NULL
           AND COALESCE(release_date_kr, release_date) <= @today
         ORDER BY COALESCE(release_date_kr, release_date) DESC, id DESC LIMIT 0, 20;
-- [rehearsal] 랭킹 (캐시 미스일 때): 집계, 개수, 점수식 정렬
EXPLAIN SELECT SUM(rating_sum), SUM(review_count) FROM movies WHERE is_active = 1 AND review_count > 0;
EXPLAIN SELECT id, (review_count / (review_count + 10)) * (rating_sum / review_count) AS score FROM movies
         WHERE is_active = 1 AND review_count > 0 ORDER BY score DESC, id ASC LIMIT 0, 10;
