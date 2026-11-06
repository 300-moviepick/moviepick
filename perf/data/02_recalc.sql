-- =====================================================================
-- 파생 컬럼 재계산 + 보조 테이블 삭제 (perf/data/02_recalc.sql)
-- 01_generate.sql 다음에 실행. 다시 실행해도 결과가 같다 (현재 원천 데이터 기준으로 맞춘다).
--
-- 파생 컬럼과 원천 (perf-part1.md 7.3)
-- - movies.rating_sum / review_count : 그 영화의 VISIBLE 리뷰 SUM(rating) / COUNT(*)
-- - posts.comment_count              : 그 게시글의 VISIBLE 댓글 COUNT(*)
-- - 좋아요 수는 저장 컬럼이 없다 (요청마다 COUNT 집계)
-- - posts.view_count 는 원천이 없어 01에서 넣은 값을 그대로 둔다
-- =====================================================================

SET NAMES utf8mb4;

-- 영화 평점 합계·리뷰 수 (리뷰가 없는 영화는 0)
UPDATE movies m
LEFT JOIN (
    SELECT movie_id, SUM(rating) AS rating_sum, COUNT(*) AS review_count
    FROM reviews
    WHERE status = 'VISIBLE'
    GROUP BY movie_id
) x ON x.movie_id = m.id
SET m.rating_sum = COALESCE(x.rating_sum, 0),
    m.review_count = COALESCE(x.review_count, 0);

-- 게시글 댓글 수
UPDATE posts p
LEFT JOIN (
    SELECT post_id, COUNT(*) AS comment_count
    FROM comments
    WHERE status = 'VISIBLE'
    GROUP BY post_id
) x ON x.post_id = p.id
SET p.comment_count = COALESCE(x.comment_count, 0);

-- 보조 테이블 삭제 (엔티티와 무관한 테이블이라 남아 있어도 앱 기동에는 영향 없음)
DROP TABLE IF EXISTS perf_seq, perf_movie_rank, perf_member, perf_review, perf_post, perf_comment;

-- 통계 갱신 (EXPLAIN이 적재 직후 상태를 반영하도록)
ANALYZE TABLE members, movies, reviews, review_likes, posts, post_likes, comments, comment_likes,
              wishlists, community_reports;

SELECT 'perf 02_recalc 완료' AS step;
