# 지능형 북마크 웹 서비스
## 북마크·댓글·AI·대화 검색 API 문서

> 북마크, 댓글, 태그, AI 검색·추천, 대화 검색 기능의 API 계약 문서입니다.
>
> 기준: 현재 프로젝트 코드. 아직 머지되지 않은 PR의 변경은 해당 절에 `PR #번호`로 표시했습니다.
> 공통 규칙(Base URL, 인증 헤더, 공통 응답 형식)은 `user-space-api-documentation.md`와 같습니다.

---

## 1. 담당 범위

```text
북마크
├── 사이트 저장 (확장 프로그램: 탭 제목 전달)
├── 목록 조회 (정렬·페이지)
├── 최근 저장 목록
├── 상세 조회
├── 삭제 / 여러 개 삭제
├── 이동
├── 공유(복사)              PR #14
├── 저장 여부 확인 (확장 프로그램)
└── 분석 상태 확인           PR #13

댓글
├── 목록 / 작성 / 수정 / 삭제

AI
├── 재분석 요청
├── 의미 기반 검색           (PR #12에서 응답 형식 변경)
└── 유사 사이트 추천

태그
├── 태그 목록
└── 태그별 사이트 목록

대화 검색
├── 새 대화 시작 / 이어가기
├── 보관함 목록·검색
├── 지난 대화 열기
└── 대화 삭제
```

---

## 2. 권한 정책

| 대상 | 규칙 |
|---|---|
| 북마크 조회·저장·삭제·이동 | 북마크가 속한 스페이스의 멤버만 가능. 아니면 403 |
| 여러 개 삭제·이동·공유 | 하나라도 없으면 404, 권한이 없으면 403이며 **아무것도 처리하지 않음** |
| 이동·공유 | 원래 스페이스와 대상 스페이스 **모두**의 멤버여야 함 |
| 댓글 조회·작성 | 북마크가 속한 스페이스의 멤버 |
| 댓글 수정·삭제 | 작성자 본인만. 응답의 `mine`으로 버튼 표시 여부 판단 |
| 검색·추천·태그 | 내가 속한 스페이스(개인 + 소속 팀)의 북마크만 |
| 대화 | 본인 대화만. 다른 사람의 대화는 404 |

---

## 3. 공통 객체

### 북마크 카드 (`BookmarkSummary`)

목록, 최근 저장, 태그별 목록에서 사용합니다.

```json
{
  "bookmarkId": 15,
  "title": "React 19 vs 18 비교",
  "url": "https://example.com/react-19-vs-18",
  "summary": "React 19의 주요 변경점을 ...",
  "visitCount": 12,
  "lastVisitedAt": "2026-11-20T21:10:33",
  "category": "개발/기술",
  "analysisStatus": "SUCCESS",
  "tags": ["React", "Frontend"],
  "spaceId": 3,
  "spaceName": "프론트엔드 스터디",
  "savedBy": { "userId": 7, "name": "홍길동", "profileImage": null },
  "createdAt": "2026-11-18T10:15:30",
  "commentCount": 2,
  "latestComment": { "authorName": "김철수", "content": "정리 잘 되어 있네요", "createdAt": "2026-11-19T09:00:00" }
}
```

| 필드 | 설명 |
|---|---|
| `analysisStatus` | `PENDING`: 분석 중, `SUCCESS`: 완료, `FAILED`: 실패 |
| `summary`, `category` | 분석 전에는 `null` |
| `savedBy` | 저장자를 알 수 없거나(이전 데이터) 탈퇴한 경우 `null` |
| `latestComment` | 댓글이 없으면 `null`. `content`는 최대 50자, 넘으면 `…` |
| `lastVisitedAt` | 방문 전이면 `null` |

---

# 4. 북마크 API

## 4.1 사이트 저장

```http
POST /api/bookmarks
```

```json
{ "url": "https://spring.io/guides", "spaceId": 1, "title": "Spring | Guides" }
```

