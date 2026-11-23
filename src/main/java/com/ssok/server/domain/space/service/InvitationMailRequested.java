package com.ssok.server.domain.space.service;

// 토큰·메일 주소가 포함된 이벤트를 로그에 출력하지 않는다.
public record InvitationMailRequested(Long invitationId, String tokenHash, String email, String url) {
}
