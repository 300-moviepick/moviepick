-- =====================================================================
-- MoviePick 부하 테스트 더미 데이터 생성 (perf/data/01_generate.sql)
--
-- 실행: bash perf/run.sh load  (DB를 새로 만든 직후, 앱이 꺼진 상태에서 1회)
-- 전제: 새 볼륨 (schema.sql → … → data.sql(관리자) → db/seed 영화 덤프 2,000편 적재 완료)
--
-- 규칙
-- - RAND()를 쓰지 않는다. 모든 값은 번호 n의 산술식으로 만든다 → 누가 적재해도 같은 데이터.
-- - created_at은 고정 기준시각 @base에서 과거로 분산 (콘텐츠: 최근 170일, 회원: 170~180일 전).
-- - 영화 id를 가정하지 않는다. popularity 순위(rk)로 매핑한다 (perf_movie_rank).
-- - 회원·리뷰·게시글·댓글도 번호(n) → id 매핑 테이블을 거친다 (data.sql 관리자가 members.id 1번).
-- - 이미 적재한 DB에서 다시 실행하면 members 고유키(email, nickname) 위반으로 멈춘다 (중복 적재 방지).
--
-- 규모: 회원 30,000(+관리자 테스트 1) / 리뷰 100,000 / 리뷰 좋아요 200,000 / 게시글 50,000
--       게시글 좋아요 100,000 / 댓글 100,000 / 댓글 좋아요 50,000 / 찜 100,000 / 신고 5,000
--
-- 예약 (쓰기 시나리오가 409 없이 반복되도록)
-- - 리뷰는 회원 501~30,000만 작성 → VU 계정 perf1~perf500은 리뷰가 없다 (review: 인기 1위 영화 리뷰 작성→삭제)
-- - 찜은 popularity 최하위(rk = 영화 수) 1편을 쓰지 않는다 (member: 그 영화 찜 추가→삭제)
-- - VU 계정 perf1~perf500은 찜이 3개 이상 있다 (member: 내 찜 목록)
--
-- 테스트 계정 (로컬 더미 전용)
-- - 회원: perf{n}@perf.local / 닉네임 perf{n} / 비밀번호 password  (n = 1~30000, VU용 1~500)
-- - 관리자: perf-admin@perf.local / 닉네임 perf_admin / 비밀번호 password
-- - 해시: Spring Security 문서 예제의 "password" BCrypt(강도 10), 접두사 없이 data.sql과 같은 $2a$10$ 형식
--   로그인이 안 되면 아래 @pw 한 줄만 다른 해시로 바꿔 다시 적재한다 (perf-part2.md 3장)
-- =====================================================================

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 300000;

SET @base := TIMESTAMP('2026-11-01 00:00:00');
SET @pw   := '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG';
SET @span := 14688000;   -- 콘텐츠 created_at 분산 범위: 170일(초)

-- ---------------------------------------------------------------------
-- 0. 보조 테이블 (02_recalc.sql 마지막에 삭제)
--    TEMPORARY 테이블은 한 쿼리에서 두 번 참조할 수 없어 일반 테이블로 만든다.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS perf_seq, perf_movie_rank, perf_member, perf_review, perf_post, perf_comment;

