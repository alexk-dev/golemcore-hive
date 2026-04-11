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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.auth.application.port.out.OAuth2AuthorizationCodeRepository;
import me.golemcore.hive.auth.application.service.OAuth2AuthorizationApplicationService;
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.Role;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import org.junit.jupiter.api.Test;

class OAuth2AuthorizationApplicationServiceTest {

    @Test
    void shouldAuthorizeAndExchangeCodeWhenGolemSsoIsEnabled() {
        InMemoryRepository authorizationCodeRepository = new InMemoryRepository();
        OperatorAuthApplicationService operatorAuthApplicationService = mock(OperatorAuthApplicationService.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        OperatorAccount operator = OperatorAccount.builder()
                .id("op_1")
                .username("admin")
                .displayName("Hive Admin")
                .roles(Set.of(Role.ADMIN))
                .build();
        Golem golem = Golem.builder()
                .id("golem_1")
                .dashboardSsoEnabled(true)
                .lastHeartbeat(HeartbeatPing.builder()
                        .dashboardBaseUrl("https://bot.example.com/dashboard")
                        .build())
                .build();
        when(golemDirectoryUseCase.findGolem("golem_1")).thenReturn(Optional.of(golem));
        when(operatorAuthApplicationService.issueSsoTokens("op_1", "golem_1"))
                .thenReturn(Optional.of(new OperatorAuthResult(operator, "bot-access", null)));
        OAuth2AuthorizationApplicationService service = new OAuth2AuthorizationApplicationService(
                authorizationCodeRepository,
                operatorAuthApplicationService,
                golemDirectoryUseCase);

        String redirectUri = service.authorize(
                "op_1",
                "golem_1",
                "https://bot.example.com/dashboard/api/auth/hive/callback",
                "state-1",
                null);
        String code = redirectUri.substring(redirectUri.indexOf("code=") + "code=".length(),
                redirectUri.indexOf("&state="));
        Optional<OperatorAuthResult> result = service.exchange(
                code,
                "golem_1",
                "https://bot.example.com/dashboard/api/auth/hive/callback",
                null);

        assertTrue(redirectUri.startsWith("https://bot.example.com/dashboard/api/auth/hive/callback?code="));
        assertTrue(result.isPresent());
        assertEquals("bot-access", result.get().accessToken());
    }

    @Test
    void shouldRejectAuthorizeWhenGolemSsoIsDisabled() {
        OAuth2AuthorizationCodeRepository authorizationCodeRepository = mock(OAuth2AuthorizationCodeRepository.class);
        OperatorAuthApplicationService operatorAuthApplicationService = mock(OperatorAuthApplicationService.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        Golem golem = Golem.builder()
                .id("golem_1")
                .dashboardSsoEnabled(false)
                .lastHeartbeat(HeartbeatPing.builder()
                        .dashboardBaseUrl("https://bot.example.com/dashboard")
                        .build())
                .build();
        when(golemDirectoryUseCase.findGolem("golem_1")).thenReturn(Optional.of(golem));
        OAuth2AuthorizationApplicationService service = new OAuth2AuthorizationApplicationService(
                authorizationCodeRepository,
                operatorAuthApplicationService,
                golemDirectoryUseCase);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.authorize(
                "op_1",
                "golem_1",
                "https://bot.example.com/dashboard/api/auth/hive/callback",
                null,
                null));

        assertEquals("Dashboard SSO is disabled for golem golem_1", error.getMessage());
    }

    private static class InMemoryRepository implements OAuth2AuthorizationCodeRepository {

        private OAuth2AuthorizationCode authorizationCode;

        @Override
        public void save(OAuth2AuthorizationCode savedAuthorizationCode) {
            this.authorizationCode = savedAuthorizationCode;
        }

        @Override
        public Optional<OAuth2AuthorizationCode> consume(String code) {
            if (authorizationCode == null || authorizationCode.expiresAt().isBefore(Instant.now())
                    || !authorizationCode.code().equals(code)) {
                return Optional.empty();
            }
            OAuth2AuthorizationCode consumed = authorizationCode;
            authorizationCode = null;
            return Optional.of(consumed);
        }
    }
}
