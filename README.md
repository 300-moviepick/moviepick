# MoviePick

영화 탐색 · 리뷰 커뮤니티 플랫폼

## 기술 스택
Java 17 · Spring Boot 4.1.1 · Spring Security(JWT, OAuth2) · Spring Data JPA · QueryDSL · Spring Batch · Spring AI(OpenAI) · MySQL 8.0 · Redis 7.2 · PortOne · Docker · Gradle

## 처음 실행하기 (팀원용)
팀 전원이 같은 Docker MySQL·Redis를 쓴다. PC에 설치된 MySQL(3306)·Redis(6379)와 겹치지 않도록 MySQL은 3307, Redis는 6380 포트를 쓴다.

> 터미널 명령은 **Git Bash**에서 실행한다. PowerShell에서는 SQL 파일 리다이렉트(`<`)가 동작하지 않고 `>`로 저장한 파일의 인코딩이 바뀐다.

1. Docker Desktop을 켠다.
2. 프로젝트 맨 위에서 `.env.example`을 복사해 `.env`를 만든다. Windows PowerShell은 `Copy-Item .env.example .env`, Git Bash·Mac은 `cp .env.example .env`.
   - `JWT_SECRET`, `TMDB_ACCESS_TOKEN`, PortOne 값 같은 비밀값은 `.env`에 채운다. 값을 받는 곳은 파일 안 안내를 참고한다.
   - `.env`는 저장소에 올라가지 않는다.
3. 팀에서 공유받은 영화 덤프 파일(예: `movies_batch_20261106.sql`)을 `db/seed/`에 넣는다.
   - `db/seed/` 안의 파일은 Git에 올라가지 않는다(`.gitignore`). 덤프 파일은 팀에서 따로 받는다.
   - 예전 `movies_data.sql`(100편)이 있으면 삭제한다. 이 폴더의 `.sql` 파일은 이름과 관계없이 **모두** 실행되므로 덤프는 하나만 둔다.
4. DB를 새로 만든다.
   ```bash
   docker compose down -v
   ```
   ```bash
   docker compose up -d
   ```
   - **주의: `down -v`는 로컬 DB 데이터(회원, 리뷰, 게시글 등)를 모두 지운다.**
   - `-v`를 빼면 예전 볼륨이 남아 초기화가 다시 실행되지 않는다.
5. MySQL 초기화가 끝날 때까지 기다린다.
   ```bash
   docker compose logs -f mysql
   ```
   - **마지막에 나오는** `ready for connections ... port: 3306`을 확인한다. 확인했으면 `Ctrl+C`로 로그 보기를 끝낸다(컨테이너는 계속 실행된다).
   - 중간에 나오는 `ready for connections ... port: 0`은 초기화용 임시 서버라서 기준이 아니다.
   - 영화 덤프 적재를 포함한 초기화는 시간이 조금 걸린다. 초기화 중에 앱을 실행하면 `Communications link failure`가 난다.
6. 앱을 실행한다. IntelliJ에서 `MoviePickApplication`을 실행하거나(아래 설정 참고) `./gradlew bootRun`을 쓴다. 앱은 `.env`를 읽어 Docker DB에 연결한다.

### DB 초기화 순서
MySQL 데이터 볼륨이 비어 있을 때(처음 만들 때) `db/init/00-init.sh`가 **한 번만** 실행한다. 이미 있는 볼륨에서는 다시 실행되지 않는다.

| 순서 | 파일 | 내용 |
|---|---|---|
| 1 | `db/schema.sql` | 기본 테이블 |
| 2 | `db/movie-embeddings-schema.sql` | 영화 임베딩(AI 의미 검색) |
| 3 | `db/ai-credit-wallet-schema.sql` | AI 크레딧 지갑 |
| 4 | `db/ai-credit-transactions-schema.sql` | AI 크레딧 거래 원장 |
| 5 | `db/ai-recommendation-feedback-schema.sql` | AI 추천 피드백 |
| 6 | `db/batch-schema.sql` | Spring Batch 실행 이력 테이블 |
| 7 | `db/data.sql` | 관리자 계정 |
| 8 | `db/seed/*.sql` | 영화 덤프. 폴더 안의 `.sql`을 이름순으로 모두 실행하고, 없으면 영화 없이 시작한다 |

- SQL 하나가 실패하면 초기화가 그 자리에서 멈춘다.
- 진행 상황은 `docker compose logs mysql`의 `[moviepick-init] 실행: ...` 줄로 확인한다.

