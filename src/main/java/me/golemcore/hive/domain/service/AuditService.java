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
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String AUDIT_DIR = "audit";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    public AuditEvent record(AuditEvent.AuditEventBuilder builder) {
        Instant now = Instant.now();
        AuditEvent event = builder
                .id("audit_" + UUID.randomUUID().toString().replace("-", ""))
                .createdAt(builder.build().getCreatedAt() != null ? builder.build().getCreatedAt() : now)
                .build();
        saveEvent(event);
        return event;
    }

    public List<AuditEvent> listEvents(String actorId,
            String golemId,
            String boardId,
            String cardId,
            Instant from,
            Instant to,
            String eventType) {
        List<AuditEvent> events = new ArrayList<>();
        for (String path : storagePort.listObjects(AUDIT_DIR, "")) {
            Optional<AuditEvent> eventOptional = loadEvent(path);
            if (eventOptional.isEmpty()) {
                continue;
            }
            AuditEvent event = eventOptional.get();
            if (actorId != null && !actorId.isBlank() && !actorId.equals(event.getActorId())) {
                continue;
            }
            if (golemId != null && !golemId.isBlank() && !golemId.equals(event.getGolemId())) {
                continue;
            }
            if (boardId != null && !boardId.isBlank() && !boardId.equals(event.getBoardId())) {
                continue;
            }
            if (cardId != null && !cardId.isBlank() && !cardId.equals(event.getCardId())) {
                continue;
            }
            if (eventType != null && !eventType.isBlank() && !eventType.equals(event.getEventType())) {
                continue;
            }
            if (from != null && event.getCreatedAt() != null && event.getCreatedAt().isBefore(from)) {
                continue;
            }
            if (to != null && event.getCreatedAt() != null && event.getCreatedAt().isAfter(to)) {
                continue;
            }
            events.add(event);
        }
        events.sort(Comparator.comparing(AuditEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AuditEvent::getId, Comparator.reverseOrder()));
        return events;
    }

    private Optional<AuditEvent> loadEvent(String path) {
        String content = storagePort.getText(AUDIT_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, AuditEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize audit event " + path, exception);
        }
    }

    private void saveEvent(AuditEvent event) {
        try {
            storagePort.putTextAtomic(AUDIT_DIR, event.getId() + ".json", objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit event " + event.getId(), exception);
        }
    }
}