CREATE TABLE perf_seq (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO perf_seq (n)
WITH RECURSIVE s (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM s WHERE n < 200000
)
SELECT n FROM s;

-- 활성 영화 popularity 순위 (NULL은 맨 뒤, 같은 값은 id 오름차순)
CREATE TABLE perf_movie_rank (
    rk INT NOT NULL PRIMARY KEY,
    movie_id BIGINT NOT NULL
) ENGINE=InnoDB;
INSERT INTO perf_movie_rank (rk, movie_id)
SELECT ROW_NUMBER() OVER (ORDER BY popularity DESC, id ASC), id
FROM movies
WHERE is_active = 1;

SET @mc := (SELECT COUNT(*) FROM perf_movie_rank);   -- 기대값 2000
SET @g1 := @mc - 100;                                -- 리뷰 일반 그룹 영화 수 (rk 101 ~ @mc)
SET @g2 := @mc - 101;                                -- 찜 일반 그룹 영화 수 (rk 101 ~ @mc-1, 최하위 1편 예약)

-- ---------------------------------------------------------------------
-- 1. 회원 30,000 + 관리자 테스트 계정 1
-- ---------------------------------------------------------------------
INSERT INTO members (provider, provider_id, email, password_hash, nickname, role, status,
                     suspended_until, withdrawn_at, created_at, updated_at)
SELECT 'LOCAL', NULL,
       CONCAT('perf', n, '@perf.local'), @pw, CONCAT('perf', n),
       'USER', 'ACTIVE', NULL, NULL,
       @base - INTERVAL (@span + (n * 97) % 864000) SECOND,
       @base - INTERVAL (@span + (n * 97) % 864000) SECOND
FROM perf_seq
WHERE n <= 30000
ORDER BY n;

INSERT INTO members (provider, provider_id, email, password_hash, nickname, role, status,
                     suspended_until, withdrawn_at, created_at, updated_at)
VALUES ('LOCAL', NULL, 'perf-admin@perf.local', @pw, 'perf_admin', 'ADMIN', 'ACTIVE',
        NULL, NULL, @base - INTERVAL 180 DAY, @base - INTERVAL 180 DAY);

CREATE TABLE perf_member (
    n INT NOT NULL PRIMARY KEY,
    id BIGINT NOT NULL,
    KEY idx_perf_member_id (id)
) ENGINE=InnoDB;
INSERT INTO perf_member (n, id)
SELECT CAST(SUBSTRING(nickname, 5) AS UNSIGNED), id
FROM members
WHERE role = 'USER' AND email LIKE 'perf%@perf.local';

-- ---------------------------------------------------------------------
-- 2. 리뷰 100,000 (회원 501~30,000만 작성, 회원·영화당 1개)
--    a) n 1~50,000      : 상위 100편(rk 1~100)에 영화당 500개
--         k = (n-1)%100+1, j = (n-1) DIV 100 (0~499), 회원 = 501 + (j*59 + k*7) % 29500
--         → 같은 영화(k)에서 j*59 (최대 29,441 < 29,500)가 모두 달라 회원이 겹치지 않는다
--    b) n 50,001~100,000: 나머지(rk 101~@mc)에 고르게
--         i = n-50001, rk = 101 + i % @g1, s = i DIV @g1 (0~26), 회원 = 501 + (s*1103 + rk*13) % 29500
--         → 같은 영화에서 s*1103 (최대 28,678 < 29,500)이 모두 달라 회원이 겹치지 않는다
--    두 그룹은 영화(rk)가 달라 서로 겹치지 않는다.
-- ---------------------------------------------------------------------
INSERT INTO reviews (member_id, movie_id, rating, content, spoiler, status, rewritten_at, created_at, updated_at)
SELECT pm.id, mr.movie_id,
       0.5 * (1 + (x.n * 7) % 10),
       CONCAT('perf 리뷰 ', x.n, ' - 부하 테스트용 리뷰 내용입니다.'),
       IF(x.n % 10 = 0, 1, 0),
       'VISIBLE', NULL,
       @base - INTERVAL ((x.n * 131) % @span) SECOND,
       @base - INTERVAL ((x.n * 131) % @span) SECOND
FROM (
    SELECT n,
           CASE WHEN n <= 50000 THEN (n - 1) % 100 + 1
                ELSE 101 + (n - 50001) % @g1 END AS rk,
           CASE WHEN n <= 50000 THEN 501 + (((n - 1) DIV 100) * 59 + ((n - 1) % 100 + 1) * 7) % 29500
                ELSE 501 + (((n - 50001) DIV @g1) * 1103 + (101 + (n - 50001) % @g1) * 13) % 29500 END AS member_no
    FROM perf_seq
    WHERE n <= 100000
) x
JOIN perf_movie_rank mr ON mr.rk = x.rk
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

CREATE TABLE perf_review (rn INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO perf_review (rn, id)
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM reviews;

-- ---------------------------------------------------------------------
-- 3. 리뷰 좋아요 200,000 (리뷰당 2개, 균등)
--    r = (n-1)%100000+1, t = (n-1) DIV 100000 (0~1), 회원 = 1 + (r*31 + t*15001) % 30000
--    → 같은 리뷰에서 t가 다르면 15,001 차이라 회원이 겹치지 않는다
-- ---------------------------------------------------------------------
INSERT INTO review_likes (member_id, review_id, created_at, updated_at)
SELECT pm.id, pr.id,
       @base - INTERVAL ((x.n * 173) % @span) SECOND,
       @base - INTERVAL ((x.n * 173) % @span) SECOND
FROM (
    SELECT n,
           (n - 1) % 100000 + 1 AS r,
           1 + (((n - 1) % 100000 + 1) * 31 + ((n - 1) DIV 100000) * 15001) % 30000 AS member_no
    FROM perf_seq
    WHERE n <= 200000
) x
JOIN perf_review pr ON pr.rn = x.r
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

-- ---------------------------------------------------------------------
-- 4. 게시글 50,000 (모두 VISIBLE, comment_count는 02_recalc에서 계산)
-- ---------------------------------------------------------------------
INSERT INTO posts (member_id, title, content, view_count, comment_count, status, created_at, updated_at)
SELECT pm.id,
       CONCAT('perf 게시글 ', s.n),
       CONCAT('perf 게시글 본문 ', s.n, ' ', REPEAT('영화 이야기를 나눠요. ', 1 + s.n % 20)),
       (s.n * 37) % 5000,
       0,
       'VISIBLE',
       @base - INTERVAL ((s.n * 211) % @span) SECOND,
       @base - INTERVAL ((s.n * 211) % @span) SECOND
FROM perf_seq s
JOIN perf_member pm ON pm.n = 1 + (s.n * 7919) % 30000
WHERE s.n <= 50000
ORDER BY s.n;

CREATE TABLE perf_post (rn INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO perf_post (rn, id)
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM posts;

-- ---------------------------------------------------------------------
-- 5. 게시글 좋아요 100,000
--    a) n 1~20,000 (20%)    : 상위 1% 게시글(rn 1~500)에 게시글당 40개
--         p = (n-1)%500+1, t = (n-1) DIV 500 (0~39), 회원 = 1 + (p*13 + t*743) % 30000 (t*743 최대 28,977)
--    b) n 20,001~100,000    : 나머지(rn 501~50,000)
--         i = n-20001, p = 501 + i%49500, t = i DIV 49500 (0~1), 회원 = 1 + (p*17 + t*15013) % 30000
-- ---------------------------------------------------------------------
INSERT INTO post_likes (member_id, post_id, created_at, updated_at)
SELECT pm.id, pp.id,
       @base - INTERVAL ((x.n * 223) % @span) SECOND,
       @base - INTERVAL ((x.n * 223) % @span) SECOND
