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

package me.golemcore.hive.fleet.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemAuthSession;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.fleet.application.port.in.GolemFleetUseCase;
import me.golemcore.hive.fleet.application.port.out.EnrollmentTokenRepository;
import me.golemcore.hive.fleet.application.port.out.GolemAuthSessionRepository;
import me.golemcore.hive.fleet.application.port.out.GolemTokenPort;
import me.golemcore.hive.fleet.application.service.GolemEnrollmentApplicationService;
import org.junit.jupiter.api.Test;

class GolemEnrollmentApplicationServiceTest {

    @Test
    void shouldCreateEnrollmentTokenWithPreviewAndActorMetadata() {
        EnrollmentTokenRepository enrollmentTokenRepository = mock(EnrollmentTokenRepository.class);
        GolemAuthSessionRepository golemAuthSessionRepository = mock(GolemAuthSessionRepository.class);
        GolemTokenPort golemTokenPort = mock(GolemTokenPort.class);
        GolemFleetUseCase golemFleetUseCase = mock(GolemFleetUseCase.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);

        GolemEnrollmentApplicationService service = new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                settings);

        CreatedEnrollmentToken created = service.createEnrollmentToken(
                new ActorContext("op_1", "admin"),
                "shared-lab",
                null);

        assertNotNull(created.revealedToken());
        assertTrue(created.revealedToken().startsWith(created.token().getId() + "."));
        assertTrue(created.token().getTokenPreview().startsWith(created.token().getId() + "."));
        assertEquals("op_1", created.token().getCreatedByOperatorId());
        assertEquals("admin", created.token().getCreatedByOperatorUsername());
        verify(enrollmentTokenRepository).save(argThat(token -> "shared-lab".equals(token.getNote())
                && token.getExpiresAt().isAfter(token.getCreatedAt())));
    }

    @Test
    void shouldCreateEnrollmentTokenWithUnlimitedExpiration() {
        EnrollmentTokenRepository enrollmentTokenRepository = mock(EnrollmentTokenRepository.class);
        GolemAuthSessionRepository golemAuthSessionRepository = mock(GolemAuthSessionRepository.class);
        GolemTokenPort golemTokenPort = mock(GolemTokenPort.class);
        GolemFleetUseCase golemFleetUseCase = mock(GolemFleetUseCase.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);

        GolemEnrollmentApplicationService service = new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                settings);

        CreatedEnrollmentToken created = service.createEnrollmentToken(
                new ActorContext("op_1", "admin"),
                "shared-lab",
                EnrollmentTokenExpirationPreset.UNLIMITED);

        assertNull(created.token().getExpiresAt());
        verify(enrollmentTokenRepository).save(argThat(token -> token.getExpiresAt() == null));
    }

    @Test
    void shouldCreateEnrollmentTokenUsingCalendarMonthPreset() {
        EnrollmentTokenRepository enrollmentTokenRepository = mock(EnrollmentTokenRepository.class);
        GolemAuthSessionRepository golemAuthSessionRepository = mock(GolemAuthSessionRepository.class);
        GolemTokenPort golemTokenPort = mock(GolemTokenPort.class);
        GolemFleetUseCase golemFleetUseCase = mock(GolemFleetUseCase.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);

        GolemEnrollmentApplicationService service = new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                settings);

        CreatedEnrollmentToken created = service.createEnrollmentToken(
                new ActorContext("op_1", "admin"),
                "shared-lab",
                EnrollmentTokenExpirationPreset.ONE_MONTH);

        Instant expectedExpiration = ZonedDateTime.ofInstant(created.token().getCreatedAt(), ZoneOffset.UTC)
                .plusMonths(1)
                .toInstant();
        assertEquals(expectedExpiration, created.token().getExpiresAt());
    }

    @Test
    void shouldRotateMachineTokensOnlyWhenStoredSessionMatchesTokenHash() {
        EnrollmentTokenRepository enrollmentTokenRepository = mock(EnrollmentTokenRepository.class);
        GolemAuthSessionRepository golemAuthSessionRepository = mock(GolemAuthSessionRepository.class);
        GolemTokenPort golemTokenPort = mock(GolemTokenPort.class);
        GolemFleetUseCase golemFleetUseCase = mock(GolemFleetUseCase.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);
        Golem golem = Golem.builder()
                .id("golem_1")
                .displayName("Builder")
                .state(GolemState.ONLINE)
                .build();
        when(golemTokenPort.parseRefreshToken("refresh-token"))
                .thenReturn(Optional.of(new GolemRefreshTokenClaims("golem_1", "gs_123", Set.of("golems:heartbeat"))));
        when(golemAuthSessionRepository.findById("gs_123")).thenReturn(Optional.of(GolemAuthSession.builder()
                .id("gs_123")
                .golemId("golem_1")
                .tokenHash("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .scopes(Set.of("golems:heartbeat"))
                .createdAt(Instant.parse("2026-04-08T17:00:00Z"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .rotatedAt(Instant.parse("2026-04-08T17:00:00Z"))
                .build()));
        when(golemFleetUseCase.findGolem("golem_1")).thenReturn(Optional.of(golem));
        when(golemTokenPort.issueAccessToken(golem, List.of("golems:heartbeat"))).thenReturn("new-access-token");
        when(golemTokenPort.issueRefreshToken(golem, List.of("golems:heartbeat"), "gs_123"))
                .thenReturn("new-refresh-token");

        GolemEnrollmentApplicationService service = new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                settings);

        MachineTokenPair rotated = service.rotateMachineTokens("golem_1", "refresh-token");

        assertEquals("new-access-token", rotated.accessToken());
        assertEquals("new-refresh-token", rotated.refreshToken());
        verify(golemAuthSessionRepository).save(argThat(session -> "gs_123".equals(session.getId())
                && session.getExpiresAt().isAfter(session.getCreatedAt())));
    }

    @Test
    void shouldRegisterGolemAndIncrementEnrollmentTokenUsage() {
        EnrollmentTokenRepository enrollmentTokenRepository = mock(EnrollmentTokenRepository.class);
        GolemAuthSessionRepository golemAuthSessionRepository = mock(GolemAuthSessionRepository.class);
        GolemTokenPort golemTokenPort = mock(GolemTokenPort.class);
        GolemFleetUseCase golemFleetUseCase = mock(GolemFleetUseCase.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);
        EnrollmentToken enrollmentToken = EnrollmentToken.builder()
                .id("et_1")
                .secretHash("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .tokenPreview("et_1.preview")
                .createdAt(Instant.parse("2026-04-08T17:00:00Z"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Golem golem = Golem.builder()
                .id("golem_1")
                .displayName("Builder")
                .state(GolemState.PENDING_ENROLLMENT)
                .build();
        when(enrollmentTokenRepository.findById("et_1")).thenReturn(Optional.of(enrollmentToken));
        when(golemFleetUseCase.registerGolem("Builder", "host-a", "bot-1.2.3", "build-42",
                Set.of("control", "events"),
                GolemCapabilitySnapshot.builder().snapshotHash("abc123").build(), "et_1"))
                .thenReturn(golem);
        when(golemTokenPort.issueAccessToken(golem,
                List.of(
                        "golems:control:connect",
                        "golems:events:write",
                        "golems:heartbeat",
                        "golems:policy:read",
                        "golems:policy:write")))
                .thenReturn("golem-access-token");
        when(golemTokenPort.issueRefreshToken(eq(golem),
                eq(List.of(
                        "golems:control:connect",
                        "golems:events:write",
                        "golems:heartbeat",
                        "golems:policy:read",
                        "golems:policy:write")),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("golem-refresh-token");

        GolemEnrollmentApplicationService service = new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                settings);

        RegistrationResult result = service.registerGolem(
                "et_1.refresh-token",
                "Builder",
                "host-a",
                "bot-1.2.3",
                "build-42",
                Set.of("control", "events"),
                GolemCapabilitySnapshot.builder().snapshotHash("abc123").build());

        assertEquals("golem_1", result.golem().getId());
        assertEquals("golem-access-token", result.tokens().accessToken());
        verify(enrollmentTokenRepository).save(argThat(token -> token.getRegistrationCount() == 1
                && "golem_1".equals(token.getLastRegisteredGolemId())));
    }
}
