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

package me.golemcore.hive.fleet.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.fleet.application.port.out.HeartbeatRepository;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonHeartbeatRepository implements HeartbeatRepository {

    private static final String HEARTBEATS_DIR = "heartbeats";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public void save(HeartbeatPing heartbeatPing) {
        try {
            storagePort.putTextAtomic(HEARTBEATS_DIR, heartbeatPing.getGolemId() + ".json",
                    objectMapper.writeValueAsString(heartbeatPing));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize heartbeat for " + heartbeatPing.getGolemId(),
                    exception);
        }
    }
}
