# 앱 내 알림 API

## 확정 정책

앱 내 목록 조회 방식이며 이메일·웹푸시·SSE·WebSocket 발송은 하지 않습니다.
알림 생성은 원래 작업과 같은 트랜잭션에서 수행하므로 원래 작업이 롤백되면 알림도 남지 않습니다.

| 종류 | 수신 대상 | 현재 연결 |
|---|---|---|
| INVITATION_RECEIVED | 초대 이메일에 해당하는 기존 계정 | 초대 발급·재발급 |
| INVITATION_ACCEPTED | 수락 당시의 OWNER | 수락 완료 |
| MEMBER_ROLE_CHANGED | 변경된 팀원 | 역할이 실제로 바뀐 경우만 |
| MEMBER_REMOVED | 제거된 팀원 | OWNER의 제거 완료 |
| MEMBER_LEFT | OWNER | EDITOR·VIEWER 직접 탈퇴 |
| OWNERSHIP_TRANSFERRED | 새 OWNER | 소유권 이전 완료 |
| COMMENT_CREATED | 같은 공간의 다른 멤버 | 이벤트 소비 구현, 댓글 서비스는 미구현 |
| INACTIVITY_REMINDER | 로그인한 본인 | 30일 이상 지난 뒤 성공 로그인 시 1회 |

본인의 행동에 대한 자기 알림은 보내지 않습니다(복귀 안내 제외).
동일 이벤트는 수신자별로 중복 저장하지 않습니다. 재발급은 기존 초대 알림을 재사용합니다.
가입 전 이메일에는 앱 내 수신 계정이 없으므로 메일 초대만 유지합니다. 가입 후 재발급하면 앱 내 알림도 생성됩니다.

## API

모두 로그인 토큰이 필요하며 요청에서 recipientId·userId를 받지 않습니다.

| 기능 | 요청 | data |
|---|---|---|
| 목록 | GET /api/notifications?page=0&size=20&unreadOnly=false | notifications, page, size, totalElements, totalPages |
| 미읽음 개수 | GET /api/notifications/unread-count | {"unreadCount": 3} |
| 개별 읽음 | PATCH /api/notifications/{notificationId}/read | true |
| 전체 읽음 | PATCH /api/notifications/read-all | {"updatedCount": 3} |

목록은 createdAt 내림차순, 동률은 ID 내림차순입니다. page는 0 이상, size는 1~100입니다.
readAt이 null이면 미읽음입니다. 반복 읽음은 성공하며 최초 readAt을 바꾸지 않습니다.
다른 사람의 알림이나 없는 알림의 읽음 요청은 동일하게 404입니다.
인증 없음은 401, 잘못된 페이지·파라미터는 400입니다.

NotificationResponse: notificationId, type, message, createdAt, readAt, target.
문구는 기본 한국어 문구이며 파일명·댓글 본문·초대 토큰 등 민감한 내용을 저장하지 않습니다.

## 클릭 대상과 권한

target은 kind, available, spaceId, bookmarkId, invitationId, requiresInvitationToken으로 구성됩니다.
kind는 HOME / SPACE / BOOKMARK / INVITATION / NONE입니다.
프론트가 자신의 화면·패널 경로에 연결하며, 존재하지 않는 프론트 URL을 서버가 임의로 만들지 않습니다.

- 목록 반환 시 현재 멤버십과 자료의 존재 여부를 검사합니다.
- 제거·탈퇴·공간 삭제·북마크 삭제 후에는 available=false, kind=NONE이며 대상 ID를 반환하지 않습니다.
- 역할 변경 후에도 현재 읽기 권한이 있는 공간으로만 이동할 수 있습니다.
- 새 초대는 해당 이메일 수신자이며 유효한 PENDING 상태일 때 INVITATION 안내 대상으로 반환합니다.
  초대 수락 토큰은 알림에 넣지 않습니다. requiresInvitationToken=true이면 기존 이메일 링크로 수락해야 합니다.
