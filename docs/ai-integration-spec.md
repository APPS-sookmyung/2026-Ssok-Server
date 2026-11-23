# Spring ↔ AI 서버 연동 규격

AI 파트와 협의한 안(홍예빈 님 의견 반영)에 따라 Spring 쪽 구현을 마친 규격이다.
모든 본문은 snake_case JSON.

## 1. 분석 요청 (Spring → AI) `POST /api/v1/ai/analyze`

최초 저장과 재분석 모두 같은 형식.

```json
{
  "bookmark_id": 123,
  "url": "https://example.com/react-19-vs-18",
  "space_id": 7,
  "saved_at": "2026-11-23T10:15:30.120+09:00",
  "callback_url": "https://{spring}/api/v1/bookmarks/ai-result",
  "analysis_request_id": "4f1c2b0e-8a1d-4c55-9a77-2f0c1e6b9d3a"
}
```

- `saved_at`: 최초 저장 시각. 재분석 때도 바뀌지 않는다. `YYYY-MM-DDTHH:mm:ss.sss±HH:mm`, UTC도 `+00:00`.
- `analysis_request_id`: 요청마다 새로 발급. 콜백에 그대로 돌려줘야 한다.

## 2. 분석 결과 콜백 (AI → Spring) `POST /api/v1/bookmarks/ai-result`

헤더 `X-AI-Callback-Secret: {공유 시크릿}`. AI는 배포 설정에 등록된 Spring 주소로만 시크릿을 보낸다.

```json
{
  "bookmark_id": 123,
  "analysis_request_id": "4f1c2b0e-...",
  "status": "SUCCESS",
  "analysis": { "title": "...", "summary": "...", "category": "개발/기술", "tags": ["React"] }
}
```

실패 시 `{"bookmark_id": 123, "analysis_request_id": "...", "status": "FAILED"}`.

| 상황 | 응답 | AI 처리 |
|---|---|---|
| 반영 | 200 | 끝 |
| 삭제된 북마크 / 이전 요청 / 중복 | 200 (무시, message에 사유) | 끝 |
| 필수 필드 누락 | 400 | 형식 수정 |
| 시크릿 불일치 | 401 | 설정 확인 |
| 5xx·네트워크 오류 | — | 재시도 |

- `title`이 비어 있으면 기존 제목 유지.
- 10분 동안 콜백이 없으면 Spring이 `FAILED`로 바꾼다. 늦게 온 성공 결과도 반영한다.
- 분석 중 북마크가 다른 스페이스로 옮겨졌다면 결과 반영 후 Spring이 4번(스페이스 갱신)을 호출한다.

## 3. 검색 (Spring → AI) `POST /api/v1/ai/search`

```json
{ "query": "그 리액트 버전 비교하는 글", "top_k": 5, "space_ids": [1, 3] }
```

응답 `{ "query": "...", "results": [{ "bookmark_id": 123, "similarity": 0.89 }] }`.
`space_ids` 안에서만 검색한다. Spring은 빈 배열을 보내지 않으며, 결과를 받은 뒤 권한을 다시 확인한다.

## 4. 벡터 삭제·스페이스 갱신 (Spring → AI)

| 요청 | 본문 | 응답 |
|---|---|---|
| `DELETE /api/v1/ai/bookmarks/{bookmark_id}` | 없음 | 204 (반복 호출에도 204, 404도 성공으로 처리) |
| `PATCH /api/v1/ai/bookmarks/{bookmark_id}` | `{"space_id": 9}` | 204 (벡터가 아직 없으면 404 가능, 성공으로 처리) |

- Spring은 DB 변경이 커밋된 뒤 호출하고, 실패하면 1분·2분·4분… 최대 1시간 간격으로 계속 재시도한다.
- 같은 북마크에 갱신과 삭제가 함께 쌓이면 삭제만 보낸다.
- AI는 삭제된 `bookmark_id`에 대해 늦게 끝난 분석이 벡터를 다시 저장하지 않도록 막는다.

## 5. 대화 검색 (Spring → AI) `POST /api/v1/ai/chat`

```json
{
  "messages": [
    { "role": "user", "content": "그 글" },
    { "role": "assistant", "content": "어떤 주제인가요?" },
    { "role": "user", "content": "리액트" }
  ],
  "space_ids": [1, 3],
  "top_k": 5
}
```

응답:

```json
{
  "reply": "이 글을 찾으시나요?",
  "needs_clarification": false,
  "results": [{ "bookmark_id": 123, "similarity": 0.9 }]
}
```

- `messages`: 최근 최대 10개, 오래된 것부터, 마지막이 이번 질문.
- 질문이 모호하면 `needs_clarification: true`와 되묻는 질문을 `reply`에 담는다.
- Spring이 대화 기록·권한·링크 카드 조립을 맡는다. 실패(5xx) 시 사용자에게 502, 질문은 저장하지 않는다.

## 배포 순서

Spring은 `analysis_request_id`가 없는 콜백을 400으로 거절하므로 AI 쪽 변경과 함께 배포한다.
