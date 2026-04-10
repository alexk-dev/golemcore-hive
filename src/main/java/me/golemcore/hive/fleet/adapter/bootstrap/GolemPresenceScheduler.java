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

package me.golemcore.hive.fleet.adapter.bootstrap;

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.fleet.application.port.in.EvaluateGolemPresenceUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GolemPresenceScheduler {

    private final EvaluateGolemPresenceUseCase evaluateGolemPresenceUseCase;

    @Scheduled(fixedDelay = 15_000)
    public void evaluatePresence() {
        evaluateGolemPresenceUseCase.evaluatePresence();
    }
}
