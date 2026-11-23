# 배포 기본 구성

## 구성 요소

| 서비스 | 이미지 | 용도 |
|---|---|---|
| `server` | 이 저장소 `Dockerfile` | Spring API (8080) |
| `mysql` | `mysql:8.4` | 메인 DB |
| `pgvector` | `pgvector/pgvector:pg16` | AI 서버 벡터 DB (스키마는 AI 서버가 관리) |
| `ai-server` | `AI_SERVER_IMAGE` | AI 서버 (`--profile ai`일 때만) |

## 실행

```bash
cp .env.example .env        # 값 채우기 (.env는 커밋 금지)
docker compose up -d --build                 # MySQL + pgvector + Spring
docker compose --profile ai up -d            # AI 서버까지
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

## 환경 변수 (prod 프로필)

| 변수 | 필수 | 설명 |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | O | MySQL 접속 정보 |
| `JWT_SECRET` | O | `openssl rand -base64 64` |
| `AI_SERVER_URL` | O | AI 서버 주소 |
| `AI_CALLBACK_SECRET` | O | AI 서버와 같은 값. **비어 있으면 서버가 시작되지 않음** |
| `SERVER_PUBLIC_URL` | O | AI 서버가 콜백을 보낼 이 서버의 외부 주소 (예: `https://api.도메인`) |
| `CORS_ALLOWED_ORIGINS` | O | 웹 프론트 주소, 확장 프로그램 origin(`chrome-extension://{ID}`), 쉼표 구분 |
| `SWAGGER_ENABLED` |  | 기본 `false` |
| `JPA_DDL_AUTO` |  | 기본 `update`. 마이그레이션 도구 도입 후 `validate` |

## 운영 시 참고

- HTTPS는 앞단(Nginx·로드밸런서)에서 종료하고 `X-Forwarded-*` 헤더를 넘긴다 (`server.forward-headers-strategy=framework`).
- 컨테이너·JVM·DB 시간대는 모두 `Asia/Seoul`로 맞춘다. AI에 보내는 `saved_at` 오프셋이 이 값을 따른다.
- 상태 확인: `GET /actuator/health` (인증 불필요, 상세 정보 비공개).
- 종료 시 처리 중 요청을 최대 20초 기다린다 (graceful shutdown).
- MySQL·pgvector 포트는 `127.0.0.1`에만 열려 있다.

## 배포 방식 (결정)

```
GitHub PR ──CI──▶ 테스트(MySQL) + 이미지 빌드 확인
main push ──Deploy──▶ GHCR 이미지 푸시 ──SSH──▶ 서버에서 deploy/deploy.sh
                                               └ 상태 확인 실패 시 이전 이미지로 롤백
인터넷 ──HTTPS──▶ Nginx(호스트, Let's Encrypt) ──▶ 127.0.0.1:8080 Spring
```

- **서버**: Ubuntu 클라우드 VM 1대 (Docker, Docker Compose, Nginx, certbot 설치)
- **도메인**: `api.{서비스 도메인}` → 서버 IP. `deploy/nginx/ssok.conf`의 `api.ssok.example`을 바꿔 쓴다.
- **이미지**: `ghcr.io/apps-sookmyung/2026-ssok-server:{커밋 7자리}`, `:latest`

### 서버 최초 준비

```bash
git clone https://github.com/APPS-sookmyung/2026-Ssok-Server.git ~/ssok && cd ~/ssok
cp .env.example .env   # 운영 값 채우기 (SERVER_PUBLIC_URL=https://api.도메인)
sudo cp deploy/nginx/ssok.conf /etc/nginx/sites-available/ssok.conf   # 도메인 수정
sudo ln -s /etc/nginx/sites-available/ssok.conf /etc/nginx/sites-enabled/
sudo certbot --nginx -d api.도메인
sudo nginx -t && sudo systemctl reload nginx
```

### GitHub 저장소 설정

| Secrets | 설명 |
|---|---|
| `DEPLOY_HOST` | 서버 IP 또는 도메인. 비어 있으면 이미지만 올리고 배포는 건너뜀 |
| `DEPLOY_USER` | SSH 사용자 |
| `DEPLOY_SSH_KEY` | SSH 개인 키 |
| `DEPLOY_PORT` | 선택, 기본 22 |
| `DEPLOY_PATH` | 선택, 기본 `~/ssok` |

`production` Environment를 만들어 두면 배포 전 승인 단계를 걸 수 있다.

### 수동 배포·롤백

```bash
SERVER_IMAGE=ghcr.io/apps-sookmyung/2026-ssok-server:abc1234 ./deploy/deploy.sh
```

## 아직 정하지 않은 것

- 클라우드 업체·서버 사양, 실제 도메인
- 파일 저장소 (12번 작업)
- DB 마이그레이션 도구, 백업·복구, 로그 수집 (18번 작업)
- AI 서버 컨테이너의 실제 환경 변수 이름 (`docker-compose.yml`의 `ai-server`는 가정값)