### 환경변수
`.env.example`에 모든 변수와 설명이 있다. 값을 채워야 하는 것과 기본값으로 동작하는 것은 아래와 같다.

| 구분 | 변수 | 기본값 | 설명 |
|---|---|---|---|
| 필수 | `JWT_SECRET`, `TMDB_ACCESS_TOKEN` | 없음 | 비어 있으면 앱이 시작되지 않는다 |
| DB·Redis | `MYSQL_*`, `REDIS_PORT` | 3307 / 6380 등 | Docker와 앱이 같이 읽는다 |
| 결제 | `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_WEBHOOK_SECRET` | 빈 값 | 비어 있어도 시작되지만 결제 확인·웹훅이 실패한다. PortOne 콘솔의 웹훅 주소는 `{서버 주소}/api/webhooks/portone`(후원·AI 크레딧 공통) |
| 소셜 로그인 | `KAKAO_*`, `GOOGLE_*` | 더미 값 | 카카오·구글 로그인을 쓸 때 채운다 |
| AI 스위치 | `AI_CHAT_PROVIDER`, `AI_EMBEDDING_PROVIDER` | `none` | `openai`일 때만 AI 추천·크레딧 구매 시작(chat), 의미 검색(embedding)이 켜진다 |
| AI | `OPENAI_API_KEY` | 빈 값 | AI를 켤 때 필요 |
| AI 상한 | `OPENAI_CHAT_*`, `OPENAI_EMBEDDING_*` | 모델 `gpt-4o-mini`·`text-embedding-3-small`, 1000토큰, 15초, 재시도 0, 768차원 | 보통 바꾸지 않는다 |
| 프론트 | `APP_FRONTEND_URL`, `APP_FRONTEND_ALLOWED_ORIGINS` | `http://localhost:5173` | 소셜 로그인 후 이동 주소, CORS 허용 주소 |
| 쿠키 | `APP_COOKIE_SECURE` | `false` | HTTPS 운영에서는 `true` |
| 배치 | `MOVIE_BATCH_*` | 아래 [정기 수집](#정기-수집) 참고 | |

## IntelliJ 실행 설정
`Run/Debug Configurations` → `MoviePickApplication`

| 항목 | 값 | 이유 |
|---|---|---|
| Active profiles | `local` | 로컬 설정과 초기 수집 기능이 `local` 프로필 전용이다 |
| Working directory | `moviepick` 폴더(프로젝트 맨 위) | `.env`를 이 폴더 기준 상대 경로로 읽는다. 다른 폴더면 DB 설정을 읽지 못한다 |
| Shorten command line | `JAR manifest` | `Command line is too long` 오류를 막는다 |
| Environment variables | 비워 둔다 | 실행 설정의 환경 변수가 `.env`보다 우선한다. 같은 이름이 있으면 `.env`를 고쳐도 반영되지 않는다 |

- `build.gradle`을 바꾸거나 바뀐 내용을 받았으면 Gradle 창에서 새로고침(Reload All Gradle Projects)을 한다.

## 영화 데이터 수집 (Spring Batch)
TMDB 인기 영화를 수집해 `movies`, `movie_genres`에 저장한다. 팀원은 보통 공유받은 덤프를 쓰면 되므로 직접 수집할 필요가 없다.

### 초기 수집 (덤프를 새로 만들 때)
- 인기 영화 목록 **100페이지(약 2,000편)**를 받는다. 실측으로 약 10분 걸렸다.
- 앱이 시작될 때 실행되며 `local` 프로필에서만 동작한다.
- 빈 DB에서 시작하려면 `db/seed/`에서 덤프를 빼고 DB를 새로 만든다(`down -v` → `up -d`).
- `.env`의 `MOVIE_BATCH_SCHEDULER_ENABLED`는 비워 두거나 `false`로 둔다.

실행 옵션 `--movie.batch.initial-run=true`를 **실행 인자로만** 준다. `.env`에 넣으면 앱을 켤 때마다 다시 수집한다.
- IntelliJ: 실행 설정의 Program arguments에 `--movie.batch.initial-run=true`를 넣는다.
- Gradle:
  ```bash
  ./gradlew bootRun --args='--movie.batch.initial-run=true'
  ```

완료는 로그로 판단한다.
- 진행 중에는 페이지마다 `영화 수집 진행: page=N, 성공=..., 실패=...`가 나온다. N은 100까지 올라간다.
- 끝나면 `초기 대량 수집 종료: executionId=..., status=COMPLETED`가 나온다.
- **수집이 끝나도 앱은 종료되지 않는다.** 직접 종료하고 Program arguments의 옵션을 지운다.

### 실패했을 때
- 1편이라도 실패하면 Job이 `FAILED`가 되고 **앱 시작도 실패**한다. 로그에 `초기 영화 수집 Batch 실패: FAILED`가 나온다.
- 그때까지 저장된 영화는 DB에 남는다.
- 원인은 `초기 영화 수집 결과: 저장=..., 실패=..., 중단 페이지=..., 사유=...` 로그와 그 앞의 경고 로그로 확인한다.
- **같은 옵션으로 다시 실행**한다. 이미 있는 영화는 `tmdb_id` 기준으로 갱신되므로 중복이 생기지 않는다. 다만 100페이지를 처음부터 다시 받는다.
- 앱 자체가 정상인지는 옵션 없이 실행해서 확인한다. `Started MoviePickApplication`이 나오면 앱은 정상이고, 원인은 수집 쪽에 있다.

### 정기 수집
매일 정해진 시각에 인기 목록 **상위 페이지만** 다시 받아 새 영화를 추가하고, 그 범위에 든 영화의 정보(한국 개봉일 등)를 갱신한다. 기본값은 꺼져 있다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `MOVIE_BATCH_SCHEDULER_ENABLED` | `false` | `true`일 때만 정기 수집이 동작한다. 프로필과 관계없다 |
| `MOVIE_BATCH_SCHEDULER_CRON` | `0 0 3 * * *` | 실행 시각(매일 03:00, 한국 시간) |
| `MOVIE_BATCH_COLLECT_PAGES` | `1` | 수집할 페이지 수. 1페이지에 20편, **최대 5** |

초기 수집과 정기 수집은 페이지 설정이 서로 다르다.

| 구분 | 페이지 설정 | 기본 / 최대 | 지정 방법 |
|---|---|---|---|
| 초기 수집 | `movie.batch.initial-pages` | 100 / 100 (약 2,000편) | 실행 인자. 보통 지정할 필요 없음 |
| 정기 수집, 관리자 수동 실행 | `MOVIE_BATCH_COLLECT_PAGES` | 1 / 5 (최대 100편) | `.env` 또는 환경변수 |

- `MOVIE_BATCH_COLLECT_PAGES`는 초기 수집에 영향을 주지 않는다.
- 관리자는 `POST /api/admin/batch/movies/sync`로 정기 수집과 같은 작업을 직접 실행할 수 있다.

### 덤프 만들기 (Git Bash)
초기 수집이 끝난 DB에서 영화 데이터만 덤프한다. 대상은 `movies` → `movie_genres`이고, 이 순서가 FK 순서다.

1. 앱을 종료한다.
2. MySQL에 접속한다.
   ```bash
   docker exec -it moviepick-mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
   ```
3. 덤프 전에 아래 값을 확인한다.
   ```sql
   -- 총 편수
   SELECT COUNT(*) FROM movies;
   -- 리뷰 집계 값이 남아 있으면 안 된다 (0이어야 함)
   SELECT COUNT(*) FROM movies WHERE rating_sum <> 0 OR review_count <> 0;
   ```
4. 덤프한다. 파일 이름은 자유지만 확장자는 `.sql`이다.
   ```bash
   docker exec moviepick-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 --no-create-info --complete-insert --single-transaction --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" movies movie_genres' > db/seed/movies_batch_YYYYMMDD.sql
   ```
   - `--no-create-info`: 데이터만 담는다. 테이블은 `schema.sql`이 만든다.
   - `--complete-insert`: INSERT에 컬럼 목록을 넣어, 테이블의 컬럼 순서가 바뀌어도 적재되게 한다.
   - 비밀번호는 명령에 쓰지 않는다. 컨테이너 안의 환경변수를 쓴다.
5. 만든 파일은 Git에 올라가지 않으므로 팀원에게 따로 공유한다.

## 문제 해결
| 증상 | 원인 | 해결 |
|---|---|---|
| `Communications link failure` | MySQL 초기화가 끝나기 전에 앱을 실행했다 | `docker compose logs -f mysql`에서 마지막 `ready for connections ... port: 3306`을 확인한 뒤 다시 실행한다 |
| `Command line is too long` | IntelliJ 실행 명령이 Windows 길이 제한을 넘었다 | 실행 설정의 Shorten command line을 `JAR manifest`로 바꾼다 |
| `Unable to determine Dialect` | `.env`를 읽지 못해 DB 설정이 비었다 | 실행 설정의 Working directory가 `moviepick` 폴더인지 확인한다 |
| `Schema-validation: missing table ...` 등 스키마 검증 오류 | 오래된 DB 볼륨이라 새 테이블·컬럼이 없다 | `docker compose down -v` → `docker compose up -d`로 DB를 새로 만든다(로컬 데이터가 지워진다) |
| 초기 수집 후 앱 시작 실패 | 수집 중 1편 이상 실패했다 | 위 [실패했을 때](#실패했을-때)를 참고한다 |
| 원인을 알 수 없는 오류 | 코드 문제인지 IntelliJ 설정 문제인지 모른다 | 먼저 `./gradlew bootRun`으로 실행해 본다. 이렇게 하면 정상이면 IntelliJ 실행 설정을 확인한다 |
| SQL 파일 적재·덤프가 안 되거나 한글이 깨진다 | PowerShell은 `<` 리다이렉트를 지원하지 않고, `>`로 저장하면 인코딩이 바뀐다 | Git Bash에서 실행한다 |

### 이전 방식에서 옮겨 올 때
- **`application-local.yml`은 더 이상 필요 없다.** 남아 있으면 그 안의 `spring.datasource`가 우선해서 예전 DB(3306)에 연결된다. datasource 부분을 지우거나 파일을 삭제한다.
- 그 파일이나 다른 곳에 있던 비밀값(TMDB 토큰, PortOne 값 등)은 `.env`로 옮긴다.
- IntelliJ 실행 설정(Run/Debug Configurations)의 환경 변수에 같은 값(`JWT_SECRET`, `TMDB_ACCESS_TOKEN` 등)이 있으면 지운다. **환경 변수가 `.env`보다 우선**하므로 `.env`를 고쳐도 반영되지 않는다.

## 개발 참고
- DB 구조는 `db/schema.sql`로 관리한다. 앱은 `ddl-auto: validate`로 엔티티와 DB가 같은지만 확인한다.
  - 엔티티를 바꾼 사람은 같은 PR에서 `db/schema.sql`도 고친다.
  - dev를 받은 뒤 스키마 검증 오류가 나면 위 [문제 해결](#문제-해결)처럼 DB를 새로 만든다.
- 응답은 `ApiResponse`를, 예외는 `BusinessException`과 도메인별 `ErrorCode`를 사용한다.
- 테스트는 H2로 실행된다(`src/test/resources/config/application.yml`).

## 배포 (운영, 초안)
> ⑤-2에서 실제 진행 순서에 맞춰 다시 고친다. 명령은 EC2(Ubuntu)의 터미널에서 실행한다(PC에서 실행하는 명령은 따로 표시).

### 구성
| 항목 | 내용 |
|---|---|
| 주소 | 프론트 `https://www.moviepick.kr`(Vercel, 루트 `moviepick.kr`은 www로 이동), 백엔드 `https://api.moviepick.kr` |
| 서버 | AWS EC2 1대(서울, Ubuntu, 메모리 4GB). Docker로 앱·MySQL·Redis·Caddy를 함께 실행 |
| 파일 | `docker-compose.prod.yml`(운영 compose), `Dockerfile`(앱 이미지), `Caddyfile`(HTTPS·리버스 프록시), `.env.prod.example`(운영 `.env` 예시), `src/main/resources/application-prod.yml`(prod 프로필) |
| 요청 경로 | 브라우저 → Vercel(`/api`, `/oauth2/authorization`, `/login/oauth2` 중계) → Caddy(443) → 앱(8080). PortOne 웹훅은 Caddy로 바로 들어온다 |
| 브랜치 | EC2는 `main`을 받는다. 배포 전에 `dev` → `main` 머지 |

### 1. EC2 준비
1. EC2 인스턴스를 만든다(서울 리전, Ubuntu, 메모리 4GB). 키 페어 `.pem`은 공유하거나 커밋하지 않는다.
2. 고정 IP(탄력적 IP)를 연결한다.
3. 보안 그룹 인바운드 규칙
   - TCP 80·443: 전체(0.0.0.0/0) — 인증서 발급과 HTTPS
   - TCP 22: **내 IP만**
   - UDP 443: 선택(HTTP/3). 열지 않아도 정상 동작한다
   - **3306(MySQL)·6379(Redis)·8080(앱)은 열지 않는다**
4. DNS에 `api` A 레코드 → 고정 IP. **Caddy를 띄우기 전에** 연결되어 있어야 인증서가 발급된다(`nslookup api.moviepick.kr`로 확인).
5. 스왑 2GB를 만든다(서버에서 이미지를 빌드할 때 메모리 여유).
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   ```
   ```bash
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

### 2. Docker 엔진·compose 설치
PC의 Docker Desktop과 별개로 EC2에 설치한다([공식 안내](https://docs.docker.com/engine/install/ubuntu/)).
```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```
- 마지막 명령 후 접속을 끊었다가 다시 접속한다. `docker compose version`으로 확인한다.

### 3. 저장소 받기
```bash
git clone -b main https://github.com/spa-300/moviepick.git ~/moviepick
```

### 4. 영화 덤프 옮기기
`db/seed/`의 덤프는 Git에 없으므로 PC에서 직접 보낸다. **처음 compose를 실행하기 전에** 넣어야 적재된다.
```bash
# PC의 Git Bash, 백엔드 폴더에서 실행
scp -i <키 파일 경로>.pem db/seed/movies_batch_20261106.sql ubuntu@<고정 IP>:~/moviepick/db/seed/
```
- `db/seed/`에는 덤프 하나만 둔다. 처음 실행 때 `db/init/00-init.sh`가 스키마(`movie_embeddings`, `AI_CREDIT`이 포함된 `payments.product_type` 포함) → 관리자 계정 → 덤프 순서로 적재한다.

### 5. 운영 `.env` 작성
```bash
cd ~/moviepick && cp .env.prod.example .env && nano .env
```
- 파일 안 안내대로 채운다. **빈 값으로 두지 않는다**(기본값이 있는 변수를 빈 값으로 두면 기동에 실패할 수 있다. 안 쓰는 줄은 지운다).
- 아래 [운영 .env 체크리스트](#운영-env-체크리스트)로 확인한다.

### 6. 실행
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
```bash
docker compose -f docker-compose.prod.yml logs -f mysql
```
- 마지막 `ready for connections ... port: 3306`을 확인한다(덤프 적재로 몇 분 걸릴 수 있다). 그동안 앱이 DB 연결에 실패하면 자동으로 다시 시작된다.
- `docker compose -f docker-compose.prod.yml logs -f app`에서 `Started MoviePickApplication`을 확인한다.

### 7. 인증서 확인
```bash
docker compose -f docker-compose.prod.yml logs caddy | grep -i "certificate obtained"
```
```bash
curl -s "https://api.moviepick.kr/api/movies?page=0&size=1" | head -c 200
```
- `"success":true`로 시작하는 응답이 오면 HTTPS·앱·DB가 모두 정상이다.

### 8. 관리자 비밀번호 변경
시드 관리자 계정(`db/data.sql`)은 공개된 비밀번호를 쓰므로 운영에서는 바로 바꾼다. 비밀번호 변경 API가 없어 DB에서 직접 바꾼다. **비밀번호와 해시는 어디에도 적지 않는다.**
1. bcrypt 해시를 만든다(비밀번호는 입력 프롬프트에 친다. 명령에 쓰면 기록에 남는다).
   ```bash
   sudo apt-get install -y apache2-utils && htpasswd -nBC 10 ""
   ```
   - 출력의 맨 앞 `:`를 뺀 `$2y$10$...` 전체가 해시다.
2. DB에 접속해 관리자 계정의 `password_hash`를 바꾼다.
   ```bash
   docker compose -f docker-compose.prod.yml exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
   ```
   - `UPDATE members SET password_hash = '<만든 해시>' WHERE email = 'admin@admin.com';` 실행 후 `exit`
3. 새 비밀번호로 관리자 로그인을 확인한다.

### 9. 운영 임베딩 일괄 생성 (1회)
AI 추천은 영화 임베딩이 있어야 동작한다. 영화 2,000편 기준 비용 약 0.02달러.
1. `.env`에 `MOVIE_EMBEDDING_BULKSYNC_ENABLED=true` 줄을 추가하고 앱만 다시 띄운다.
   - 설정 이름 `movie.embedding.bulk-sync.enabled`를 환경변수로 쓴 것이다(점은 `_`, `-`는 뺀다).
   ```bash
   docker compose -f docker-compose.prod.yml up -d app
   ```
2. `docker compose -f docker-compose.prod.yml logs -f app`에서 `전체 영화 임베딩 수집 완료 ... failed=0`을 확인한다.
3. **바로 그 줄을 지우고** 앱을 다시 띄운다(`up -d app`). 실패가 있으면 앱이 시작 실패 → 자동 재시작을 반복하며 다시 수집하므로, 실패해도 줄을 먼저 지우고 원인을 확인한다.

### 10. 배포 후 수정
- 백엔드: `dev` → `main` 머지 후 EC2에서
  ```bash
  cd ~/moviepick && git pull && docker compose -f docker-compose.prod.yml up -d --build app
  ```
  - 앱 컨테이너만 다시 빌드한다. MySQL·Redis·Caddy는 그대로 둔다.
- **운영에서 `docker compose ... down -v`는 절대 쓰지 않는다.** DB 데이터와 인증서가 모두 지워진다. 멈출 때는 `down`(`-v` 없이)만 쓴다.
- DB 구조가 바뀌면 init 스크립트는 다시 실행되지 않으므로, 바뀐 SQL을 운영 DB에 직접 적용한다(위 8-2의 접속 명령).
- `.env`를 고쳤으면 `docker compose -f docker-compose.prod.yml up -d app`으로 앱을 다시 만든다(AI 스위치도 재시작해야 반영).

### 운영 .env 체크리스트
| 변수 | 운영 값 | 확인 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | 로컬과 다른 새 값(처음 실행 후 바꾸지 않음) | [ ] |
| `MYSQL_DATABASE`, `MYSQL_USER` | `moviepick` | [ ] |
| `API_DOMAIN` | `api.moviepick.kr` | [ ] |
| `JWT_SECRET` | 로컬과 다른 새 값(Base64, 디코딩 후 32바이트 이상) | [ ] |
| `TMDB_ACCESS_TOKEN` | 채움 | [ ] |
| `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY` | 테스트 채널 값(테스트 모드 유지) | [ ] |
| `PORTONE_WEBHOOK_SECRET` | 웹훅 등록 시 발급한 시크릿. 등록 전에는 줄 삭제 | [ ] |
| `KAKAO_*`, `GOOGLE_*` | 채움(빈 값이면 기동 실패) | [ ] |
| `OPENAI_API_KEY` | 운영용 키(로컬과 분리, 선불·자동 충전 끔) | [ ] |
| `AI_CHAT_PROVIDER`, `AI_EMBEDDING_PROVIDER` | `openai` | [ ] |
| `APP_FRONTEND_URL`, `APP_FRONTEND_ALLOWED_ORIGINS` | `https://www.moviepick.kr` | [ ] |
| `APP_COOKIE_SECURE` | `true` | [ ] |
| `MOVIE_BATCH_SCHEDULER_ENABLED` | `true` | [ ] |
| `MYSQL_PORT`, `REDIS_PORT`, `OPENAI_*` 상한 | 적지 않음(기본값) | [ ] |

### 외부 콘솔 등록
| 대상 | 주소 |
|---|---|
| 카카오 Redirect URI | `https://www.moviepick.kr/login/oauth2/code/kakao` (Web 사이트 도메인 `https://www.moviepick.kr`) |
| 구글 승인된 리디렉션 URI | `https://www.moviepick.kr/login/oauth2/code/google` |
| PortOne 웹훅 | `https://api.moviepick.kr/api/webhooks/portone` |
- 로컬 개발용 주소(`http://localhost:8080/login/oauth2/code/...`)는 지우지 않는다. 운영에서는 prod 프로필이 콜백을 프론트 주소로 고정한다.

### 배포 직후 확인
- [ ] 가입·로그인·새로고침 후 로그인 유지·소셜 로그인(카카오·구글)
- [ ] 영화 탐색·상세·랭킹·정렬 / 리뷰·커뮤니티·신고
- [ ] AI 추천·크레딧 차감(응답이 Vercel 대기 한도 안에 오는지)
- [ ] 크레딧 구매·후원 테스트 결제와 웹훅 수신 로그(`logs app | grep 웹훅`)
- [ ] Vercel 중계: `/api/...`, `/oauth2/authorization/kakao`, `/login/oauth2/code/...`, `/movies/1`에서 새로고침
- [ ] PortOne 모바일 결제 복귀 주소 `/ai/credits`, `/support`가 열리는지
- [ ] 관리자 비밀번호 변경 완료, 운영 임베딩 생성 완료(`.env`에서 일괄 생성 줄 삭제)
