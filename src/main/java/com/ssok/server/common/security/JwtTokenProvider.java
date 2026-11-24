package com.ssok.server.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, ACCESS_TOKEN_TYPE, accessTokenValidityMs, null);
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, REFRESH_TOKEN_TYPE, refreshTokenValidityMs, null);
    }

    public String generateAccessToken(Long userId, String sessionId) {
        return generateToken(userId, ACCESS_TOKEN_TYPE, accessTokenValidityMs, sessionId);
    }

    public String generateRefreshToken(Long userId, String sessionId) {
        return generateToken(userId, REFRESH_TOKEN_TYPE, refreshTokenValidityMs, sessionId);
    }

    private String generateToken(Long userId, String tokenType, long validityMs, String sessionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(java.util.UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromAccessToken(String token) {
        return accessData(token).userId();
    }

    public record TokenData(Long userId, String sessionId, java.time.Instant expiresAt) {}
    public TokenData accessData(String token) { return tokenData(token, ACCESS_TOKEN_TYPE); }
    public TokenData refreshData(String token) { return tokenData(token, REFRESH_TOKEN_TYPE); }
    private TokenData tokenData(String token, String type) {
        Claims claims = parseClaims(token);
        if (!type.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) throw new JwtException("invalid token type");
        return new TokenData(Long.parseLong(claims.getSubject()), claims.get("sid", String.class), claims.getExpiration().toInstant());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