- `title`은 선택입니다. 브라우저 확장 프로그램이 탭 제목을 보낼 때 씁니다. 생략하면 URL 호스트(`spring.io`)가 제목이 됩니다.
- AI 분석이 끝나면 제목이 AI가 추출한 제목으로 바뀔 수 있습니다.

**202**

```json
{ "bookmarkId": 15, "title": "Spring | Guides", "analysisStatus": "PENDING", "createdAt": "2026-11-23T15:20:00" }
```

- 분석은 비동기로 진행됩니다. 완료 여부는 4.9 분석 상태 확인으로 확인합니다.

| 실패 | 상황 |
|---|---|
| 400 | URL 형식 오류, 제목 255자 초과 |
| 403 | 스페이스 멤버가 아님 |
| 404 | 스페이스 없음 |

## 4.2 목록 조회

```http
GET /api/bookmarks?spaceId=1&page=0&size=20&sort=latest
```

| 파라미터 | 설명 |
|---|---|
| `size` | 1~100, 기본 20 |
| `sort` | `latest`(기본, 최신 저장순), `oldest`, `most-visited`, `recently-visited`, `title` |

**200**

```json
{ "content": [ /* 북마크 카드 */ ], "page": 0, "size": 20, "totalElements": 52, "totalPages": 3 }
```

- 잘못된 `sort`나 `size`, 숫자가 아닌 `page`는 400입니다.

## 4.3 최근 저장 목록

```http
GET /api/bookmarks/recent?size=10
```

- 내가 속한 **모든 스페이스**에서 최근 저장된 순서로 가져옵니다. `size`는 1~50, 기본 10입니다.
- 응답 `data`는 북마크 카드 배열입니다.

## 4.4 상세 조회

```http
GET /api/bookmarks/{bookmarkId}
```

- 조회할 때마다 방문 수가 1 늘어납니다.
- 응답 필드: 카드 필드 중 `bookmarkId`, `title`, `url`, `summary`, `visitCount`, `createdAt`, `lastVisitedAt`, `category`, `analysisStatus`, `tags`, `spaceId`, `spaceName`, `savedBy`와 `commentCount`

## 4.5 삭제

```http
DELETE /api/bookmarks/{bookmarkId}
```

- 댓글도 함께 삭제됩니다.

## 4.6 여러 개 삭제

```http
POST /api/bookmarks/bulk-delete
```

```json
{ "bookmarkIds": [15, 16, 17] }
```

**200** `{ "bookmarkIds": [15, 16, 17], "count": 3 }`

- 최대 100개입니다. 빈 목록이면 400입니다.

## 4.7 이동

```http
PATCH /api/bookmarks/move
```

```json
{ "bookmarkIds": [15, 16], "targetSpaceId": 4 }
```

**200** `{ "bookmarkIds": [15], "count": 1 }`

- 이미 대상 스페이스에 있는 북마크는 건너뛰고, 실제로 옮긴 것만 돌려줍니다.

## 4.8 공유(다른 스페이스로 복사) — PR #14

```http
POST /api/bookmarks/share
```

```json
{ "bookmarkIds": [15, 16, 17], "targetSpaceId": 4 }
```

**201**

```json
{
  "shared": [ { "sourceBookmarkId": 15, "bookmarkId": 52 }, { "sourceBookmarkId": 16, "bookmarkId": 53 } ],
  "skippedBookmarkIds": [17]
}
```

- 원본은 그대로 남습니다.
- 대상 스페이스에 같은 URL이 있으면 건너뛰고 `skippedBookmarkIds`에 담습니다.
- 복사본은 원본의 요약·태그를 바로 보여주지만, 대상 스페이스용 분석을 새로 하므로 상태는 `PENDING`입니다.

## 4.9 분석 상태 확인 — PR #13

```http
GET /api/bookmarks/analysis-status?ids=15,16,17
```

**200**

