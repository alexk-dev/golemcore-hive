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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.config.HiveProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductionReadinessService {

    private static final String DEFAULT_BOOTSTRAP_PASSWORD = "change-me-now";

    private final HiveProperties properties;

    @PostConstruct
    public void validateProductionReadiness() {
        if (!properties.getDeployment().isProductionMode()) {
            return;
        }
        if (properties.getSecurity().getJwt().getSecret() == null || properties.getSecurity().getJwt().getSecret().isBlank()) {
            throw new IllegalStateException("Production mode requires hive.security.jwt.secret");
        }
        if (!properties.getSecurity().getCookie().isSecure()) {
            throw new IllegalStateException("Production mode requires secure refresh cookies");
        }
        if (properties.getBootstrap().getAdmin().isEnabled()
                && DEFAULT_BOOTSTRAP_PASSWORD.equals(properties.getBootstrap().getAdmin().getPassword())) {
            throw new IllegalStateException("Production mode requires changing the bootstrap admin password");
        }
    }
}
