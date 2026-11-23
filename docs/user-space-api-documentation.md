# 지능형 북마크 웹 서비스
## 유저·스페이스 API 문서

> 유저 인증·프로필 및 개인/팀 스페이스 기능의 API 계약 문서입니다.
>
> 기준: 현재 프로젝트 코드, API 명세서, 확정된 권한 정책

---

## 1. 담당 범위

```text
유저
├── 회원가입
├── 로그인
└── 개인정보 수정

스페이스
├── 스페이스 생성
├── 스페이스 목록 조회
├── 스페이스 상세 조회
├── 스페이스 삭제
├── 팀원 초대
├── 팀원 목록 조회
└── 팀원 삭제
```

---

## 2. 공통 규칙

### Base URL

로컬 개발 환경:

```text
http://localhost:8080
```

### 인증 헤더

보호된 API는 로그인 후 발급받은 `accessToken`을 사용합니다.

```http
Authorization: Bearer {accessToken}
```

Swagger의 `Authorize` 입력창에는 프로젝트 설정상 `accessToken`만 입력합니다.

```text
올바른 입력: eyJhbGciOiJIUzI1NiJ9...
잘못된 입력: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 공통 응답 형식

```json
{
  "status": 200,
  "success": true,
  "message": "success message",
  "data": {}
}
```

### 주요 식별자

| 이름 | 의미 |
|---|---|
| `userId` | `User` 엔티티의 사용자 ID |
| `spaceId` | `Space` 엔티티의 스페이스 ID |
| `memberId` | `SpaceMember` 엔티티의 멤버십 ID |

> 팀원 삭제 API에는 `userId`가 아니라 `memberId`를 전달해야 합니다.

---

## 3. 권한 정책

| 기능 | OWNER | EDITOR | VIEWER |
|---|---|---|---|
| 참여 스페이스 목록·상세, 북마크 목록·상세 | 가능 | 가능 | 가능 |
| 북마크 저장·삭제 (같은 팀의 다른 저장자 자료 포함) | 가능 | 가능 | 불가 |
| AI 재분석·검색·추천, 태그 검색 | 가능 | 가능 | 불가 |
| 팀원 목록 | 가능 | 가능 | 불가 |
| 팀원 초대·제거·역할 변경 | 가능 | 불가 | 불가 |
| 스페이스 삭제 | 가능 | 불가 | 불가 |

- 역할은 스페이스마다 적용합니다. 다른 스페이스의 역할로 접근할 수 없습니다.
- 비로그인은 401, 해당 스페이스의 비멤버는 403입니다.
- 로그인 사용자의 스페이스 생성은 기존대로 가능합니다.
- `PERSONAL`에는 초대할 수 없고, OWNER의 제거·역할 변경은 지원하지 않습니다.
- EDITOR·VIEWER는 팀 스페이스에서 직접 탈퇴할 수 있습니다. OWNER는 소유권 이전 후 탈퇴합니다. 개인 스페이스는 탈퇴·이전 대신 OWNER가 삭제할 수 있습니다.
- 스페이스 삭제는 북마크·태그·멤버십을 MySQL에서 영구 삭제합니다. AI 벡터 삭제는 별도 연동 작업입니다.
- 댓글 작성·대화 검색 API는 아직 구현 전입니다. 라이브러리 API는 space-libraries.md를 참고합니다. 이후 작성에는 `requireWrite`, 검색·대화에는 `requireSearch`를 적용해야 합니다.

### 백엔드 공통 권한 서비스

`SpaceAccessService`는 내부 서비스이며, 사용자 ID는 인증 정보에서 전달합니다.

| 메서드 | 동작 |
|---|---|
| `getAccessibleSpaceIds(userId)` | VIEWER를 포함한 참여 스페이스 목록 |
| `getSearchableSpaceIds(userId)` | OWNER·EDITOR 스페이스만 반환 |
| `requireMember(userId, spaceId)` | 모든 참여 역할 허용, 비멤버 403 |
| `requireWrite(userId, spaceId)` | OWNER·EDITOR 허용, VIEWER 403 |
| `requireSearch(userId, spaceId)` | OWNER·EDITOR 허용, VIEWER 403 |
| `requireOwner(userId, spaceId)` | OWNER만 허용 |
| `resolveSearchSpaceIds(userId, spaceId)` | 미지정 시 검색 가능 목록, 지정 시 존재 여부와 검색 권한 확인 |

- 검색 스페이스가 없으면 404, 비멤버 또는 VIEWER이면 403입니다.
- 전체 검색에서 검색 가능한 공간이 없으면 빈 목록을 반환하고 AI를 호출하지 않습니다.
- 역할은 DB에서 조회하므로 역할 변경 후 다음 요청에 적용됩니다.
- AI 검색은 허용된 `space_ids`를 전달하며, 반환된 북마크도 허용 공간에 속하는지 확인합니다.
- AI 검색 요청의 선택 필드 `spaceId`, 태그 API의 선택 쿼리 `spaceId`로 특정 공간을 지정할 수 있습니다.

---

# 4. 유저 API

## 4.1 회원가입

```http
POST /api/auth/register
```

인증: 불필요

### Request Body

```json
{
  "email": "user@test.com",
  "password": "pw1234!",
  "name": "사용자"
}
```

### 성공 응답

```text
201 Created
```

```json
{
  "status": 201,
  "success": true,
  "message": "register success",
  "data": {
    "userId": 1,
    "email": "user@test.com"
  }
}
```

### 실패 응답

| 상황 | 상태 코드 |
|---|---:|
| 필수값 누락 또는 형식 오류 | 400 |
| 이미 등록된 이메일 | 409 |

비밀번호는 `PasswordEncoder`를 통해 암호화되어 저장되며 API 응답에 포함되지 않습니다.

---

## 4.2 로그인

```http
POST /api/auth/login
```

인증: 불필요

### Request Body

```json
{
  "email": "user@test.com",
  "password": "pw1234!"
}
```

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "login success",
  "data": {
    "accessToken": "JWT_ACCESS_TOKEN",
    "refreshToken": "JWT_REFRESH_TOKEN",
    "userId": 1,
    "nickname": "사용자"
  }
}
```

