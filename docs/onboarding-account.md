# 온보딩·내 정보·세션·계정 삭제

## 온보딩과 내 정보

| 기능 | 요청 | data |
|---|---|---|
| 내 정보 | GET /api/users/me | userId, email, name, profileImage, onboardingCompleted |
| 프로필 수정 | PUT /api/users/profile | 기존 ProfileUpdateResponse |
| 설문 조회 | GET /api/users/me/onboarding | completed, purposes, completedAt |
| 설문 제출·수정 | PUT /api/users/me/onboarding | 동일 |

모두 access token이 필요합니다. 사용자 ID는 인증 정보에서 얻으며 본문에서 받지 않습니다.
설문 본문 예: `{"purposes":["reference","team-share"]}`.
프론트 main의 src/constants/onboarding.ts와 같은 reference / archive / team-share / personal / etc를 사용합니다.
1~5개 선택을 받으며 중복은 제거합니다. 빈 목록·null·알 수 없는 값은 400입니다.
첫 제출 시 completedAt을 기록하고 수정할 때도 최초 완료 시각은 유지합니다.
설문 완료는 설치 안내를 위한 상태이며, 기존 북마크 API를 별도로 차단하지 않습니다.

프로필 name은 필수(최대 100자, 앞뒤 공백 제거), profileImage는 선택 URL(최대 1000자)입니다.
null로 보내면 이미지를 제거할 수 있습니다. 이메일·비밀번호는 이 API로 변경하지 않습니다.
비밀번호·세션·토큰 해시는 내 정보 응답에 노출하지 않습니다.

## 로그인·갱신·로그아웃

로그인 POST /api/auth/login 응답은 기존 accessToken, refreshToken, userId, nickname에
onboardingCompleted를 추가합니다. 계정 추가도 같은 로그인 API로 세션을 발급받습니다.

| 기능 | 요청 | 본문·결과 |
|---|---|---|
| 갱신 | POST /api/auth/refresh | {"refreshToken":"..."} → 새 accessToken, refreshToken |
| 현재 세션 로그아웃 | POST /api/auth/logout | Bearer accessToken 필요, data=true |

- 기기·로그인별 세션을 DB에 저장하고 JWT의 sid와 연결합니다.
- refresh token 원문은 저장하지 않고 SHA-256 해시만 보관합니다.
- 갱신 성공 시 refresh token을 교체하며 사용한 토큰은 401입니다. 동시 갱신은 하나만 성공합니다.
- 이전 access token은 자신의 만료까지 유지되지만, 로그아웃하면 같은 세션의 모든 access·refresh token을 즉시 차단합니다.
- 다른 기기의 로그인은 유지합니다. refresh를 재사용해도 다른 세션까지 로그아웃시키지 않습니다.
- 세션 만료·계정 삭제·없는 sid·세션 없는 구형 JWT는 401입니다.
- 기본 만료는 기존대로 access 1시간 / refresh 14일이며, 갱신 시 refresh 만료도 새로 계산합니다.
- 프론트는 갱신 요청을 세션당 한 번씩 처리하고, 401이면 재로그인합니다.
- 배포 후 기존 JWT 사용자는 재로그인이 필요합니다. 로그인 시 sid가 있는 새 토큰을 발급합니다.

## 계정 삭제

`DELETE /api/users/me`, Bearer accessToken과 본문 `{"password":"현재 비밀번호"}`가 필요합니다.
비밀번호 불일치는 401, 소유한 TEAM 스페이스가 있으면 409입니다.
팀 소유자는 먼저 소유권을 이전하거나 공간을 직접 삭제한 뒤 다시 요청해야 합니다.
스페이스 참여 상태가 동시에 바뀌어 잠금 범위가 달라져도 409로 중단하므로 재시도합니다.

성공 data: `{"accountDeleted":true,"vectorCleanupTaskCount":1}`.
vectorCleanupTaskCount는 삭제 커밋 때 예약한 AI 정리 작업 수이며 AI 삭제 완료 건수가 아닙니다.

삭제·보존 정책:

- 본인이 소유한 PERSONAL 스페이스의 북마크·라이브러리·초대·파일을 삭제합니다.
- 참여 중인 TEAM에서는 탈퇴하고, 팀의 공유 북마크·파일·라이브러리는 유지합니다.
- 남겨진 팀 파일의 uploaderId는 null이 됩니다. 프론트는 탈퇴한 사용자로 표시합니다.
- 본인이 올린 CHAT_ATTACHMENT는 현재 팀원 여부·역할과 무관하게 계정 삭제 과정에서 제거합니다.
- 설문·자신의 알림·모든 로그인 세션을 삭제합니다. 다른 사람의 알림에 남은 actorId도 제거합니다.
- 본인 이메일을 대상으로 한 초대는 이메일을 익명화하고 PENDING 상태를 취소합니다.
- 물리 파일은 기존 커밋 후 정리·실패 재시도 체계를 사용합니다. DB 롤백 시 정상 파일을 지우지 않습니다.
- 관련 공간을 ID 순으로 잠근 뒤 계정을 잠그고, 새 참여 공간이 생겼는지 다시 검사합니다.

