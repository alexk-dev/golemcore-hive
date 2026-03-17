/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.hive.adapter.inbound.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.OperatorAccount;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HiveProperties properties;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            byte[] randomBytes = new byte[64];
            SECURE_RANDOM.nextBytes(randomBytes);
            secret = Base64.getEncoder().encodeToString(randomBytes);
            log.warn("[Auth] No JWT secret configured, generated ephemeral secret");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            byte[] padded = new byte[MIN_SECRET_BYTES];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, MIN_SECRET_BYTES));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(OperatorAccount operator) {
        Duration expiration = Duration.ofMinutes(properties.getSecurity().getJwt().getAccessExpirationMinutes());
        return buildOperatorToken(operator, "access", expiration, null);
    }

    public String generateRefreshToken(OperatorAccount operator, String sessionId) {
        Duration expiration = Duration.ofDays(properties.getSecurity().getJwt().getRefreshExpirationDays());
        return buildOperatorToken(operator, "refresh", expiration, sessionId);
    }

    public String generateGolemAccessToken(Golem golem, List<String> scopes) {
        Duration expiration = Duration.ofMinutes(properties.getSecurity().getJwt().getGolemAccessExpirationMinutes());
        return buildGolemToken(golem, scopes, "access", expiration, null);
    }

    public String generateGolemRefreshToken(Golem golem, List<String> scopes, String sessionId) {
        Duration expiration = Duration.ofDays(properties.getSecurity().getJwt().getGolemRefreshExpirationDays());
        return buildGolemToken(golem, scopes, "refresh", expiration, sessionId);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return properties.getSecurity().getJwt().getIssuer().equals(claims.getIssuer());
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("[Auth] Invalid JWT: {}", exception.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String getOperatorId(String token) {
        return parseClaims(token).get("operatorId", String.class);
    }

    public String getSubjectId(String token) {
        Claims claims = parseClaims(token);
        String operatorId = claims.get("operatorId", String.class);
        if (operatorId != null) {
            return operatorId;
        }
        return claims.get("golemId", String.class);
    }

    public SubjectType getSubjectType(String token) {
        String principalType = parseClaims(token).get("principalType", String.class);
        return SubjectType.valueOf(principalType);
    }

    public String getSessionId(String token) {
        return parseClaims(token).get("sid", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        List<String> roles = parseClaims(token).get("roles", List.class);
        return roles != null ? roles : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getScopes(String token) {
        Claims claims = parseClaims(token);
        List<String> scopes = claims.get("scopes", List.class);
        return scopes != null ? scopes : List.of();
    }

    public String getAudience(String token) {
        Claims claims = parseClaims(token);
        return claims.getAudience().iterator().hasNext()
                ? claims.getAudience().iterator().next()
                : null;
    }

    private String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    private String buildOperatorToken(OperatorAccount operator, String type, Duration expiration, String sessionId) {
        Instant now = Instant.now();
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(operator.getUsername())
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .audience().add(properties.getSecurity().getJwt().getAudience()).and()
                .claim("principalType", SubjectType.OPERATOR.name())
                .claim("type", type)
                .claim("operatorId", operator.getId())
                .claim("roles", operator.getRoles().stream().map(Enum::name).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)));
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.signWith(signingKey).compact();
    }

    private String buildGolemToken(Golem golem, List<String> scopes, String type, Duration expiration, String sessionId) {
        Instant now = Instant.now();
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(golem.getId())
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .audience().add(properties.getSecurity().getJwt().getGolemAudience()).and()
                .claim("principalType", SubjectType.GOLEM.name())
                .claim("type", type)
                .claim("golemId", golem.getId())
                .claim("displayName", golem.getDisplayName())
                .claim("scopes", scopes.stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)));
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.signWith(signingKey).compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