### 실패 응답

| 상황 | 상태 코드 |
|---|---:|
| 이메일 또는 비밀번호 불일치 | 401 |
| 필수값 누락 | 400 |

보호된 API에는 `accessToken`을 사용합니다.

---

## 4.3 개인정보 수정

```http
PUT /api/users/profile
```

인증: 필요

### Request Body

```json
{
  "name": "수정된 사용자",
  "profileImage": "https://example.com/profile.png"
}
```

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "profile updated successfully",
  "data": {
    "userId": 1,
    "nickname": "수정된 사용자",
    "updatedAt": "2026-08-30T18:20:31"
  }
}
```

`userId`는 Request Body로 받지 않으며, JWT의 현재 로그인 사용자 ID를 수정 대상으로 사용합니다.

---

# 5. 스페이스 API

## 5.1 스페이스 생성

```http
POST /api/spaces
```

인증: 필요

### Request Body

```json
{
  "spaceName": "캡스톤 프로젝트",
  "description": "팀 프로젝트 자료를 관리하는 공간",
  "type": "TEAM"
}
```

`type` 허용값:

```text
PERSONAL
TEAM
```

### 성공 응답

```text
201 Created
```

```json
{
  "status": 201,
  "success": true,
  "message": "space created successfully",
  "data": {
    "spaceId": 1,
    "spaceName": "캡스톤 프로젝트",
    "description": "팀 프로젝트 자료를 관리하는 공간",
    "type": "TEAM"
  }
}
```

생성자는 자동으로 `Space.owner`가 되며, `SpaceMember`에 `OWNER` 역할로 등록됩니다.

---

## 5.2 스페이스 목록 조회

```http
GET /api/spaces
```

인증: 필요

로그인한 사용자가 생성했거나 참여 중인 스페이스만 조회합니다.

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "space list retrieved successfully",
  "data": [
    {
      "spaceId": 1,
      "spaceName": "캡스톤 프로젝트",
      "type": "TEAM",
      "bookmarkCount": 0
    }
  ]
}
```

---

## 5.3 스페이스 상세 조회

```http
GET /api/spaces/{spaceId}
```

인증: 필요

권한: 해당 스페이스의 `OWNER`, `EDITOR`, `VIEWER`만 가능

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "space retrieved successfully",
  "data": {
    "spaceId": 1,
    "spaceName": "캡스톤 프로젝트",
    "description": "팀 프로젝트 자료를 관리하는 공간",
    "type": "TEAM",
    "ownerId": 1,
    "memberCount": 2,
    "bookmarkCount": 0,
    "createdAt": "2026-08-30T18:20:31"
  }
}
```

---

## 5.4 스페이스 삭제

```http
DELETE /api/spaces/{spaceId}
```

인증: 필요

권한: `OWNER`만 가능

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "space deleted successfully",
  "data": true
}
```