## AI 벡터 정리

현재 AI 저장소 feat/vector-deletion-api의 app/api/vectors.py 규격에 맞춰 연결합니다.

```http
DELETE /api/v1/ai/vectors?space_id={삭제된 개인 스페이스 ID}
X-AI-Secret: 서버 간 시크릿
```

204일 때만 작업을 완료 처리합니다. 설정은 AI_VECTOR_DELETE_SECRET이며 콜백용 시크릿과 별개입니다.
공유 TEAM 벡터는 계정 삭제 때문에 지우지 않습니다.
삭제 ID만 담은 account_vector_deletion_jobs를 사용자 삭제와 같은 트랜잭션에서 기록합니다.
커밋 후 즉시 시도하고, 인증 미설정·AI 장애·예상 밖 응답은 DB에 남겨 다시 시도합니다.
기본 주기 60초, 최대 50건, 실패 간격 최대 1시간입니다. 재시작 후에도 작업이 남습니다.
account.vector-cleanup.enabled=false는 정기 재시도만 끄며 커밋 직후 시도는 유지합니다.
기존 AI 분기가 실제 배포돼 있어야 하며, 계정의 DB 삭제 성공만으로 AI 벡터 삭제 완료를 안내하면 안 됩니다.
일반 북마크·스페이스 삭제의 전체 AI 동기화 확장은 기존 담당 작업과 별도로 연결해야 합니다.

## 설정·계정 추가 메뉴 — 사용자 확정

- 설정은 내 정보·알림 화면으로 이동하는 프론트 동작입니다.
- 계정 추가는 다른 기존 계정의 로그인 세션을 추가하고 프론트에서 전환합니다.
- 소셜 계정 연결·계정 병합·OAuth는 이번 범위가 아닙니다.
- 계정 삭제·로그아웃 시 프론트도 해당 계정의 토큰과 개인정보 캐시를 비워야 합니다.

## 배포·협업

scripts/mysql/20261123_onboarding_account.sql을 백업 후 새 앱 시작 전에 적용합니다.
15번 이전의 역할·초대·라이브러리·파일·알림 스키마가 적용된 DB를 전제로 합니다.
기존 회원은 설문 미완료이며, 기존 팀 자료는 계정 삭제 요청 없이 자동 삭제되지 않습니다.
계정 삭제 API를 기존 개발·운영 계정에 이번 작업에서 실행하지 않습니다.

- 프론트파트와 설문 완료·내 정보·계정 전환·토큰 회전·로그아웃·삭제 확인을 연결합니다.
- AI파트와 벡터 삭제 API 배포·시크릿·반복 삭제·늦은 재저장 방지를 함께 검증합니다.
- 배포 담당자와 구형 JWT 재로그인 안내·마이그레이션·파일 보존·정리 작업 상태를 함께 점검합니다.

## 검증 결과 (2026-11-23)

- MySQL 8.4.11 전용 테스트 DB에서 전체 145개 테스트 통과(실패·오류·건너뜀 0), bootJar 빌드 성공.
- 설문 저장·수정·잘못된 선택 값, 내 정보·프로필 변경, refresh 회전·동시 갱신·현재 기기 로그아웃을 검증했습니다.
- 비밀번호 재확인·팀 소유자 삭제 차단·소유권 이전 후 삭제·모든 세션 무효화를 검증했습니다.
- 개인 자료 삭제·팀 공유 자료 보존 및 업로더 익명화·이미 떠난 팀의 개인 첨부 정리를 확인했습니다.
- 계정 삭제 트랜잭션 롤백 시 계정·세션·파일이 복구되고 AI 삭제가 실행되지 않음을 확인했습니다.
- AI 클라이언트는 모의 HTTP로 경로·인증 헤더·204 응답을 검증했고, 실패 작업의 DB 보존·재시도도 확인했습니다.
- 별도 스키마 DB에서 SQL 재실행·기존 회원·파일 보존·설문과 세션 FK 정리를 확인했습니다.
- 실제 AI 서버·프론트 연동과 기존 개발·운영 DB 적용은 수행하지 않았습니다.