FROM (
    SELECT n,
           CASE WHEN n <= 20000 THEN (n - 1) % 500 + 1
                ELSE 501 + (n - 20001) % 49500 END AS p,
           CASE WHEN n <= 20000 THEN 1 + (((n - 1) % 500 + 1) * 13 + ((n - 1) DIV 500) * 743) % 30000
                ELSE 1 + ((501 + (n - 20001) % 49500) * 17 + ((n - 20001) DIV 49500) * 15013) % 30000 END AS member_no
    FROM perf_seq
    WHERE n <= 100000
) x
JOIN perf_post pp ON pp.rn = x.p
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

-- ---------------------------------------------------------------------
-- 6. 댓글 100,000 (모두 VISIBLE)
--    a) n 1~20,000 (20%) : 상위 1% 게시글(rn 1~500)에 게시글당 40개
--    b) 나머지           : rn 501~50,000에 고르게
-- ---------------------------------------------------------------------
INSERT INTO comments (member_id, post_id, content, status, created_at, updated_at)
SELECT pm.id, pp.id,
       CONCAT('perf 댓글 ', x.n, ' - 부하 테스트용 댓글입니다.'),
       'VISIBLE',
       @base - INTERVAL ((x.n * 239) % @span) SECOND,
       @base - INTERVAL ((x.n * 239) % @span) SECOND
FROM (
    SELECT n,
           CASE WHEN n <= 20000 THEN (n - 1) % 500 + 1
                ELSE 501 + (n - 20001) % 49500 END AS p,
           1 + (n * 4093) % 30000 AS member_no
    FROM perf_seq
    WHERE n <= 100000
) x
JOIN perf_post pp ON pp.rn = x.p
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

CREATE TABLE perf_comment (rn INT NOT NULL PRIMARY KEY, id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO perf_comment (rn, id)
SELECT ROW_NUMBER() OVER (ORDER BY id), id FROM comments;

-- ---------------------------------------------------------------------
-- 7. 댓글 좋아요 50,000 (댓글 하나 건너 하나에 1개씩, 균등)
--    c = 1 + ((n-1)*2) % 100000, 회원 = 1 + (c*29) % 30000  → 댓글당 1개라 고유키 충돌 없음
-- ---------------------------------------------------------------------
INSERT INTO comment_likes (comment_id, member_id, created_at, updated_at)
SELECT pc.id, pm.id,
       @base - INTERVAL ((x.n * 251) % @span) SECOND,
       @base - INTERVAL ((x.n * 251) % @span) SECOND
FROM (
    SELECT n,
           1 + ((n - 1) * 2) % 100000 AS c,
           1 + ((1 + ((n - 1) * 2) % 100000) * 29) % 30000 AS member_no
    FROM perf_seq
    WHERE n <= 50000
) x
JOIN perf_comment pc ON pc.rn = x.c
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

-- ---------------------------------------------------------------------
-- 8. 찜 100,000 (최하위 영화 rk = @mc 는 쓰지 않음)
--    a) n 1~50,000        : 상위 100편에 영화당 500개
--         k = (n-1)%100+1, j = (n-1) DIV 100 (0~499), 회원 = 1 + (j*59 + k*11) % 30000
--    b) n 50,001~51,500   : VU 계정 perf1~perf500에 3개씩 (rk 101~@mc-1)
--         i = n-50001, v = i DIV 3 + 1, rk = 101 + (v*7 + (i%3)*600) % @g2  → 한 회원의 3편이 서로 다름
--    c) n 51,501~100,000  : 회원 501~30,000, rk 101~@mc-1
--         i = n-51501, rk = 101 + i % @g2, s = i DIV @g2 (0~25), 회원 = 501 + (s*1103 + rk*13) % 29500
--    a는 rk 1~100, b·c는 rk 101 이상이라 서로 겹치지 않고, b(회원 1~500)와 c(회원 501~)도 겹치지 않는다.
-- ---------------------------------------------------------------------
INSERT INTO wishlists (member_id, movie_id, created_at, updated_at)
SELECT pm.id, mr.movie_id,
       @base - INTERVAL ((x.n * 263) % @span) SECOND,
       @base - INTERVAL ((x.n * 263) % @span) SECOND