`EDITOR`, 비멤버 및 비로그인 사용자는 삭제할 수 없습니다.

삭제 범위: 해당 스페이스의 `bookmarks` 행(주소, 제목, 요약, 방문 정보 포함),
그 북마크의 `bookmark_tags` 행, `space_members` 행과 `spaces` 행을 함께 영구 삭제합니다.
사용자 계정과 다른 스페이스의 북마크·태그·멤버 정보는 유지됩니다.
삭제는 하나의 트랜잭션에서 처리하므로 DB 삭제 중 오류가 발생하면 전체 삭제가 롤백됩니다.

---

# 6. 팀원 API

## 6.1 팀원 초대

`POST /api/spaces/{spaceId}/invitations` (기존 `/invite`도 같은 동작)

OWNER가 이메일과 선택 역할(EDITOR 또는 VIEWER)을 지정해 초대를 발급합니다.
201 응답에는 memberId 대신 invitationId·invitationUrl·expiresAt·status·delivery가 포함됩니다.
가입 전 이메일도 초대할 수 있으며, 해당 이메일로 로그인한 사용자가 수락해야 멤버십이 생성됩니다.

수락: `POST /api/spaces/invitations/accept`, 본문 `{"token":"초대 토큰"}`.
발급·목록·취소·재발급·SMTP 설정·오류 코드의 상세 계약은 [초대 API 문서](space-invitations.md)를 참고합니다.
기본 EDITOR·72시간·이메일별 1회 수락은 회의 전 임시 기준입니다.

---
## 6.2 팀원 목록 조회

```http
GET /api/spaces/{spaceId}/members
```

인증: 필요

권한: 해당 스페이스의 `OWNER` 또는 `EDITOR`

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "member list retrieved successfully",
  "data": [
    {
      "memberId": 1,
      "userId": 1,
      "nickname": "owner",
      "email": "owner@test.com",
      "role": "OWNER"
    },
    {
      "memberId": 2,
      "userId": 2,
      "nickname": "member",
      "email": "member@test.com",
      "role": "EDITOR"
    }
  ]
}
```

---

## 6.3 팀원 삭제

```http
DELETE /api/spaces/{spaceId}/members/{memberId}
```

인증: 필요

권한: `OWNER`만 가능하며, 대상은 `EDITOR` 또는 `VIEWER`만 가능

### 성공 응답

```text
200 OK
```

```json
{
  "status": 200,
  "success": true,
  "message": "member removed successfully",
  "data": true
}
```

### 허용되는 경우

```text
요청자: OWNER
대상: EDITOR
→ 삭제 가능
```

### 차단되는 경우

```text
요청자: EDITOR
→ 삭제 불가

요청자: 비멤버
→ 삭제 불가

요청자: OWNER
대상: OWNER
→ 삭제 불가

