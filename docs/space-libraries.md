# 라이브러리 API 및 데이터 정책

## 확정 정책

- 라이브러리는 개인·팀 스페이스 바로 아래의 1단계 폴더입니다. 하위 폴더는 지원하지 않습니다.
- 북마크는 스페이스에 반드시 속하며, 같은 공간의 라이브러리 하나에 선택적으로 속합니다.
- 선택하지 않은 북마크는 libraryId=null인 미분류 자료입니다. 가짜 미분류 폴더를 생성하지 않습니다.
- 라이브러리를 삭제하면 북마크의 연결만 해제합니다. 북마크·태그·요약·분석 상태·방문 기록·저장 시각은 유지합니다.
- OWNER·EDITOR는 라이브러리 생성·이름 변경·삭제·북마크 이동을 할 수 있습니다.
- VIEWER는 목록·상세·북마크 목록 조회만 가능합니다. 이름 검색·자료 변경은 403입니다.
- 다른 공간의 라이브러리로 연결하거나 공간을 넘겨 이동하는 기능은 지원하지 않습니다.
- 이름은 1~100자이며 앞뒤 공백을 제거합니다. 같은 공간의 대소문자만 다른 이름은 중복으로 처리합니다.
- 폴더 삭제·이름 변경·같은 공간 내 이동은 AI 재분석이나 벡터 삭제를 호출하지 않습니다.

## API 계약

| 기능 | 요청 | 응답 |
|---|---|---|
| 생성 | POST /api/spaces/{spaceId}/libraries | 201, LibraryResponse |
| 목록·이름 검색 | GET /api/spaces/{spaceId}/libraries?query=검색어 | 200, LibraryResponse 배열 |
| 상세 | GET /api/spaces/{spaceId}/libraries/{libraryId} | 200, LibraryResponse |
| 이름 변경 | PATCH /api/spaces/{spaceId}/libraries/{libraryId} | 200, LibraryResponse |
| 삭제 | DELETE /api/spaces/{spaceId}/libraries/{libraryId} | 200, true |
| 폴더의 북마크 | GET /api/spaces/{spaceId}/libraries/{libraryId}/bookmarks | 200, BookmarkListResponse |
| 폴더 연결 변경 | PATCH /api/bookmarks/{bookmarkId}/library | 200, BookmarkSummaryDto |

생성·이름 변경 본문: `{"name":"UX/UI 스터디"}`.
LibraryResponse: libraryId, spaceId, name, bookmarkCount, createdAt.
목록의 개수는 해당 폴더에 직접 속한 북마크 수이며, 스페이스 전체 개수와 구분합니다.
목록은 생성 ID 오름차순이며 이름 검색은 부분 일치입니다. 빈 검색어는 일반 목록 조회입니다.

폴더 북마크 목록은 page=0, size=20, sort=recent가 기본입니다.
size는 1~100, page는 0 이상, sort는 recent·oldest·title만 허용하며 동률은 ID로 정렬합니다.
기존 북마크·태그 목록과 상세에 libraryId를 추가했습니다.

북마크 저장 POST /api/bookmarks에는 선택 필드 libraryId를 추가했습니다.
생략 또는 null은 기존처럼 미분류 저장입니다. 기존 클라이언트 요청은 유지됩니다.
폴더 변경은 `{"libraryId":123}`, 미분류로 옮기기는 `{"libraryId":null}`이며 필드 누락은 400입니다.
이 작업은 bookmark.space_id와 created_at을 바꾸지 않습니다.

기존 GET /api/bookmarks?spaceId=...는 전체 북마크를 반환합니다.
선택 조건 libraryId=123은 특정 폴더, unclassified=true는 미분류만 조회합니다.
두 조건을 동시에 지정하면 400입니다. 특정 폴더 목록 조회는 검색 권한을 요구하지 않습니다.

오류: 인증 없음 401, 권한 없음 403, 공간·라이브러리·북마크 없음 404,
다른 공간 라이브러리 지정 404, 이름 중복 409, 잘못된 이름·페이지·정렬·양수가 아닌 라이브러리 ID는 400입니다.

## 동시 변경·DB 적용

라이브러리 쓰기·북마크 저장·폴더 이동·삭제는 해당 스페이스의 쓰기 잠금을 먼저 획득합니다.
폴더 삭제와 동시에 저장하더라도 삭제된 폴더를 참조하는 북마크가 남지 않도록 처리합니다.
DB 외래키도 ON DELETE SET NULL로 설정합니다. 일반 북마크 갱신은 변경된 필드만 갱신합니다.
스페이스 전체 삭제 시에는 라이브러리와 북마크 모두 함께 삭제합니다.

배포 전 백업 후 scripts/mysql/20261123_libraries.sql을 적용합니다. 자동 실행되지 않습니다.
기존 데이터는 library_id=NULL로 남습니다. DDL은 자동 커밋되며 수동 되돌리기가 필요합니다.
기존 개발·운영 DB에는 이번 작업에서 적용하지 않습니다.

## 파트 간 연결

- 프론트파트와 폴더 이름 대신 libraryId로 저장·선택하고, 미분류 목록·삭제 안내·개수 갱신을 함께 진행합니다.
- 북마크 담당자와 library_id 연결·같은 공간 내 이동·다중 이동 확장 시 잠금 순서를 함께 적용합니다.
- AI파트는 현재 space_ids 기준을 유지합니다. 라이브러리 내 AI 검색은 이번에 구현하지 않습니다.
  추가하려면 library_id 메타데이터·필터·이동 동기화 규격을 별도로 합의해야 합니다.

## 검증 (2026-11-23)

- 전체 108개 테스트 통과(실패·오류·건너뜀 0), bootJar 빌드 성공.

- MySQL 8.4.11 전용 테스트 DB에서 라이브러리 권한·검색·이름 중복·다른 공간 연결 차단을 확인했습니다.
- 폴더 삭제 후 원본 북마크·태그·요약·분석 상태·방문 정보·최초 저장 시각 보존을 확인했습니다.
- 폴더 삭제와 북마크 저장을 동시에 요청해 잘못된 폴더 참조가 남지 않음을 확인했습니다.
- 별도 마이그레이션 DB에서 기존 북마크 보존·스크립트 재실행·ON DELETE SET NULL을 검증했습니다.
- API 테스트는 테스트 인증과 모의 AI 서버를 사용합니다. 실제 프론트·AI 서버 연동은 포함하지 않습니다.
