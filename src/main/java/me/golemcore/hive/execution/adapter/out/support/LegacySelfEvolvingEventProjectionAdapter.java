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

package me.golemcore.hive.execution.adapter.out.support;

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.execution.application.GolemEventCommand;
import me.golemcore.hive.execution.application.port.out.SelfEvolvingEventProjectionPort;
import me.golemcore.hive.selfevolving.application.SelfEvolvingProjectionEvent;
import me.golemcore.hive.selfevolving.application.port.in.SelfEvolvingWriteUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacySelfEvolvingEventProjectionAdapter implements SelfEvolvingEventProjectionPort {

    private final SelfEvolvingWriteUseCase selfEvolvingWriteUseCase;

    @Override
    public void applyEvent(String golemId, GolemEventCommand event) {
        selfEvolvingWriteUseCase.applyEvent(new SelfEvolvingProjectionEvent(
                event.eventType(),
                golemId,
                event.runId(),
                event.payload(),
                event.createdAt()));
    }
}