요청자: EDITOR
대상: 자기 자신
→ 삭제 불가
```

### 주의

다음 응답을 사용하면 안 됩니다.

```http
DELETE /api/spaces/{spaceId}/members/{userId}
```

`memberId`는 반드시 `GET /api/spaces/{spaceId}/members` 응답의 `memberId`를 사용합니다.

---

# 7. 오류 상태 코드

| 상태 코드 | 의미 | 예시 |
|---:|---|---|
| 400 | 잘못된 요청 | 필수값 누락, 잘못된 type |
| 401 | 인증 실패 | 토큰 없음, 잘못된 토큰, 잘못된 로그인 |
| 403 | 권한 없음 | EDITOR의 삭제·초대, 비멤버 조회 |
| 404 | 리소스 없음 | 없는 spaceId, userId, memberId |
| 409 | 충돌 | 중복 이메일, 중복 멤버 초대 |
| 500 | 서버 오류 | 처리되지 않은 예외 |

공통 오류 응답 예시:

```json
{
  "status": 403,
  "success": false,
  "message": "only owner can remove members"
}
```

---

# 8. Swagger 통합 테스트 순서

1. `POST /api/auth/register`로 OWNER 계정을 생성합니다.
2. EDITOR와 비멤버 테스트 계정을 생성합니다.
3. `POST /api/auth/login`으로 OWNER 로그인합니다.
4. 응답의 `accessToken`을 복사합니다.
5. Swagger `Authorize`에 토큰만 입력합니다.
6. `POST /api/spaces`로 TEAM 스페이스를 생성합니다.
7. 응답의 `spaceId`를 기록합니다.
8. `POST /api/spaces/{spaceId}/invitations`로 초대를 발급한 뒤, 수신 계정으로 로그인해 `/api/spaces/invitations/accept`에서 수락합니다.
9. `GET /api/spaces/{spaceId}/members`로 `memberId`를 확인합니다.
10. EDITOR 계정으로 로그인 후 상세·목록 조회를 확인합니다.
11. EDITOR 권한으로 삭제·초대 요청이 차단되는지 확인합니다.
12. OWNER 권한으로 EDITOR 삭제를 확인합니다.
13. OWNER 삭제 시도와 존재하지 않는 `memberId`를 확인합니다.
14. Swagger에서 Logout 후 토큰 없는 요청을 확인합니다.

> `memberId`와 `userId`를 혼동하지 않도록 주의합니다.

---

# 9. 프론트엔드·AI 협업 체크리스트

## 프론트엔드 전달 사항

- 보호 API에는 `Authorization: Bearer {accessToken}`이 필요합니다.
- Swagger Authorize에는 accessToken만 입력합니다.
- 로그인 사용자 ID는 Request Body로 임의 전달하지 않습니다.
- `spaceId`, `userId`, `memberId`의 의미를 구분합니다.
- OWNER·EDITOR·VIEWER의 권한이 다릅니다.
- PERSONAL 스페이스에는 팀원을 초대할 수 없습니다.
- 오류 응답은 `status`, `success`, `message` 형식으로 처리합니다.

## AI·북마크 담당자 협의 사항

- AI 요청에서 `userId`와 `spaceId`를 어떻게 전달할지 결정합니다.
- 북마크 조회는 모든 참여 역할, 저장·삭제와 AI 검색은 OWNER·EDITOR만 가능합니다.
- 스페이스 삭제 시 해당 북마크와 태그가 DB에서 영구 삭제됨을 공유합니다.
- 요약 결과가 개인 데이터인지 팀 스페이스 데이터인지 결정합니다.

---

## 10. 팀원 역할 변경

`PATCH /api/spaces/{spaceId}/members/{memberId}/role`

OWNER만 호출할 수 있고, 대상 역할은 EDITOR 또는 VIEWER입니다.

```json
{"role":"VIEWER"}
```

성공 시 200과 변경된 `MemberDto`를 반환합니다. 비소유자·OWNER 대상 변경은 403,
OWNER 역할 지정·구형 MEMBER·잘못된 역할·필드 누락은 400, 해당 공간에 없는 멤버는 404입니다.
`memberId`는 사용자 ID가 아닌 멤버십 ID입니다.

초대는 수락 후 멤버십을 생성합니다. 발송 상태와 수락·취소·재발급은 space-invitations.md를 참고합니다.

## 11. 기존 DB 역할 전환

기존 MEMBER 데이터가 있는 DB에는 `scripts/mysql/20261122_space_roles.sql`을 수동 적용합니다.
백업 후 구버전 앱의 쓰기를 중단하고, 새 앱 시작(Hibernate 스키마 변경) 전에 실행합니다.
SQL은 임시로 구형·신형 역할을 모두 허용하고 MEMBER를 EDITOR로 변환한 뒤 구형 역할을 제거합니다.
MySQL DDL은 자동 커밋되며 이 스크립트는 자동 실행되지 않습니다.
실제 운영 DB에는 이번 작업에서 실행하지 않았습니다.

## 12. MySQL 역할 검증 (2026-11-22)

- MySQL 8.4.11의 별도 테스트 DB에서 기존 OWNER 1건과 MEMBER 2건을 넣어 전환 SQL을 검증했습니다.
- OWNER와 원본 ID가 유지되고 MEMBER가 EDITOR로 변환됐으며, VIEWER 저장과 SQL 재실행도 통과했습니다.
- 별도 권한 테스트 DB에서 전체 83개 테스트가 통과했습니다. 역할별 조회·쓰기·AI 검색 제한, 역할 변경, 다른 공간 검색 결과 제외를 포함합니다.
- 권한 API 검증은 MockMvc와 테스트 인증을 사용했으며, 실제 AI 서버 대신 모의 응답을 사용했습니다.
- 운영 DB 전환과 배포 환경의 실제 토큰·AI 연동 검증은 별도입니다.

## 13. 팀원 탈퇴 및 소유권 이전

### 자기 탈퇴

`DELETE /api/spaces/{spaceId}/members/me`

- TEAM의 EDITOR·VIEWER가 직접 탈퇴합니다. 성공은 200, data는 true입니다.
- 팀에 저장한 북마크와 다른 팀원의 데이터는 유지됩니다.
- 탈퇴 후 해당 공간의 자료 조회·검색은 허용되지 않습니다.
- OWNER는 먼저 소유권을 이전해야 합니다(직접 탈퇴 403).
- 개인 스페이스의 탈퇴는 불가하지만, 기존 OWNER의 공간 삭제는 가능합니다.

### 소유권 이전

`PATCH /api/spaces/{spaceId}/owner`

```json
{"memberId":123}
```

- 현재 OWNER만 같은 팀의 EDITOR·VIEWER에게 이전할 수 있습니다.
- 새 소유자는 OWNER, 기존 소유자는 EDITOR가 됩니다. 성공은 200과 신규 OWNER의 MemberDto입니다.
- spaces.owner_id와 두 멤버의 역할을 한 트랜잭션에서 변경합니다.
- 자기 자신에게 이전하거나 누락·0·음수 memberId를 보내면 400입니다.
- 비멤버·비소유자·개인 공간 이전은 403, 없는 공간·다른 공간 또는 없는 대상은 404입니다.
- 일반 역할 변경 API로 OWNER를 지정할 수 없다는 정책은 유지합니다.

### 동시 변경

초대·제거·역할 변경·탈퇴·소유권 이전·공간 삭제는 동일한 스페이스 행의 쓰기 잠금을 먼저 획득합니다.
권한 검사는 잠금 획득 후 수행하여 이전 OWNER가 동시에 관리 작업을 계속하는 상황을 방지합니다.

### 협업 항목

- 프론트파트와 탈퇴 확인·소유권 이전 대상 선택·성공 후 화면 이동 및 현재 역할 갱신을 함께 진행합니다.
- 다른 백엔드 담당자도 멤버십·소유권 변경 시 동일한 잠금 순서를 사용해야 합니다.
- 2번의 폴더·초대·공유·AI 세부 명세는 docs/team-policy-proposal.md에 제안으로 구분했으며 팀 협의가 필요합니다.

### 검증 결과 (2026-11-22)

- MySQL 8.4.11 전용 테스트 DB에서 전체 91개 테스트 통과, 실패·오류·건너뜀 0건.
- 동시 소유권 이전 두 요청 중 하나만 성공하고, OWNER 멤버십과 spaces.owner_id가 일치함을 확인했습니다.
- 탈퇴 후 자료 접근 차단 및 팀 자료 보존, 이전 후 권한 변경, 개인 스페이스의 탈퇴·이전 차단 및 삭제 허용을 확인했습니다.
- H2 기반 전체 기능 테스트 및 bootJar 빌드도 통과했습니다. API 테스트는 테스트 인증을 사용합니다.
- 기존 서비스 DB에는 변경을 적용하지 않았습니다. 이번 기능은 추가 스키마 변경이 없습니다.


## 14. 라이브러리 기능

라이브러리는 스페이스 아래 1단계이며, 북마크의 폴더 선택은 선택 사항입니다.
폴더 삭제는 자료를 지우지 않고 연결만 해제합니다. OWNER·EDITOR는 생성·수정·삭제·검색 가능하며 VIEWER는 조회만 가능합니다.
API·필터·동시성·DB 전환은 [라이브러리 문서](space-libraries.md)를 참고합니다.


## 15. 로컬 파일 저장·첨부

PDF·PNG·JPEG(최대 10 MiB)를 로컬 비공개 경로에 저장합니다.
파일 북마크는 type=FILE·fileId·analysisStatus=NOT_SUPPORTED로 표시하고, 기존 링크는 type=LINK를 유지합니다.
대화 첨부는 업로더 본인에게만 공개하며 실제 메시지 연결은 별도입니다.
권한·다운로드·삭제 재시도·DB 전환은 [파일 API 문서](file-attachments.md)를 참고합니다.


## 16. 앱 내 알림

초대·수락·멤버 변경 및 로그인 복귀 알림을 연결했습니다. 댓글은 담당 서비스의 이벤트 연결이 필요합니다.
내 알림 목록·읽음·미읽음 개수와 현재 권한에 따른 이동 대상은 [알림 API 문서](notifications.md)를 참고합니다.
로그인 성공 시 마지막 시각을 기록하지만, 설문·로그아웃·계정 삭제 등 15번 기능은 별도 작업입니다.