```json
[
  { "bookmarkId": 15, "analysisStatus": "SUCCESS", "title": "React 19 vs 18 비교",
    "summary": "...", "category": "개발/기술", "tags": ["React"] },
  { "bookmarkId": 16, "analysisStatus": "PENDING", "title": "example.com",
    "summary": null, "category": null, "tags": [] }
]
```

- **화면 처리**: `PENDING` 카드가 있는 동안 3초 간격으로 호출하고, 모두 `SUCCESS`·`FAILED`가 되면 멈춥니다. 서버는 10분 동안 결과가 없으면 `FAILED`로 바꿉니다.
- 응답에서 빠진 ID는 삭제됐거나 볼 수 없는 북마크입니다.
- 한 번에 최대 50개입니다.

## 4.10 저장 여부 확인 (확장 프로그램)

```http
GET /api/bookmarks/lookup?url=https://spring.io/guides
```

**200**

```json
[ { "bookmarkId": 15, "title": "가이드", "spaceId": 1, "spaceName": "개인", "createdAt": "2026-11-20T10:00:00" } ]
```

- 내가 속한 스페이스에서 같은 URL을 찾습니다. URL 끝의 `/` 유무는 같은 주소로 봅니다.
- 저장된 적이 없으면 빈 배열입니다.

---

# 5. 댓글 API

## 5.1 목록

```http
GET /api/bookmarks/{bookmarkId}/comments?page=0&size=20
```

**200**

```json
{
  "content": [
    {
      "commentId": 41,
      "bookmarkId": 15,
      "author": { "userId": 7, "name": "홍길동", "profileImage": null },
      "content": "정리 잘 되어 있네요",
      "createdAt": "2026-11-23T15:20:00",
      "editedAt": null,
      "mine": true
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

- 작성 순(오래된 것부터)입니다. `size`는 1~100입니다.
- `author`는 탈퇴한 사용자면 `null`, `editedAt`은 수정한 적이 없으면 `null`입니다.

## 5.2 작성

```http
POST /api/bookmarks/{bookmarkId}/comments
```

```json
{ "content": "정리 잘 되어 있네요" }
```

- **201**, 응답은 댓글 객체 하나입니다.
- 내용은 앞뒤 공백을 지우고 저장하며, 1~1000자여야 합니다(아니면 400).

## 5.3 수정 / 5.4 삭제

```http
PATCH  /api/bookmarks/{bookmarkId}/comments/{commentId}    { "content": "..." }
DELETE /api/bookmarks/{bookmarkId}/comments/{commentId}
```

- 작성자가 아니면 403, 해당 북마크의 댓글이 아니면 404입니다.

---

# 6. AI API

## 6.1 재분석 요청

```http
POST /api/ai/summary
```

```json
{ "bookmarkId": 15 }
```

**202** `{ "bookmarkId": 15, "analysisStatus": "PENDING" }`

- `FAILED` 카드의 "다시 분석" 버튼에 씁니다.
- (PR #13) 이미 분석 중이면 새로 요청하지 않고 현재 상태를 돌려줍니다.

## 6.2 의미 기반 검색

```http
POST /api/ai/search
```

```json
{ "query": "리액트 버전 비교", "spaceId": 3 }
```

- `spaceId`를 생략하면 내가 속한 모든 스페이스에서 검색합니다. 권한 없는 스페이스는 403, 없는 스페이스는 404입니다.
- 현재 응답 `data`는 결과 배열입니다.

```json
[ { "bookmarkId": 15, "title": "...", "url": "...", "summary": "...", "similarity": 0.94 } ]
```

**PR #12 머지 후 변경**

```json
// 요청: savedFrom·savedTo(날짜, 양 끝 포함), limit(1~50, 기본 10) 추가. 모두 선택
{ "query": "리액트 버전 비교", "spaceId": 3, "savedFrom": "2026-11-01", "savedTo": "2026-11-23", "limit": 10 }

