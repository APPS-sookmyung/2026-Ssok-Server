package com.ssok.server.domain.notification.entity;

public enum NotificationType {
    INVITATION_RECEIVED("팀 스페이스 초대를 받았습니다."),
    INVITATION_ACCEPTED("팀원이 초대를 수락했습니다."),
    MEMBER_ROLE_CHANGED("스페이스에서 내 역할이 변경되었습니다."),
    MEMBER_REMOVED("스페이스에서 제외되었습니다."),
    MEMBER_LEFT("팀원이 스페이스를 탈퇴했습니다."),
    OWNERSHIP_TRANSFERRED("스페이스 소유권을 이전받았습니다."),
    COMMENT_CREATED("북마크에 새 댓글이 등록되었습니다."),
    INACTIVITY_REMINDER("오랜만에 돌아오셨네요. 저장해 둔 자료를 확인해 보세요.");

    private final String message;
    NotificationType(String message) { this.message = message; }
    public String message() { return message; }
}
