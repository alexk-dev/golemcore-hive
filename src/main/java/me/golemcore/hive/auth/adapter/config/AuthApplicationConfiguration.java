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

package me.golemcore.hive.auth.adapter.config;

import me.golemcore.hive.auth.application.port.out.OperatorAccountRepository;
import me.golemcore.hive.auth.application.port.out.OperatorRefreshSessionRepository;
import me.golemcore.hive.auth.application.port.out.OperatorTokenPort;
import me.golemcore.hive.auth.application.port.out.PasswordHashPort;
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthApplicationConfiguration {

    @Bean
    public OperatorAuthApplicationService operatorAuthApplicationService(
            OperatorAccountRepository operatorAccountRepository,
            OperatorRefreshSessionRepository operatorRefreshSessionRepository,
            PasswordHashPort passwordHashPort,
            OperatorTokenPort operatorTokenPort) {
        return new OperatorAuthApplicationService(
                operatorAccountRepository,
                operatorRefreshSessionRepository,
                passwordHashPort,
                operatorTokenPort);
    }
}
