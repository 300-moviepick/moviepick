SET NAMES utf8mb4;

-- moviepick 시연·개발용 초기 데이터
-- Docker MySQL 컨테이너가 처음 만들어질 때 schema.sql 다음에 실행된다.
-- 영화 데이터는 저장소에 올리지 않고 db/seed/ 폴더에 둔 파일이 이어서 실행된다.

-- 관리자 계정 (비밀번호는 저장소에 남기지 않고 팀에 따로 전달한다. 운영에서는 배포 직후 반드시 다른 비밀번호로 바꾼다: README 배포 절 참고)
INSERT INTO members (provider, provider_id, email, password_hash, nickname, role, status, created_at, updated_at)
VALUES ('LOCAL', NULL, 'admin@admin.com', '$2a$10$NUpNDbLPUqaBTrkixyyi8.tnkMp/MQVJGiuwRW68O2k9PGOh4/QG.', '관리자', 'ADMIN', 'ACTIVE', NOW(6), NOW(6));
