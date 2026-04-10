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

package me.golemcore.hive.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.auth.application.port.out.OperatorAccountRepository;
import me.golemcore.hive.auth.application.port.out.OperatorRefreshSessionRepository;
import me.golemcore.hive.auth.application.port.out.OperatorTokenPort;
import me.golemcore.hive.auth.application.port.out.PasswordHashPort;
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.RefreshSession;
import me.golemcore.hive.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperatorAuthApplicationServiceTest {

    @Test
    void shouldAuthenticateKnownOperatorAndPersistRefreshSession() {
        OperatorAccountRepository operatorAccountRepository = mock(OperatorAccountRepository.class);
        OperatorRefreshSessionRepository refreshSessionRepository = mock(OperatorRefreshSessionRepository.class);
        PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
        OperatorTokenPort operatorTokenPort = mock(OperatorTokenPort.class);
        OperatorAccount operator = operatorAccount();
        when(operatorAccountRepository.findByUsername("admin")).thenReturn(Optional.of(operator));
        when(passwordHashPort.matches("change-me-now", operator.getPasswordHash())).thenReturn(true);
        when(operatorTokenPort.issueAccessToken(operator)).thenReturn("access-token");
        when(operatorTokenPort.issueRefreshToken(eq(operator), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("refresh-token");

        OperatorAuthApplicationService service = new OperatorAuthApplicationService(
                operatorAccountRepository,
                refreshSessionRepository,
                passwordHashPort,
                operatorTokenPort);

        Optional<OperatorAuthResult> result = service.authenticate("admin", "change-me-now");

        assertTrue(result.isPresent());
        assertEquals("access-token", result.get().accessToken());
        assertEquals("refresh-token", result.get().refreshToken());
        assertEquals(operator.getId(), result.get().operator().getId());
        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository).save(sessionCaptor.capture());
        assertTrue(sessionCaptor.getValue().getId().startsWith("rs_"));
        assertEquals(operator.getId(), sessionCaptor.getValue().getOperatorId());
        verify(operatorTokenPort).issueRefreshToken(
                eq(operator),
                argThat(sessionId -> sessionId != null && sessionId.startsWith("rs_")));
    }

    @Test
    void shouldRejectInvalidPassword() {
        OperatorAccountRepository operatorAccountRepository = mock(OperatorAccountRepository.class);
        OperatorRefreshSessionRepository refreshSessionRepository = mock(OperatorRefreshSessionRepository.class);
        PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
        OperatorTokenPort operatorTokenPort = mock(OperatorTokenPort.class);
        OperatorAccount operator = operatorAccount();
        when(operatorAccountRepository.findByUsername("admin")).thenReturn(Optional.of(operator));
        when(passwordHashPort.matches("wrong-password", operator.getPasswordHash())).thenReturn(false);

        OperatorAuthApplicationService service = new OperatorAuthApplicationService(
                operatorAccountRepository,
                refreshSessionRepository,
                passwordHashPort,
                operatorTokenPort);

        Optional<OperatorAuthResult> result = service.authenticate("admin", "wrong-password");

        assertFalse(result.isPresent());
        verify(refreshSessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRefreshOnlyWhenRefreshSessionMatchesStoredHash() {
        OperatorAccountRepository operatorAccountRepository = mock(OperatorAccountRepository.class);
        OperatorRefreshSessionRepository refreshSessionRepository = mock(OperatorRefreshSessionRepository.class);
        PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
        OperatorTokenPort operatorTokenPort = mock(OperatorTokenPort.class);
        OperatorAccount operator = operatorAccount();
        when(operatorTokenPort.parseRefreshToken("refresh-token"))
                .thenReturn(Optional.of(new OperatorRefreshTokenClaims(operator.getId(), operator.getUsername(),
                        "rs_existing")));
        when(operatorAccountRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(refreshSessionRepository.findById("rs_existing")).thenReturn(Optional.of(RefreshSession.builder()
                .id("rs_existing")
                .operatorId(operator.getId())
                .tokenHash("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .rotatedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build()));
        when(operatorTokenPort.issueAccessToken(operator)).thenReturn("new-access-token");
        when(operatorTokenPort.issueRefreshToken(operator, "rs_existing")).thenReturn("new-refresh-token");

        OperatorAuthApplicationService service = new OperatorAuthApplicationService(
                operatorAccountRepository,
                refreshSessionRepository,
                passwordHashPort,
                operatorTokenPort);

        Optional<OperatorAuthResult> result = service.refresh("refresh-token");

        assertTrue(result.isPresent());
        assertEquals("new-access-token", result.get().accessToken());
        assertEquals("new-refresh-token", result.get().refreshToken());
    }

    @Test
    void shouldDeleteRefreshSessionOnLogout() {
        OperatorAccountRepository operatorAccountRepository = mock(OperatorAccountRepository.class);
        OperatorRefreshSessionRepository refreshSessionRepository = mock(OperatorRefreshSessionRepository.class);
        PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
        OperatorTokenPort operatorTokenPort = mock(OperatorTokenPort.class);
        when(operatorTokenPort.parseRefreshToken("refresh-token"))
                .thenReturn(Optional.of(new OperatorRefreshTokenClaims("op_1", "admin", "rs_123")));

        OperatorAuthApplicationService service = new OperatorAuthApplicationService(
                operatorAccountRepository,
                refreshSessionRepository,
                passwordHashPort,
                operatorTokenPort);

        service.logout("refresh-token");

        verify(refreshSessionRepository).deleteById("rs_123");
    }

    private OperatorAccount operatorAccount() {
        return OperatorAccount.builder()
                .id("op_1")
                .username("admin")
                .displayName("Hive Admin")
                .passwordHash("$2a$10$encoded")
                .roles(Set.of(Role.ADMIN, Role.OPERATOR))
                .createdAt(Instant.parse("2026-04-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();
    }

}
