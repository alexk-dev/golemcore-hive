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

package me.golemcore.hive.selfevolving.adapter.config;

import me.golemcore.hive.selfevolving.application.port.out.SelfEvolvingReadPort;
import me.golemcore.hive.selfevolving.application.port.out.SelfEvolvingWritePort;
import me.golemcore.hive.selfevolving.application.service.SelfEvolvingReadApplicationService;
import me.golemcore.hive.selfevolving.application.service.SelfEvolvingWriteApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SelfEvolvingApplicationConfiguration {

    @Bean
    public SelfEvolvingReadApplicationService selfEvolvingReadApplicationService(
            SelfEvolvingReadPort selfEvolvingReadPort) {
        return new SelfEvolvingReadApplicationService(selfEvolvingReadPort);
    }

    @Bean
    public SelfEvolvingWriteApplicationService selfEvolvingWriteApplicationService(
            SelfEvolvingWritePort selfEvolvingWritePort) {
        return new SelfEvolvingWriteApplicationService(selfEvolvingWritePort);
    }
}
