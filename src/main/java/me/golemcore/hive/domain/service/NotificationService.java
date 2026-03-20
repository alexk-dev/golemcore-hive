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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.port.outbound.NotificationPort;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String NOTIFICATIONS_DIR = "notifications";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final NotificationPort notificationPort;
    private final HiveProperties properties;

    public NotificationEvent create(NotificationEvent.NotificationEventBuilder builder) {
        Instant now = Instant.now();
        NotificationEvent event = builder
                .id("ntf_" + UUID.randomUUID().toString().replace("-", ""))
                .createdAt(builder.build().getCreatedAt() != null ? builder.build().getCreatedAt() : now)
                .severity(builder.build().getSeverity() != null ? builder.build().getSeverity()
                        : NotificationSeverity.INFO)
                .build();
        saveEvent(event);
        notificationPort.deliver(event);
        return event;
    }

    public List<NotificationEvent> listNotifications() {
        List<NotificationEvent> events = new ArrayList<>();
        for (String path : storagePort.listObjects(NOTIFICATIONS_DIR, "")) {
            Optional<NotificationEvent> eventOptional = loadEvent(path);
            eventOptional.ifPresent(events::add);
        }
        events.sort(
                Comparator.comparing(NotificationEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NotificationEvent::getId, Comparator.reverseOrder()));
        return events;
    }

    public NotificationEvent acknowledge(String notificationId) {
        NotificationEvent event = loadEvent(notificationId + ".json")
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
        event.setAcknowledged(true);
        event.setAcknowledgedAt(Instant.now());
        saveEvent(event);
        return event;
    }

    public boolean isApprovalRequestedEnabled() {
        return properties.getGovernance().getNotifications().isApprovalRequested();
    }

    public boolean isBlockerRaisedEnabled() {
        return properties.getGovernance().getNotifications().isBlockerRaised();
    }

    public boolean isGolemOfflineEnabled() {
        return properties.getGovernance().getNotifications().isGolemOffline();
    }

    public boolean isCommandFailedEnabled() {
        return properties.getGovernance().getNotifications().isCommandFailed();
    }

    private Optional<NotificationEvent> loadEvent(String path) {
        String content = storagePort.getText(NOTIFICATIONS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, NotificationEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize notification " + path, exception);
        }
    }

    private void saveEvent(NotificationEvent event) {
        try {
            storagePort.putTextAtomic(NOTIFICATIONS_DIR, event.getId() + ".json",
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification " + event.getId(), exception);
        }
    }
}
