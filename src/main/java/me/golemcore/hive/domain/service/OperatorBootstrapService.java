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
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.model.Role;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperatorBootstrapService {

    private static final String OPERATORS_DIR = "operators";

    private final HiveProperties properties;
    private final StoragePort storagePort;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        HiveProperties.AdminProperties adminProperties = properties.getBootstrap().getAdmin();
        if (!adminProperties.isEnabled()) {
            return;
        }

        storagePort.ensureDirectory(OPERATORS_DIR);
        List<String> existingOperators = storagePort.listObjects(OPERATORS_DIR, "");
        if (!existingOperators.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        OperatorAccount operator = OperatorAccount.builder()
                .id("op_" + UUID.randomUUID().toString().replace("-", ""))
                .username(adminProperties.getUsername())
                .displayName(adminProperties.getDisplayName())
                .passwordHash(passwordEncoder.encode(adminProperties.getPassword()))
                .roles(Set.of(Role.ADMIN, Role.OPERATOR))
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            storagePort.putTextAtomic(OPERATORS_DIR, operator.getId() + ".json",
                    objectMapper.writeValueAsString(operator));
            log.info("[Auth] Bootstrapped default admin operator '{}'", operator.getUsername());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize bootstrap operator", exception);
        }
    }
}
