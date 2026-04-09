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

package me.golemcore.hive.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.auth.application.OperatorAuthResult;
import me.golemcore.hive.auth.application.OperatorRefreshTokenClaims;
import me.golemcore.hive.auth.application.port.out.OperatorAccountRepository;
import me.golemcore.hive.auth.application.port.out.OperatorRefreshSessionRepository;
import me.golemcore.hive.auth.application.port.out.OperatorTokenPort;
import me.golemcore.hive.auth.application.port.out.PasswordHashPort;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.RefreshSession;

@RequiredArgsConstructor
public class OperatorAuthApplicationService {

    private final OperatorAccountRepository operatorAccountRepository;
    private final OperatorRefreshSessionRepository operatorRefreshSessionRepository;
    private final PasswordHashPort passwordHashPort;
    private final OperatorTokenPort operatorTokenPort;

    public Optional<OperatorAuthResult> authenticate(String username, String password) {
        Optional<OperatorAccount> operatorOptional = operatorAccountRepository.findByUsername(username);
        if (operatorOptional.isEmpty()) {
            return Optional.empty();
        }

        OperatorAccount operator = operatorOptional.get();
        if (!passwordHashPort.matches(password, operator.getPasswordHash())) {
            return Optional.empty();
        }

        return Optional.of(issueTokens(operator, null));
    }

    public Optional<OperatorAuthResult> refresh(String refreshToken) {
        Optional<OperatorRefreshTokenClaims> claimsOptional = operatorTokenPort.parseRefreshToken(refreshToken);
        if (claimsOptional.isEmpty()) {
            return Optional.empty();
        }

        OperatorRefreshTokenClaims claims = claimsOptional.get();
        if (claims.sessionId() == null || claims.sessionId().isBlank()) {
            return Optional.empty();
        }

        RefreshSession session = operatorRefreshSessionRepository.findById(claims.sessionId()).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!Objects.equals(session.getTokenHash(), hashToken(refreshToken))) {
            return Optional.empty();
        }

        OperatorAccount operator = operatorAccountRepository.findById(claims.operatorId()).orElse(null);
        if (operator == null) {
            return Optional.empty();
        }

        return Optional.of(issueTokens(operator, session));
    }

    public void logout(String refreshToken) {
        Optional<OperatorRefreshTokenClaims> claimsOptional = operatorTokenPort.parseRefreshToken(refreshToken);
        if (claimsOptional.isEmpty()) {
            return;
        }
        OperatorRefreshTokenClaims claims = claimsOptional.get();
        if (claims.sessionId() == null || claims.sessionId().isBlank()) {
            return;
        }
        operatorRefreshSessionRepository.deleteById(claims.sessionId());
    }

    public Optional<OperatorAccount> findOperatorByUsername(String username) {
        return operatorAccountRepository.findByUsername(username);
    }

    private OperatorAuthResult issueTokens(OperatorAccount operator, RefreshSession existingSession) {
        Instant now = Instant.now();
        String sessionId = existingSession != null
                ? existingSession.getId()
                : "rs_" + UUID.randomUUID().toString().replace("-", "");
        String refreshToken = operatorTokenPort.issueRefreshToken(operator, sessionId);
        RefreshSession refreshSession = RefreshSession.builder()
                .id(sessionId)
                .operatorId(operator.getId())
                .tokenHash(hashToken(refreshToken))
                .createdAt(existingSession != null ? existingSession.getCreatedAt() : now)
                .expiresAt(now.plusSeconds(60L * 60L * 24L * 7L))
                .rotatedAt(now)
                .build();
        operatorRefreshSessionRepository.save(refreshSession);
        return new OperatorAuthResult(
                operator,
                operatorTokenPort.issueAccessToken(operator),
                refreshToken);
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
}
