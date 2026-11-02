-- moviepick 기준 DB 구조 (MySQL 8.0)
-- 엔티티를 바꾸면 같은 PR에서 이 파일도 고친다. 앱은 ddl-auto: validate로 이 구조와 엔티티가 같은지만 확인한다.
-- 이 파일은 Docker MySQL 컨테이너가 처음 만들어질 때 db/init/00-init.sh가 실행한다.
-- 바뀐 구조를 반영하려면: docker compose down -v → docker compose up -d
-- 제약 이름 uk_members_email_provider, uk_post_like_member, uk_comment_like_member,
-- uk_review_likes_member_id_review_id, uk_report_reporter_target, uk_members_nickname,
-- uk_wishlists_member_id_movie_id 는 코드가 중복 오류를 판별할 때 쓰므로 이름을 바꾸지 않는다.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `admin_sanctions` (
  `sanction_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `suspended_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1r4krkxc6tou4k6f378irdxaa` (`report_id`),
  KEY `FKd1phx02yvde0o3cuo8u3s7ljg` (`member_id`),
  CONSTRAINT `FKd1phx02yvde0o3cuo8u3s7ljg` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `FKil4rvajkyqual4v2bcprxh73` FOREIGN KEY (`report_id`) REFERENCES `community_reports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `comment` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content` text NOT NULL,
  `status` enum('BLINDED','DELETED','VISIBLE') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKocwtsgphsvfiqmipwu0qqj84l` (`member_id`),
  KEY `FKs1slvnkuemjsq2kj4h3vhx7i1` (`post_id`),
  KEY `idx_comment_post_id_status_created_at` (`post_id`,`status`,`created_at`),
  CONSTRAINT `FKocwtsgphsvfiqmipwu0qqj84l` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `FKs1slvnkuemjsq2kj4h3vhx7i1` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `comment_likes` (
  `comment_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_like_member` (`comment_id`,`member_id`),
  KEY `FKq6gltbxc24mdpub8fpe4ms8wf` (`member_id`),
  CONSTRAINT `FKd0epu3dcjc57pwe7lt5jgfqsi` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`),
  CONSTRAINT `FKq6gltbxc24mdpub8fpe4ms8wf` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_reports` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint NOT NULL,
  `target_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `reason` varchar(20) NOT NULL,
  `detail` varchar(200) DEFAULT NULL,
  `status` enum('ACCEPTED','PENDING','REJECTED') NOT NULL,
  `target_type` enum('COMMENT','POST','REVIEW') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_reporter_target` (`reporter_id`,`target_type`,`target_id`),
  KEY `FK12s2daju3wxyu5g9baq6ij9od` (`reporter_id`),
  KEY `idx_report_target_status` (`target_type`,`target_id`,`status`),
  CONSTRAINT `FK12s2daju3wxyu5g9baq6ij9od` FOREIGN KEY (`reporter_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `members` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `suspended_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `nickname` varchar(50) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `provider_id` varchar(255) DEFAULT NULL,
  `provider` enum('GOOGLE','KAKAO','LOCAL') NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `status` enum('ACTIVE','SUSPENDED','WITHDRAWN') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_members_email_provider` (`email`,`provider`),
  UNIQUE KEY `uk_members_provider_provider_id` (`provider`,`provider_id`),
  UNIQUE KEY `uk_members_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movie_genres` (
  `tmdb_genre_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movie_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `genre_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_movie_genre` (`movie_id`,`tmdb_genre_id`),
  CONSTRAINT `FK4ak9svw913jblkfgru84h2phd` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movies` (
  `adult` bit(1) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `popularity` double DEFAULT NULL,
  `rating_sum` decimal(12,1) NOT NULL,
  `release_date` date DEFAULT NULL,
  `review_count` int NOT NULL,
  `runtime_minutes` int DEFAULT NULL,
  `vote_average` double DEFAULT NULL,
  `vote_count` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `tmdb_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `original_language` varchar(10) DEFAULT NULL,
  `status` varchar(50) DEFAULT NULL,
  `original_title` varchar(500) DEFAULT NULL,
  `title` varchar(500) NOT NULL,
  `backdrop_path` varchar(255) DEFAULT NULL,
  `overview` text,
  `poster_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp5019n1rxysavm3imviq5028i` (`tmdb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
  `amount` bigint NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `payment_id` varchar(64) NOT NULL,
  `product_type` enum('SITE_SUPPORT') NOT NULL,
  `status` enum('CANCELLED','CREATED','EXPIRED','FAILED','PAID') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payments_payment_id` (`payment_id`),
  KEY `idx_payments_status_created_at` (`status`,`created_at`),
  KEY `idx_payments_member_id_created_at` (`member_id`,`created_at`),
  CONSTRAINT `FKtvbq19graff4nnoqpngbe762` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `post` (
  `comment_count` int NOT NULL,
  `view_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `status` enum('BLINDED','DELETED','VISIBLE') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6okhu5mmwauixu447h0fnipql` (`member_id`),
  KEY `idx_post_status_created_at` (`status`,`created_at`),
  CONSTRAINT `FK6okhu5mmwauixu447h0fnipql` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `post_likes` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_like_member` (`post_id`,`member_id`),
  KEY `FKsr9uiilpnflc32a04yyx7bun` (`member_id`),
  CONSTRAINT `FKmxmoc9p5ndijnsqtvsjcuoxm3` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKsr9uiilpnflc32a04yyx7bun` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review_likes` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `review_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_likes_member_id_review_id` (`member_id`,`review_id`),
  KEY `idx_review_likes_review_id` (`review_id`),
  CONSTRAINT `FKeh2w7e058vjvllrbbgv6rbhp7` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `FKm2uonfg8ky6jwtu6iugkilox8` FOREIGN KEY (`review_id`) REFERENCES `reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reviews` (
  `rating` decimal(2,1) NOT NULL,
  `spoiler` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `movie_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `rewritten_at` datetime(6) DEFAULT NULL,
  `content` varchar(1000) NOT NULL,
  `status` enum('BLIND','DELETED','VISIBLE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reviews_member_id_movie_id` (`member_id`,`movie_id`),
  KEY `idx_reviews_movie_id_status_created_at` (`movie_id`,`status`,`created_at`),
  KEY `idx_reviews_member_id_status` (`member_id`,`status`),
  KEY `idx_reviews_status_created_at` (`status`,`created_at`),
  CONSTRAINT `FK6wsc8rr8tb1fc782foh3mjc8q` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `FK87tlqya0rq8ijfjscldpvvdyq` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wishlists` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `movie_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wishlists_member_id_movie_id` (`member_id`,`movie_id`),
  KEY `idx_wishlists_movie_id` (`movie_id`),
  CONSTRAINT `fk_wishlists_member_id` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `fk_wishlists_movie_id` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `type` enum('MOVIE_RELEASE') NOT NULL,
  `movie_id` bigint DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `message` varchar(1000) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `event_key` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notifications_member_id_event_key` (`member_id`,`event_key`),
  KEY `idx_notifications_member_id_is_read` (`member_id`,`is_read`),
  KEY `idx_notifications_movie_id` (`movie_id`),
  CONSTRAINT `fk_notifications_member_id` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`),
  CONSTRAINT `fk_notifications_movie_id` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
