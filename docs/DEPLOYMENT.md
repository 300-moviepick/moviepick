# 배포 가이드

운영(EC2) 배포 절차입니다. 프로젝트 소개는 [README](../README.md), 로컬 실행은 [DEVELOPMENT.md](DEVELOPMENT.md)를 봅니다.

## 배포 (운영)
명령은 따로 표시하지 않으면 EC2(Ubuntu)의 터미널에서 실행한다. PC에서 실행하는 명령은 `# PC` 주석으로 표시한다.
서버 IP와 키 파일 이름은 문서에 적지 않고 `<서버 IP>`, `<키 파일>`로 쓴다.

### 구성
| 항목 | 내용 |
|---|---|
| 주소 | 서비스 `https://www.moviepick.kr`(루트 `moviepick.kr`은 www로 영구 이동), `https://api.moviepick.kr`(PortOne 웹훅 수신용). 세 주소 모두 같은 EC2 |
| 서버 | AWS EC2 1대(서울, Ubuntu 24.04, t4g.medium). Docker Compose로 app·mysql·redis·caddy를 함께 실행 |
| 파일 | `docker-compose.prod.yml`(운영 compose), `Dockerfile`(앱 이미지), `Caddyfile`(HTTPS·프론트 화면 제공·리버스 프록시), `.env.prod.example`(운영 `.env` 예시), `src/main/resources/application-prod.yml`(prod 프로필) |
| 프론트 | 프론트 저장소(`moviepick-front`)에서 빌드한 결과를 서버의 `~/moviepick/front/`에 올린다. Caddy가 `/srv/front`로 읽어 제공한다 |
| 코드 전달 | 서버는 git에서 코드를 받지 않는다. PC에서 만든 압축 파일을 올린다(아래 3단계, 10단계) |
| 브랜치 | 기능 브랜치 → `dev`(개발 통합) → `main`(배포 기준). EC2에는 `main`으로 만든 압축을 올린다 |

```
브라우저 ──https──▶ Caddy (EC2 443)
                     ├─ www.moviepick.kr
                     │    ├─ /api/*, /oauth2/authorization/*, /login/oauth2/* ──▶ Spring Boot (app:8080)
                     │    └─ 그 외 경로 ──▶ 프론트 정적 파일 (/srv/front, 없는 경로는 index.html)
                     ├─ moviepick.kr ──▶ https://www.moviepick.kr 로 영구 이동
                     └─ api.moviepick.kr ──▶ Spring Boot (PortOne 웹훅)
Spring Boot ──▶ MySQL (mysql:3306), Redis (redis:6379)   ※ 컨테이너 내부망, 외부 포트 없음
```

### 1. EC2 준비
1. EC2 인스턴스를 만든다(서울 리전, Ubuntu 24.04, t4g.medium). 키 페어 파일은 공유하거나 커밋하지 않는다.
2. 고정 IP(탄력적 IP)를 연결한다.
3. 보안 그룹 인바운드 규칙
   - TCP 80·443: 전체(0.0.0.0/0) — 인증서 발급과 HTTPS
   - TCP 22: **내 IP만**
   - UDP 443: 선택(HTTP/3). 열지 않아도 정상 동작한다
   - **3306(MySQL)·6379(Redis)·8080(앱)은 열지 않는다**
4. DNS에 `www`, 루트(`moviepick.kr`), `api` A 레코드를 모두 고정 IP로 연결한다. **Caddy를 띄우기 전에** 연결되어 있어야 인증서가 발급된다(`nslookup www.moviepick.kr` 등으로 확인).
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
- 압축 해제 후 줄바꿈 변환에 쓸 `dos2unix`도 설치한다.
  ```bash
  sudo apt-get install -y dos2unix
  ```

### 3. 백엔드 코드 올리기
PC에서 `main` 브랜치를 압축해 서버로 보낸다. `.env`, `front/`, `db/seed/`의 덤프는 저장소에 없으므로 압축에 들어가지 않는다.
```bash
# PC의 Git Bash, 백엔드 저장소 맨 위 폴더에서 실행
git archive --format=tar.gz -o ~/moviepick-deploy.tar.gz main
scp -i ~/.ssh/<키 파일> ~/moviepick-deploy.tar.gz ubuntu@<서버 IP>:~/
```
```bash
mkdir -p ~/moviepick && tar -xzf ~/moviepick-deploy.tar.gz -C ~/moviepick
cd ~/moviepick && find . -path ./front -prune -o -path ./db/seed -prune -o -type f -exec dos2unix -q {} +
```
- Windows에서 만든 압축은 줄바꿈이 CRLF일 수 있어 `dos2unix`로 LF로 바꾼다(스크립트·설정 파일이 CRLF면 서버에서 오동작할 수 있다). `front/`와 `db/seed/`는 건드리지 않는다.
- 압축을 풀면 파일이 덮어써지기만 한다. 저장소에서 지운 파일은 서버에 남으므로 필요하면 직접 지운다.