FROM (
    SELECT n,
           CASE WHEN n <= 50000 THEN (n - 1) % 100 + 1
                WHEN n <= 51500 THEN 101 + ((((n - 50001) DIV 3) + 1) * 7 + ((n - 50001) % 3) * 600) % @g2
                ELSE 101 + (n - 51501) % @g2 END AS rk,
           CASE WHEN n <= 50000 THEN 1 + (((n - 1) DIV 100) * 59 + ((n - 1) % 100 + 1) * 11) % 30000
                WHEN n <= 51500 THEN ((n - 50001) DIV 3) + 1
                ELSE 501 + (((n - 51501) DIV @g2) * 1103 + (101 + (n - 51501) % @g2) * 13) % 29500 END AS member_no
    FROM perf_seq
    WHERE n <= 100000
) x
JOIN perf_movie_rank mr ON mr.rk = x.rk
JOIN perf_member pm ON pm.n = x.member_no
ORDER BY x.n;

-- ---------------------------------------------------------------------
-- 9. 신고 5,000 (대상마다 서로 다른 대상 → uk_report_reporter_target 충돌 없음)
--    POST 2,000 (n 1~2,000)       : 게시글 rn = 1 + ((n-1)*23) % 50000
--    REVIEW 2,000 (n 2,001~4,000) : 리뷰 rn = 1 + ((n-2001)*47) % 100000
--    COMMENT 1,000 (n 4,001~5,000): 댓글 rn = 1 + ((n-4001)*97) % 100000
--    신고자 = 대상 작성자 번호 % 30000 + 1 (작성자와 항상 다름)
--    상태: n%5 < 3 → PENDING(60%), 그 외 REJECTED(40%). ACCEPTED 없음
--    사유: SPAM·ABUSE·SPOILER·ADULT·ETC 순환, ETC면 detail 필수
-- ---------------------------------------------------------------------
INSERT INTO community_reports (reporter_id, target_type, target_id, reason, detail, status, created_at, updated_at)
SELECT rep.id, x.target_type, x.target_id,
       ELT(1 + x.n % 5, 'SPAM', 'ABUSE', 'SPOILER', 'ADULT', 'ETC'),
       IF(x.n % 5 = 4, 'perf 기타 사유', NULL),
       IF(x.n % 5 < 3, 'PENDING', 'REJECTED'),
       @base - INTERVAL ((x.n * 277) % @span) SECOND,
       @base - INTERVAL ((x.n * 277) % @span) SECOND
FROM (
    SELECT s.n, 'POST' AS target_type, p.id AS target_id, p.member_id AS author_id
    FROM perf_seq s
    JOIN perf_post pp ON pp.rn = 1 + ((s.n - 1) * 23) % 50000
    JOIN posts p ON p.id = pp.id
    WHERE s.n <= 2000
    UNION ALL
    SELECT s.n, 'REVIEW', r.id, r.member_id
    FROM perf_seq s
    JOIN perf_review pr ON pr.rn = 1 + ((s.n - 2001) * 47) % 100000
    JOIN reviews r ON r.id = pr.id
    WHERE s.n BETWEEN 2001 AND 4000
    UNION ALL
    SELECT s.n, 'COMMENT', c.id, c.member_id
    FROM perf_seq s
    JOIN perf_comment pc ON pc.rn = 1 + ((s.n - 4001) * 97) % 100000
    JOIN comments c ON c.id = pc.id
    WHERE s.n BETWEEN 4001 AND 5000
) x
JOIN perf_member au ON au.id = x.author_id
JOIN perf_member rep ON rep.n = au.n % 30000 + 1
ORDER BY x.n;

-- 생성 단계 끝. 파생 컬럼 재계산과 보조 테이블 삭제는 02_recalc.sql
SELECT 'perf 01_generate 완료' AS step;