// 응답 data: 배열 → 객체
{ "count": 1, "results": [ { "bookmarkId": 15, "title": "...", "url": "...", "summary": "...", "similarity": 0.94 } ] }
```

- 화면의 "검색결과 N건"에는 `count`를 씁니다.
- 시작일이 종료일보다 늦거나 `limit`이 범위를 벗어나면 400입니다.

## 6.3 유사 사이트 추천

```http
GET /api/ai/recommend?bookmarkId=15
```

- 내가 속한 스페이스에서 비슷한 사이트를 최대 5개 추천합니다. 자기 자신은 빠집니다.
- 분석이 끝나지 않은 북마크는 빈 배열입니다.

| 공통 실패 | 상황 |
|---|---|
| 502 | AI 서버 오류 |

---

# 7. 태그 API

```http
GET /api/tags                                       → [ { "tagName": "React", "bookmarkCount": 3 } ]
GET /api/tags/{tagName}/bookmarks?page=0&size=20    → { "tagName", "content": [카드], "page", "size", "totalElements", "totalPages" }
```

- 내가 속한 스페이스의 북마크만 집계합니다.
- 내가 볼 수 있는 북마크에 없는 태그를 조회하면 404입니다.

---

# 8. 대화 검색 API

## 8.1 새 대화 시작

```http
POST /api/conversations
```

```json
{ "message": "지난주에 저장한 리액트 버전 비교 글 찾아줘", "spaceId": null }
```

**201**

```json
{
  "conversationId": 12,
  "title": "지난주에 저장한 리액트 버전 비교 글 찾아줘",
  "question": { "messageId": 101, "role": "USER", "content": "...", "clarification": false, "cards": [], "createdAt": "..." },
  "answer": {
    "messageId": 102,
    "role": "ASSISTANT",
    "content": "이 글을 찾으시나요?",
    "clarification": false,
    "cards": [ { "bookmarkId": 15, "title": "...", "url": "...", "summary": "...", "spaceId": 3, "spaceName": "..." } ],
    "createdAt": "..."
  }
}
```

- `clarification: true`면 AI가 되물은 것입니다. `content`의 질문을 보여주고 답을 이어서 보내면 됩니다.
- 메시지는 1~2000자입니다. `spaceId`의 규칙은 검색(6.2)과 같습니다.
- **502**(AI 서버 오류)면 질문이 저장되지 않습니다. 같은 질문으로 다시 보내면 됩니다.
- 속한 스페이스가 없으면 AI를 호출하지 않고 안내 문구를 돌려줍니다.

## 8.2 대화 이어가기

```http
POST /api/conversations/{conversationId}/messages
```

- 요청·응답 형식은 8.1과 같고, 성공은 **200**입니다.

## 8.3 보관함 목록·검색

```http
GET /api/conversations?keyword=리액트&page=0&size=20
```

```json
{
  "content": [
    { "conversationId": 12, "title": "...", "lastMessagePreview": "이 글을 찾으시나요?",
      "lastMessageAt": "2026-11-23T15:20:00", "createdAt": "2026-11-23T15:19:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

- 최근 대화순입니다. `keyword`가 있으면 제목과 메시지 내용에서 찾습니다. `size`는 1~50입니다.

## 8.4 지난 대화 열기 / 8.5 삭제

```http
GET    /api/conversations/{conversationId}   → { "conversationId", "title", "createdAt", "messages": [메시지] }
DELETE /api/conversations/{conversationId}
```

- 링크 카드는 **열 때마다 현재 권한으로 다시 확인**합니다. 팀에서 나갔거나 삭제된 북마크는 카드에서 빠집니다.

---

## 9. 실패 응답 정리

| 코드 | 상황 |
|---|---|
| 400 | 필수 값 누락, 형식 오류, 범위 초과(페이지 크기, 개수 제한, 날짜 순서) |
| 401 | 로그인 필요(토큰 없음·만료·리프레시 토큰 사용) |
| 403 | 스페이스 권한 없음, 남의 댓글 수정·삭제 |
| 404 | 북마크·스페이스·댓글·대화·태그 없음, 없는 경로 |
| 502 | AI 서버 오류 |
