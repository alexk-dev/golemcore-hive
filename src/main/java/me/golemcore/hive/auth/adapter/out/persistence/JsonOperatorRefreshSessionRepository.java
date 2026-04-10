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

package me.golemcore.hive.auth.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.auth.application.port.out.OperatorRefreshSessionRepository;
import me.golemcore.hive.domain.model.RefreshSession;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonOperatorRefreshSessionRepository implements OperatorRefreshSessionRepository {

    private static final String REFRESH_DIR = "auth/refresh-sessions";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<RefreshSession> findById(String sessionId) {
        String content = storagePort.getText(REFRESH_DIR, sessionId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, RefreshSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize refresh session " + sessionId, exception);
        }
    }

    @Override
    public void save(RefreshSession refreshSession) {
        try {
            storagePort.putTextAtomic(
                    REFRESH_DIR,
                    refreshSession.getId() + ".json",
                    objectMapper.writeValueAsString(refreshSession));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize refresh session " + refreshSession.getId(),
                    exception);
        }
    }

    @Override
    public void deleteById(String sessionId) {
        storagePort.delete(REFRESH_DIR, sessionId + ".json");
    }
}
