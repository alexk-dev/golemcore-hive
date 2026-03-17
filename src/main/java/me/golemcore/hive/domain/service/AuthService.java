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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.RefreshSession;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String OPERATORS_DIR = "operators";
    private static final String REFRESH_DIR = "auth/refresh-sessions";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenPair authenticate(String username, String password) {
        Optional<OperatorAccount> operatorOptional = findByUsername(username);
        if (operatorOptional.isEmpty()) {
            return null;
        }

        OperatorAccount operator = operatorOptional.get();
        if (!passwordEncoder.matches(password, operator.getPasswordHash())) {
            return null;
        }

        return issueTokens(operator, null);
    }

    public TokenPair refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return null;
        }

        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        RefreshSession session = loadRefreshSession(sessionId).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        if (!Objects.equals(session.getTokenHash(), hashToken(refreshToken))) {
            return null;
        }

        OperatorAccount operator = loadOperator(jwtTokenProvider.getOperatorId(refreshToken)).orElse(null);
        if (operator == null) {
            return null;
        }

        return issueTokens(operator, session);
    }

    public void logout(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return;
        }

        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        storagePort.delete(REFRESH_DIR, sessionId + ".json");
    }

    public OperatorAccount getOperatorByUsername(String username) {
        return findByUsername(username).orElse(null);
    }

    private TokenPair issueTokens(OperatorAccount operator, RefreshSession existingSession) {
        Instant now = Instant.now();
        String sessionId = existingSession != null ? existingSession.getId() : "rs_" + UUID.randomUUID().toString().replace("-", "");
        String refreshToken = jwtTokenProvider.generateRefreshToken(operator, sessionId);
        RefreshSession refreshSession = RefreshSession.builder()
                .id(sessionId)
                .operatorId(operator.getId())
                .tokenHash(hashToken(refreshToken))
                .createdAt(existingSession != null ? existingSession.getCreatedAt() : now)
                .expiresAt(now.plusSeconds(60L * 60L * 24L * 7L))
                .rotatedAt(now)
                .build();
        saveRefreshSession(refreshSession);
        return new TokenPair(
                jwtTokenProvider.generateAccessToken(operator),
                refreshToken);
    }

    private Optional<OperatorAccount> findByUsername(String username) {
        List<String> operatorFiles = storagePort.listObjects(OPERATORS_DIR, "");
        return operatorFiles.stream()
                .map(this::loadOperatorByPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(operator -> operator.getUsername().equals(username))
                .findFirst();
    }

    private Optional<OperatorAccount> loadOperator(String operatorId) {
        String content = storagePort.getText(OPERATORS_DIR, operatorId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, OperatorAccount.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize operator " + operatorId, exception);
        }
    }

    private Optional<OperatorAccount> loadOperatorByPath(String path) {
        String content = storagePort.getText(OPERATORS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, OperatorAccount.class));
        } catch (JsonProcessingException exception) {
            log.warn("[Auth] Failed to read operator record '{}': {}", path, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RefreshSession> loadRefreshSession(String sessionId) {
        String content = storagePort.getText(REFRESH_DIR, sessionId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, RefreshSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize refresh session " + sessionId, exception);
        }
    }

    private void saveRefreshSession(RefreshSession refreshSession) {
        try {
            storagePort.putTextAtomic(REFRESH_DIR, refreshSession.getId() + ".json",
                    objectMapper.writeValueAsString(refreshSession));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize refresh session", exception);
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 implementation", exception);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;
    }
}
