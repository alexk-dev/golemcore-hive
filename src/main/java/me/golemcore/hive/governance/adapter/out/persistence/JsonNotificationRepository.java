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

package me.golemcore.hive.governance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.governance.application.port.out.NotificationRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonNotificationRepository implements NotificationRepositoryPort {

    private static final String NOTIFICATIONS_DIR = "notifications";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public void save(NotificationEvent notificationEvent) {
        try {
            storagePort.putTextAtomic(
                    NOTIFICATIONS_DIR,
                    notificationEvent.getId() + ".json",
                    objectMapper.writeValueAsString(notificationEvent));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification " + notificationEvent.getId(), exception);
        }
    }

    @Override
    public Optional<NotificationEvent> findById(String notificationId) {
        String content = storagePort.getText(NOTIFICATIONS_DIR, notificationId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, NotificationEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize notification " + notificationId, exception);
        }
    }

    @Override
    public List<NotificationEvent> findAll() {
        List<NotificationEvent> notificationEvents = new ArrayList<>();
        for (String path : storagePort.listObjects(NOTIFICATIONS_DIR, "")) {
            String content = storagePort.getText(NOTIFICATIONS_DIR, path);
            if (content == null) {
                continue;
            }
            try {
                notificationEvents.add(objectMapper.readValue(content, NotificationEvent.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to deserialize notification " + path, exception);
            }
        }
        return notificationEvents;
    }
}
