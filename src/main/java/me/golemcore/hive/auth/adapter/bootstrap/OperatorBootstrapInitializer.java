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

package me.golemcore.hive.auth.adapter.bootstrap;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.auth.application.port.out.OperatorAccountRepository;
import me.golemcore.hive.auth.application.port.out.PasswordHashPort;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.Role;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OperatorBootstrapInitializer {

    private final HiveProperties properties;
    private final OperatorAccountRepository operatorAccountRepository;
    private final PasswordHashPort passwordHashPort;

    @PostConstruct
    public void init() {
        HiveProperties.AdminProperties adminProperties = properties.getBootstrap().getAdmin();
        if (!adminProperties.isEnabled()) {
            return;
        }

        if (!operatorAccountRepository.list().isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        OperatorAccount operatorAccount = OperatorAccount.builder()
                .id("op_" + UUID.randomUUID().toString().replace("-", ""))
                .username(adminProperties.getUsername())
                .displayName(adminProperties.getDisplayName())
                .passwordHash(passwordHashPort.encode(adminProperties.getPassword()))
                .roles(Set.of(Role.ADMIN, Role.OPERATOR))
                .createdAt(now)
                .updatedAt(now)
                .build();
        operatorAccountRepository.save(operatorAccount);
        log.info("[Auth] Bootstrapped default admin operator '{}'", operatorAccount.getUsername());
    }
}
