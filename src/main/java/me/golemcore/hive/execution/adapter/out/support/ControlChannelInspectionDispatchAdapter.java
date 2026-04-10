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
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.execution.application.port.out.InspectionDispatchPort;
import me.golemcore.hive.infrastructure.control.GolemControlDispatchPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControlChannelInspectionDispatchAdapter implements InspectionDispatchPort {

    private final GolemControlDispatchPort golemControlDispatchPort;

    @Override
    public boolean isConnected(String golemId) {
        return golemControlDispatchPort.isConnected(golemId);
    }

    @Override
    public boolean send(String golemId, ControlCommandEnvelope envelope) {
        return golemControlDispatchPort.send(golemId, envelope);
    }
}
