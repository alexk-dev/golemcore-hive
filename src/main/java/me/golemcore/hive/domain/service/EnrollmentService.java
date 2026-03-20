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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.adapter.inbound.web.security.SubjectType;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemAuthSession;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final String ENROLLMENT_TOKENS_DIR = "enrollment-tokens";
    private static final String GOLEM_REFRESH_SESSIONS_DIR = "auth/golem-refresh-sessions";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final HiveProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final GolemRegistryService golemRegistryService;

    public CreatedEnrollmentToken createEnrollmentToken(AuthenticatedActor actor, String note,
            Integer expiresInMinutes) {
        Instant now = Instant.now();
        String tokenId = "et_" + UUID.randomUUID().toString().replace("-", "");
        String secret = generateSecret();
        int ttlMinutes = expiresInMinutes != null && expiresInMinutes > 0
                ? expiresInMinutes
                : properties.getFleet().getEnrollmentTokenTtlMinutes();
        EnrollmentToken enrollmentToken = EnrollmentToken.builder()
                .id(tokenId)
                .secretHash(hashToken(secret))
                .tokenPreview(tokenId + "." + previewSecret(secret))
                .note(note)
                .createdByOperatorId(actor.getSubjectId())
                .createdByOperatorUsername(actor.getName())
                .createdAt(now)
                .expiresAt(now.plusSeconds(ttlMinutes * 60L))
                .build();
        saveEnrollmentToken(enrollmentToken);
        return new CreatedEnrollmentToken(enrollmentToken, tokenId + "." + secret);
    }

    public List<EnrollmentToken> listEnrollmentTokens() {
        return storagePort.listObjects(ENROLLMENT_TOKENS_DIR, "").stream()
                .map(this::loadEnrollmentTokenByPath)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(EnrollmentToken::getCreatedAt).reversed())
                .toList();
    }

    public EnrollmentToken revokeEnrollmentToken(String tokenId, String reason) {
        EnrollmentToken token = loadEnrollmentToken(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown enrollment token: " + tokenId));
        token.setRevoked(true);
        token.setRevokeReason(reason);
        token.setRevokedAt(Instant.now());
        saveEnrollmentToken(token);
        return token;
    }

    public RegistrationResult registerGolem(String enrollmentTokenValue,
            String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot) {
        EnrollmentToken enrollmentToken = validateEnrollmentToken(enrollmentTokenValue);
        Golem golem = golemRegistryService.registerGolem(displayName, hostLabel, runtimeVersion, buildVersion,
                supportedChannels, capabilitySnapshot, enrollmentToken.getId());
        List<String> scopes = List.of(
                GolemScope.CONTROL_CONNECT.value(),
                GolemScope.EVENTS_WRITE.value(),
                GolemScope.HEARTBEAT.value());
        MachineTokenPair tokens = issueMachineTokens(golem, scopes, null);
        enrollmentToken.setLastUsedAt(Instant.now());
        enrollmentToken.setRegistrationCount(enrollmentToken.getRegistrationCount() + 1L);
        enrollmentToken.setLastRegisteredGolemId(golem.getId());
        saveEnrollmentToken(enrollmentToken);
        return new RegistrationResult(golem, tokens);
    }

    public MachineTokenPair rotateMachineTokens(String golemId, String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)
                || jwtTokenProvider.getSubjectType(refreshToken) != SubjectType.GOLEM) {
            return null;
        }
        if (!golemId.equals(jwtTokenProvider.getSubjectId(refreshToken))) {
            return null;
        }

        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        GolemAuthSession session = loadGolemAuthSession(sessionId).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        if (!hashToken(refreshToken).equals(session.getTokenHash())) {
            return null;
        }
        Golem golem = golemRegistryService.findGolem(golemId).orElse(null);
        if (golem == null || golem.getState() == GolemState.REVOKED) {
            return null;
        }
        return issueMachineTokens(golem, List.copyOf(session.getScopes()), session);
    }

    private EnrollmentToken validateEnrollmentToken(String enrollmentTokenValue) {
        String[] parts = enrollmentTokenValue != null ? enrollmentTokenValue.split("\\.", 2) : new String[0];
        if (parts.length != 2) {
            throw new IllegalArgumentException("Enrollment token is malformed");
        }
        EnrollmentToken enrollmentToken = loadEnrollmentToken(parts[0])
                .orElseThrow(() -> new IllegalArgumentException("Enrollment token is invalid"));
        if (enrollmentToken.isRevoked()) {
            throw new IllegalArgumentException("Enrollment token has been revoked");
        }
        if (enrollmentToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Enrollment token has expired");
        }
        if (!hashToken(parts[1]).equals(enrollmentToken.getSecretHash())) {
            throw new IllegalArgumentException("Enrollment token is invalid");
        }
        return enrollmentToken;
    }

    private MachineTokenPair issueMachineTokens(Golem golem, List<String> scopes, GolemAuthSession existingSession) {
        Instant now = Instant.now();
        String sessionId = existingSession != null ? existingSession.getId()
                : "gs_" + UUID.randomUUID().toString().replace("-", "");
        String accessToken = jwtTokenProvider.generateGolemAccessToken(golem, scopes);
        String refreshToken = jwtTokenProvider.generateGolemRefreshToken(golem, scopes, sessionId);
        GolemAuthSession golemAuthSession = GolemAuthSession.builder()
                .id(sessionId)
                .golemId(golem.getId())
                .tokenHash(hashToken(refreshToken))
                .scopes(Set.copyOf(scopes))
                .createdAt(existingSession != null ? existingSession.getCreatedAt() : now)
                .expiresAt(now.plusSeconds(
                        properties.getSecurity().getJwt().getGolemRefreshExpirationDays() * 24L * 60L * 60L))
                .rotatedAt(now)
                .build();
        saveGolemAuthSession(golemAuthSession);
        return new MachineTokenPair(
                accessToken,
                refreshToken,
                now.plusSeconds(properties.getSecurity().getJwt().getGolemAccessExpirationMinutes() * 60L),
                golemAuthSession.getExpiresAt(),
                scopes);
    }

    private Optional<EnrollmentToken> loadEnrollmentToken(String tokenId) {
        String content = storagePort.getText(ENROLLMENT_TOKENS_DIR, tokenId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalizeEnrollmentToken(objectMapper.readValue(content, EnrollmentToken.class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize enrollment token " + tokenId, exception);
        }
    }

    private Optional<EnrollmentToken> loadEnrollmentTokenByPath(String path) {
        String content = storagePort.getText(ENROLLMENT_TOKENS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalizeEnrollmentToken(objectMapper.readValue(content, EnrollmentToken.class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize enrollment token " + path, exception);
        }
    }

    private EnrollmentToken normalizeEnrollmentToken(EnrollmentToken enrollmentToken) {
        if (enrollmentToken.getSchemaVersion() < 2) {
            enrollmentToken.setSchemaVersion(2);
        }
        if (enrollmentToken.getRegistrationCount() <= 0L && enrollmentToken.getLastUsedAt() != null) {
            enrollmentToken.setRegistrationCount(1L);
        }
        return enrollmentToken;
    }

    private void saveEnrollmentToken(EnrollmentToken enrollmentToken) {
        try {
            storagePort.putTextAtomic(ENROLLMENT_TOKENS_DIR, enrollmentToken.getId() + ".json",
                    objectMapper.writeValueAsString(enrollmentToken));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize enrollment token " + enrollmentToken.getId(),
                    exception);
        }
    }

    private Optional<GolemAuthSession> loadGolemAuthSession(String sessionId) {
        String content = storagePort.getText(GOLEM_REFRESH_SESSIONS_DIR, sessionId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, GolemAuthSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize golem auth session " + sessionId, exception);
        }
    }

    private void saveGolemAuthSession(GolemAuthSession session) {
        try {
            storagePort.putTextAtomic(GOLEM_REFRESH_SESSIONS_DIR, session.getId() + ".json",
                    objectMapper.writeValueAsString(session));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize golem auth session " + session.getId(), exception);
        }
    }

    private String generateSecret() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String previewSecret(String secret) {
        if (secret.length() <= 6) {
            return secret;
        }
        return secret.substring(0, 3) + "..." + secret.substring(secret.length() - 3);
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
    public static class CreatedEnrollmentToken {
        private final EnrollmentToken token;
        private final String revealedToken;
    }

    @Getter
    @RequiredArgsConstructor
    public static class MachineTokenPair {
        private final String accessToken;
        private final String refreshToken;
        private final Instant accessTokenExpiresAt;
        private final Instant refreshTokenExpiresAt;
        private final List<String> scopes;
    }

    @Getter
    @RequiredArgsConstructor
    public static class RegistrationResult {
        private final Golem golem;
        private final MachineTokenPair tokens;
    }
}