### 4. 영화 덤프 옮기기
`db/seed/`의 덤프는 Git에 없으므로 PC에서 직접 보낸다. **처음 compose를 실행하기 전에** 넣어야 적재된다.
```bash
# PC의 Git Bash, 백엔드 저장소 맨 위 폴더에서 실행
scp -i ~/.ssh/<키 파일> db/seed/movies_batch_20261106.sql ubuntu@<서버 IP>:~/moviepick/db/seed/
```
- `db/seed/`에는 덤프 하나만 둔다. 처음 실행 때 `db/init/00-init.sh`가 스키마(`movie_embeddings`, `AI_CREDIT`이 포함된 `payments.product_type` 포함) → 관리자 계정 → 덤프 순서로 적재한다.

### 4-1. 프론트 화면 올리기
프론트 저장소에서 빌드한 결과를 `~/moviepick/front/`에 푼다. 절차는 아래 [10. 배포 후 수정](#10-배포-후-수정)의 프론트 절차와 같다. 이 폴더가 비어 있으면 `www.moviepick.kr`에 화면이 나오지 않는다.

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
curl -s "https://www.moviepick.kr/api/movies?page=0&size=1" | head -c 200
```
- `"success":true`로 시작하는 응답이 오면 HTTPS·Caddy 중계·앱·DB가 모두 정상이다. 브라우저로 `https://www.moviepick.kr`을 열어 화면도 확인한다.

### 8. 관리자 비밀번호 변경
시드 관리자 계정(`db/data.sql`)의 비밀번호는 저장소에 두지 않고 팀에 따로 전달한다. 운영에서는 배포 직후 반드시 다른 비밀번호로 바꾼다. 비밀번호 변경 API가 없어 DB에서 직접 바꾼다. **비밀번호와 해시는 어디에도 적지 않는다.**
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
`.env`, `front/`, `db/seed/`는 압축에 없으므로 서버에 그대로 유지된다.

**프론트를 고쳤을 때** (프론트 저장소, PC PowerShell → 서버). Caddy는 다시 시작하지 않아도 된다.
```powershell
# PC PowerShell, 프론트 저장소 맨 위 폴더에서 실행
npm run build
tar -czf "$HOME\front-dist.tar.gz" -C dist .
scp -i "$HOME\.ssh\<키 파일>" "$HOME\front-dist.tar.gz" ubuntu@<서버 IP>:~/
```
```bash
cd ~/moviepick && rm -rf front && mkdir front && tar -xzf ~/front-dist.tar.gz -C front
```

**백엔드를 고쳤을 때** (`dev` → `main` 머지 후, PC Git Bash → 서버)
```bash
# PC의 Git Bash, 백엔드 저장소 맨 위 폴더에서 main을 최신으로 받은 뒤 실행
git archive --format=tar.gz -o ~/moviepick-deploy.tar.gz main
scp -i ~/.ssh/<키 파일> ~/moviepick-deploy.tar.gz ubuntu@<서버 IP>:~/
```
```bash
cd ~/moviepick && tar -xzf ~/moviepick-deploy.tar.gz -C ~/moviepick
find . -path ./front -prune -o -path ./db/seed -prune -o -type f -exec dos2unix -q {} +
docker compose -f docker-compose.prod.yml up -d --build app
```
- 앱 컨테이너만 다시 빌드한다. MySQL·Redis·Caddy는 그대로 둔다.
- `Caddyfile`이나 `docker-compose.prod.yml`을 바꿨으면 caddy에도 반영한다.
  - compose를 바꿨을 때: `docker compose -f docker-compose.prod.yml up -d`(바뀐 서비스만 다시 만든다)
  - `Caddyfile`만 바꿨을 때: `docker compose -f docker-compose.prod.yml restart caddy`(파일 하나를 연결한 볼륨이라 압축을 풀어 새 파일이 생기면 다시 시작해야 읽는다)
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
- [ ] AI 추천·크레딧 차감
- [ ] 크레딧 구매·후원 테스트 결제와 웹훅 수신 로그(`logs app | grep 웹훅`)
- [ ] Caddy 중계·화면: `/api/...`, `/oauth2/authorization/kakao`, `/login/oauth2/code/...`, `/movies/1`에서 새로고침, `https://moviepick.kr` → www 이동
- [ ] PortOne 모바일 결제 복귀 주소 `/ai/credits`, `/support`가 열리는지
- [ ] 관리자 비밀번호 변경 완료, 운영 임베딩 생성 완료(`.env`에서 일괄 생성 줄 삭제)
