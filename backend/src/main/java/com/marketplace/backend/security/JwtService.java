package com.marketplace.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Access tokens (short-lived, carry the session id "sid") and two-step challenge tokens (5 minutes,
 * type "2fa": only good for POST /api/auth/login/2fa, never accepted as an access token).
 */
@Service
public class JwtService {

    static final String TYPE = "typ";
    static final String CHALLENGE = "2fa";
    private static final long CHALLENGE_MS = 5 * 60_000;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public long accessTokenMs() {
        return expirationMs;
    }

    public String generateToken(String email, Long userId, String role, String sessionId) {
        return Jwts.builder()
                .subject(email)
                .claims(Map.of("userId", userId, "role", role, "sid", sessionId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** After the password: proves the first step for 5 minutes, until the 2FA code is checked. */
    public String challengeToken(String email, Long userId, String method) {
        return Jwts.builder()
                .subject(email)
                .claims(Map.of("userId", userId, TYPE, CHALLENGE, "method", method))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + CHALLENGE_MS))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** The user id of a valid, unexpired challenge token, or null. */
    public Long challengeUserId(String token) {
        try {
            Claims c = claims(token);
            if (!CHALLENGE.equals(c.get(TYPE)) || c.getExpiration().before(new Date())) return null;
            return ((Number) c.get("userId")).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    public String challengeMethod(String token) {
        return String.valueOf(claims(token).get("method"));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractSessionId(String token) {
        Object sid = claims(token).get("sid");
        return sid == null ? null : sid.toString();
    }

    /** A valid access token for this email: not expired, not a challenge token, bound to a session. */
    public boolean isTokenValid(String token, String email) {
        Claims c = claims(token);
        return c.getSubject().equals(email) && !c.getExpiration().before(new Date()) && c.get(TYPE) == null && c.get("sid") != null;
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(claims(token));
    }
}
