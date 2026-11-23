package com.ssok.server.domain.auth.service;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.auth.entity.AuthSession;
import com.ssok.server.domain.auth.repository.AuthSessionRepository;
import com.ssok.server.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final JwtTokenProvider tokens;
    public record Tokens(String accessToken, String refreshToken) {}

    @Transactional
    public Tokens open(Long userId) {
        var user = users.findLockedById(userId).orElseThrow(() -> unauthorized());
        String id = UUID.randomUUID().toString();
        String refresh = tokens.generateRefreshToken(userId, id);
        sessions.saveAndFlush(new AuthSession(id, user, hash(refresh), tokens.refreshData(refresh).expiresAt()));
        return new Tokens(tokens.generateAccessToken(userId, id), refresh);
    }

    @Transactional(readOnly = true)
    public JwtTokenProvider.TokenData authenticate(String token) {
        var data = tokens.accessData(token);
        if (data.sessionId() == null) throw unauthorized();
        var session = sessions.findByIdAndUserId(data.sessionId(), data.userId()).orElseThrow(() -> unauthorized());
        if (!session.active() || !users.existsById(data.userId())) throw unauthorized();
        return data;
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        JwtTokenProvider.TokenData data;
        try { data = tokens.refreshData(refreshToken); }
        catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) { throw unauthorized(); }
        if (data.sessionId() == null) throw unauthorized();
        // 계정 삭제·로그아웃과 같은 순서로 사용자 → 세션을 잠근다.
        users.findLockedById(data.userId()).orElseThrow(() -> unauthorized());
        var session = sessions.findLockedById(data.sessionId()).orElseThrow(() -> unauthorized());
        if (!session.getUser().getId().equals(data.userId()) || !session.active()
                || !MessageDigest.isEqual(session.getRefreshHash().getBytes(StandardCharsets.US_ASCII), hash(refreshToken).getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized();
        }
        String next = tokens.generateRefreshToken(data.userId(), session.getId());
        session.rotate(hash(next), tokens.refreshData(next).expiresAt());
        return new Tokens(tokens.generateAccessToken(data.userId(), session.getId()), next);
    }

    @Transactional
    public void logout(Long userId, String sessionId) {
        users.findLockedById(userId).orElseThrow(() -> unauthorized());
        var session = sessions.findLockedById(sessionId).orElseThrow(() -> unauthorized());
        if (!session.getUser().getId().equals(userId)) throw unauthorized();
        session.revoke();
    }

    private static UnauthenticatedException unauthorized() { return new UnauthenticatedException("authentication required"); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
