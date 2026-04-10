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

package me.golemcore.hive.governance.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.out.AuditEventRepositoryPort;

public class AuditLogApplicationService implements AuditLogUseCase {

    private final AuditEventRepositoryPort auditEventRepositoryPort;

    public AuditLogApplicationService(AuditEventRepositoryPort auditEventRepositoryPort) {
        this.auditEventRepositoryPort = auditEventRepositoryPort;
    }

    @Override
    public AuditEvent record(AuditEvent event) {
        Instant now = Instant.now();
        AuditEvent normalizedEvent = AuditEvent.builder()
                .schemaVersion(event.getSchemaVersion())
                .id(event.getId() != null && !event.getId().isBlank()
                        ? event.getId()
                        : "audit_" + UUID.randomUUID().toString().replace("-", ""))
                .eventType(event.getEventType())
                .severity(event.getSeverity())
                .actorType(event.getActorType())
                .actorId(event.getActorId())
                .actorName(event.getActorName())
                .targetType(event.getTargetType())
                .targetId(event.getTargetId())
                .boardId(event.getBoardId())
                .cardId(event.getCardId())
                .threadId(event.getThreadId())
                .golemId(event.getGolemId())
                .commandId(event.getCommandId())
                .runId(event.getRunId())
                .approvalId(event.getApprovalId())
                .summary(event.getSummary())
                .details(event.getDetails())
                .createdAt(event.getCreatedAt() != null ? event.getCreatedAt() : now)
                .build();
        auditEventRepositoryPort.save(normalizedEvent);
        return normalizedEvent;
    }

    @Override
    public AuditEvent record(AuditEvent.AuditEventBuilder builder) {
        return record(builder.build());
    }

    @Override
    public List<AuditEvent> listEvents(
            String actorId,
            String golemId,
            String boardId,
            String cardId,
            Instant from,
            Instant to,
            String eventType) {
        List<AuditEvent> auditEvents = auditEventRepositoryPort.findAll().stream()
                .filter(event -> actorId == null || actorId.isBlank() || actorId.equals(event.getActorId()))
                .filter(event -> golemId == null || golemId.isBlank() || golemId.equals(event.getGolemId()))
                .filter(event -> boardId == null || boardId.isBlank() || boardId.equals(event.getBoardId()))
                .filter(event -> cardId == null || cardId.isBlank() || cardId.equals(event.getCardId()))
                .filter(event -> eventType == null || eventType.isBlank() || eventType.equals(event.getEventType()))
                .filter(event -> from == null || event.getCreatedAt() == null || !event.getCreatedAt().isBefore(from))
                .filter(event -> to == null || event.getCreatedAt() == null || !event.getCreatedAt().isAfter(to))
                .sorted(Comparator.comparing(AuditEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AuditEvent::getId, Comparator.reverseOrder()))
                .toList();
        return auditEvents;
    }
}
