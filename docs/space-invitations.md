# 팀 스페이스 초대

## 구현 기준과 회의 안건

이메일을 지정한 단일 수락 초대를 구현합니다. 가입 전 이메일에도 초대할 수 있지만,
수락할 때는 해당 이메일로 로그인해야 합니다. 기본 EDITOR·72시간은 회의 전 임시 기준입니다.
발급 시 EDITOR 또는 VIEWER를 지정하고, 유효기간은 INVITATION_TTL로 설정할 수 있습니다.
불특정 사용자에게 배포하는 공용·다회용 초대 링크는 아직 지원하지 않습니다.

## API

모든 API는 로그인 토큰이 필요합니다. 발급·목록·취소·재발급은 해당 공간 OWNER 전용입니다.

| 기능 | 요청 | 성공 |
|---|---|---|
| 발급 | POST /api/spaces/{spaceId}/invitations | 201, InvitationResponse |
| 이전 경로 호환 | POST /api/spaces/{spaceId}/invite | 동일한 수락형 발급. 즉시 멤버 등록하지 않음 |
| 목록 | GET /api/spaces/{spaceId}/invitations | 200, InvitationResponse 목록 |
| 취소 | DELETE /api/spaces/{spaceId}/invitations/{invitationId} | 200, true |
| 재발급·재발송 | POST /api/spaces/{spaceId}/invitations/{invitationId}/resend | 200, 새 링크 포함 응답 |
| 수락 | POST /api/spaces/invitations/accept | 200, MemberDto |

발급 본문: `{"email":"member@example.com","role":"VIEWER"}`. role 생략 시 EDITOR입니다.
수락 본문: `{"token":"초대 토큰"}`. GET 요청·링크 진입만으로는 수락하지 않습니다.

InvitationResponse: invitationId, spaceId, email, role, status, delivery, expiresAt, invitationUrl.
목록에서는 invitationUrl이 null입니다. URL은 발급·재발급 때만 OWNER에게 반환합니다.
링크의 `#token=` fragment는 프론트가 읽어 로그인 후 수락 POST body로 전달하고 주소창에서 제거합니다.
이 URL은 해당 이메일 수신자만 수락할 수 있습니다. 서버 접근 로그에는 fragment가 전달되지 않습니다.

- status: PENDING / ACCEPTED / CANCELLED / EXPIRED. 만료는 expiresAt 기준으로 계산합니다.
- delivery: PENDING / SENT / FAILED. 생성 응답은 커밋 전 작성되므로 PENDING일 수 있습니다.
  실제 발송 결과는 목록 API에서 확인합니다. SENT는 SMTP 접수이며 받은편지함 도착 보장이 아닙니다.
- 동일 공간·이메일에 유효한 대기 초대가 있으면 409입니다. 기존 팀원도 409입니다.
- 중복 수락·만료·취소된 링크 수락은 409입니다. 취소는 반복 호출해도 200입니다.
- 수락 완료된 초대는 취소·재발급할 수 없습니다. 팀원 제거 API를 사용합니다.
- 재발급은 PENDING(만료 포함)만 허용하며, 이전 토큰을 즉시 무효화하고 유효기간을 갱신합니다.
- OWNER 초대·잘못된 필드는 400, 다른 이메일 수락·관리 권한 없음·개인 공간 발급은 403,
  없는 공간·초대·잘못된 토큰은 404입니다.
- 역할은 발급 시 저장됩니다. 수락 후 소유자가 일반 역할 변경 API로 변경할 수 있습니다.
- 공간 삭제 시 초대도 함께 삭제됩니다. 탈퇴·제거 후 사용한 초대로 다시 가입할 수 없습니다.

## 메일 설정

배포 환경에서 아래 값을 설정합니다. 저장소에 계정·비밀번호를 커밋하지 않습니다.

```properties
INVITATION_TTL=72h
INVITATION_ACCEPT_URL=https://프론트주소/invitations/accept
INVITATION_MAIL_FROM=발신주소
SPRING_MAIL_HOST=SMTP호스트
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=SMTP계정
SPRING_MAIL_PASSWORD=SMTP비밀번호
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

SMTP나 발신자 설정이 없으면 초대 자체는 생성되지만 delivery는 FAILED로 기록합니다.
발급된 링크는 OWNER가 복사할 수 있습니다. 설정 후 재발급하면 새 링크로 발송합니다.
메일 발송은 DB 커밋 후 실행하므로 롤백된 초대는 발송하지 않습니다.
현재 자동 재시도·영속 메일 큐는 없습니다. 커밋 직후 프로세스가 종료되면 PENDING이 남을 수 있으며
OWNER가 목록을 확인하고 재발급해야 합니다. SMTP 실패 원문·원본 토큰은 로그에 기록하지 않습니다.

## 배포·협업

- 새 테이블 space_invitations가 추가됩니다. 수동 스키마 관리는 scripts/mysql/20261123_space_invitations.sql 참고.
- 기존 즉시 등록된 팀원은 유지됩니다. 기존 /invite 응답의 memberId 대신 invitationId를 사용해야 합니다.
- 프론트파트와 초대 대기 목록·수락 URL·로그인 복귀·발송 실패 표시·취소·재발급 화면을 함께 진행합니다.
- 회의에서 기본 역할·유효기간·이메일 지정 여부·공용 링크 필요성을 확정합니다.
- SMTP 운영 계정 설정 및 실제 메일·실제 로그인 토큰을 통한 프론트 연동은 별도 검증이 필요합니다.

## 검증 결과 (2026-11-23)

- MySQL 8.4.11의 격리된 테스트 DB에서 전체 99개 테스트 통과(실패·오류·건너뜀 0), bootJar 빌드 성공.
- 동시 수락 시 한 요청만 성공하고 멤버십 한 건만 생성됨을 확인했습니다.
- 만료·취소·재발급·이메일 불일치·미가입 수신자의 가입 후 수락·공간 삭제를 검증했습니다.
- 모의 SMTP를 사용해 커밋 후 발송·실패 상태 기록·재발급·롤백 시 미발송을 확인했습니다.
- 테이블 생성 SQL과 동일 SQL 재실행은 별도 스키마 검증 DB에서 통과했습니다.
- 실제 이메일 발송 및 기존 개발·운영 DB 변경은 수행하지 않았습니다.