- 수락 후 공간 멤버라면 기존 초대 알림은 SPACE 대상으로 반환합니다.
- 알림이 있어도 자료 조회 API의 권한 검사를 우회할 수 없습니다.
- 읽지 않은 과거 알림은 대상이 없어져도 기록·개수에 남으며, 사용자가 읽음 처리할 수 있습니다.

## 댓글 담당자 연결 방법

댓글 생성·권한 검증·DB 저장 후 같은 트랜잭션에서 다음 이벤트를 발행합니다.

```java
events.publishEvent(new BookmarkCommentCreated(actorId, bookmarkId, savedCommentId));
```

리스너도 작성자의 쓰기 권한과 실제 북마크를 확인합니다. 댓글 ID는 저장 후 확정된 ID여야 합니다.
댓글 ID와 북마크 ID로 중복을 방지하며 작성자 본인에게는 보내지 않습니다.
댓글 조회·수정·삭제 API나 댓글 데이터베이스는 이번에 구현하지 않았습니다.
댓글 API가 없는 현재 상태에서 일반 사용자 요청으로 댓글 알림이 발생한다고 안내하면 안 됩니다.

## 복귀 안내 및 운영

users.last_login_at에 성공 로그인 시각을 저장합니다. 실패 로그인은 시각·알림을 변경하지 않습니다.
기존 계정의 시각이 null이면 과거 활동을 추정하지 않고 첫 성공 로그인부터 기록합니다.
30일은 notification.inactivity-threshold(환경 변수 NOTIFICATION_INACTIVITY_THRESHOLD=30d)로 조정할 수 있습니다.
미방문 중 메일을 보내거나 매일 알림을 쌓는 스케줄러가 아니라, 복귀 시 표시하는 안내입니다.
동일 계정의 동시 로그인은 사용자 행 잠금으로 처리해 복귀 알림 중복을 방지합니다.

배포 전 scripts/mysql/20261123_notifications.sql을 백업 후 수동 적용합니다.
기존 로그인·회원 데이터는 유지되며 기존 last_login_at은 null입니다.
대상 리소스 삭제 시 알림은 남겨 접근을 재검사하고, 수신자 계정 삭제 시 해당 알림은 DB에서 함께 삭제됩니다.
기존 개발·운영 DB에는 이번 작업에서 적용하지 않습니다.

## 협업·후속 작업

- 프론트파트와 목록·미읽음 배지·읽음 처리·target 기반 클릭 이동·기본 문구를 연결합니다.
- 댓글 담당자와 트랜잭션 내 이벤트 발행을 연결합니다.
- 현재는 페이지 조회 방식입니다. 실시간 전송·알림 설정·보관 기간은 별도 요구사항으로 결정합니다.
- 15번 설문·계정 삭제·로그아웃 정책은 별도 작업입니다. 이번 로그인 변경은 복귀 알림에 필요한 시각 기록에 한정합니다.

## 검증 결과 (2026-11-23)

- MySQL 8.4.11 전용 DB에서 전체 131개 테스트 통과(실패·오류·건너뜀 0), bootJar 빌드 성공.
- 초대·수락·역할 변경·제거·탈퇴·소유권 이전과 복귀 안내 수신 대상을 확인했습니다.
- 다른 사용자 읽음 요청 차단, 개별·전체 읽음 반복 처리, 미읽음 개수·페이지·정렬을 검증했습니다.
- 같은 이벤트 동시 처리와 읽은 알림 재수신 시 중복·읽음 초기화가 없음을 확인했습니다.
- 롤백 시 알림 제거, 대상 삭제·권한 상실·초대 만료·취소 후 이동 차단을 확인했습니다.
- 별도 스키마 DB에서 SQL 재실행·기존 계정 보존·계정 삭제 시 알림 제거를 확인했습니다.
- 댓글 테스트는 내부 이벤트를 사용합니다. 실제 댓글 API·프론트 연동·기존 개발 및 운영 DB 적용은 수행하지 않았습니다.
