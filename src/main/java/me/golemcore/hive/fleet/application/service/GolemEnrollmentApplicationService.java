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

package me.golemcore.hive.fleet.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemAuthSession;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.fleet.application.ActorContext;
import me.golemcore.hive.fleet.application.CreatedEnrollmentToken;
import me.golemcore.hive.fleet.application.EnrollmentTokenExpirationPreset;
import me.golemcore.hive.fleet.application.FleetSettings;
import me.golemcore.hive.fleet.application.GolemRefreshTokenClaims;
import me.golemcore.hive.fleet.application.MachineTokenPair;
import me.golemcore.hive.fleet.application.RegistrationResult;
import me.golemcore.hive.fleet.application.port.in.GolemEnrollmentUseCase;
import me.golemcore.hive.fleet.application.port.in.GolemFleetUseCase;
import me.golemcore.hive.fleet.application.port.out.EnrollmentTokenRepository;
import me.golemcore.hive.fleet.application.port.out.GolemAuthSessionRepository;
import me.golemcore.hive.fleet.application.port.out.GolemTokenPort;

@RequiredArgsConstructor
public class GolemEnrollmentApplicationService implements GolemEnrollmentUseCase {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EnrollmentTokenRepository enrollmentTokenRepository;
    private final GolemAuthSessionRepository golemAuthSessionRepository;
    private final GolemTokenPort golemTokenPort;
    private final GolemFleetUseCase golemFleetUseCase;
    private final FleetSettings settings;

    @Override
    public CreatedEnrollmentToken createEnrollmentToken(
            ActorContext actor,
            String note,
            EnrollmentTokenExpirationPreset expirationPreset) {
        Instant now = Instant.now();
        String tokenId = "et_" + UUID.randomUUID().toString().replace("-", "");
        String secret = generateSecret();
        EnrollmentToken enrollmentToken = EnrollmentToken.builder()
                .id(tokenId)
                .secretHash(hashToken(secret))
                .tokenPreview(tokenId + "." + previewSecret(secret))
                .note(note)
                .createdByOperatorId(actor.subjectId())
                .createdByOperatorUsername(actor.name())
                .createdAt(now)
                .expiresAt(resolveExpiration(now, expirationPreset))
                .build();
        enrollmentTokenRepository.save(enrollmentToken);
        return new CreatedEnrollmentToken(enrollmentToken, tokenId + "." + secret);
    }

    @Override
    public List<EnrollmentToken> listEnrollmentTokens() {
        return enrollmentTokenRepository.list().stream()
                .sorted(Comparator.comparing(EnrollmentToken::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public EnrollmentToken revokeEnrollmentToken(String tokenId, String reason) {
        EnrollmentToken token = enrollmentTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown enrollment token: " + tokenId));
        token.setRevoked(true);
        token.setRevokeReason(reason);
        token.setRevokedAt(Instant.now());
        enrollmentTokenRepository.save(token);
        return token;
    }

    @Override
    public RegistrationResult registerGolem(String enrollmentTokenValue,
            String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot) {
        EnrollmentToken enrollmentToken = validateEnrollmentToken(enrollmentTokenValue);
        Golem golem = golemFleetUseCase.registerGolem(displayName, hostLabel, runtimeVersion, buildVersion,
                supportedChannels, capabilitySnapshot, enrollmentToken.getId());
        List<String> scopes = List.of(
                GolemScope.CONTROL_CONNECT.value(),
                GolemScope.EVENTS_WRITE.value(),
                GolemScope.HEARTBEAT.value(),
                GolemScope.POLICY_READ.value(),
                GolemScope.POLICY_WRITE.value());
        MachineTokenPair tokens = issueMachineTokens(golem, scopes, null);
        enrollmentToken.setLastUsedAt(Instant.now());
        enrollmentToken.setRegistrationCount(enrollmentToken.getRegistrationCount() + 1L);
        enrollmentToken.setLastRegisteredGolemId(golem.getId());
        enrollmentTokenRepository.save(enrollmentToken);
        return new RegistrationResult(golem, tokens);
    }

    @Override
    public MachineTokenPair rotateMachineTokens(String golemId, String refreshToken) {
        GolemRefreshTokenClaims claims = golemTokenPort.parseRefreshToken(refreshToken).orElse(null);
        if (claims == null || !golemId.equals(claims.golemId()) || claims.sessionId() == null
                || claims.sessionId().isBlank()) {
            return null;
        }
        GolemAuthSession session = golemAuthSessionRepository.findById(claims.sessionId()).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        if (!hashToken(refreshToken).equals(session.getTokenHash())) {
            return null;
        }
        Golem golem = golemFleetUseCase.findGolem(golemId).orElse(null);
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
        EnrollmentToken enrollmentToken = enrollmentTokenRepository.findById(parts[0])
                .orElseThrow(() -> new IllegalArgumentException("Enrollment token is invalid"));
        if (enrollmentToken.isRevoked()) {
            throw new IllegalArgumentException("Enrollment token has been revoked");
        }
        if (enrollmentToken.getExpiresAt() != null && enrollmentToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Enrollment token has expired");
        }
        if (!hashToken(parts[1]).equals(enrollmentToken.getSecretHash())) {
            throw new IllegalArgumentException("Enrollment token is invalid");
        }
        return enrollmentToken;
    }

    private Instant resolveExpiration(Instant createdAt, EnrollmentTokenExpirationPreset expirationPreset) {
        if (expirationPreset == null) {
            return createdAt.plusSeconds(settings.enrollmentTokenTtlMinutes() * 60L);
        }
        return switch (expirationPreset) {
        case ONE_HOUR -> createdAt.plusSeconds(60L * 60L);
        case EIGHT_HOURS -> createdAt.plusSeconds(8L * 60L * 60L);
        case ONE_DAY -> createdAt.plusSeconds(24L * 60L * 60L);
        case SEVEN_DAYS -> createdAt.plusSeconds(7L * 24L * 60L * 60L);
        case ONE_MONTH -> ZonedDateTime.ofInstant(createdAt, ZoneOffset.UTC).plusMonths(1).toInstant();
        case ONE_YEAR -> ZonedDateTime.ofInstant(createdAt, ZoneOffset.UTC).plusYears(1).toInstant();
        case UNLIMITED -> null;
        };
    }

    private MachineTokenPair issueMachineTokens(Golem golem, List<String> scopes, GolemAuthSession existingSession) {
        Instant now = Instant.now();
        String sessionId = existingSession != null
                ? existingSession.getId()
                : "gs_" + UUID.randomUUID().toString().replace("-", "");
        String accessToken = golemTokenPort.issueAccessToken(golem, scopes);
        String refreshToken = golemTokenPort.issueRefreshToken(golem, scopes, sessionId);
        GolemAuthSession session = GolemAuthSession.builder()
                .id(sessionId)
                .golemId(golem.getId())
                .tokenHash(hashToken(refreshToken))
                .scopes(Set.copyOf(scopes))
                .createdAt(existingSession != null ? existingSession.getCreatedAt() : now)
                .expiresAt(now.plusSeconds(settings.golemRefreshExpirationDays() * 24L * 60L * 60L))
                .rotatedAt(now)
                .build();
        golemAuthSessionRepository.save(session);
        return new MachineTokenPair(
                accessToken,
                refreshToken,
                now.plusSeconds(settings.golemAccessExpirationMinutes() * 60L),
                session.getExpiresAt(),
                scopes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 implementation", exception);
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
        return secret.substring(0, 6) + "...";
    }
}
