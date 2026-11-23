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

## 아직 정하지 않은 것

- 배포 대상(클라우드·서버), 도메인, 파일 저장소
- DB 마이그레이션 도구, 백업·복구, 로그 수집 (18번 작업)
- AI 서버 컨테이너의 실제 환경 변수 이름 (`docker-compose.yml`의 `ai-server`는 가정값)
