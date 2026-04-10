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

package me.golemcore.hive.governance.adapter.out.support;

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.fleet.application.port.out.FleetAuditPort;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceFleetAuditAdapter implements FleetAuditPort {

    private final AuditLogUseCase auditLogUseCase;

    @Override
    public void record(AuditEvent auditEvent) {
        auditLogUseCase.record(AuditEvent.builder()
                .id(auditEvent.getId())
                .schemaVersion(auditEvent.getSchemaVersion())
                .eventType(auditEvent.getEventType())
                .severity(auditEvent.getSeverity())
                .actorType(auditEvent.getActorType())
                .actorId(auditEvent.getActorId())
                .actorName(auditEvent.getActorName())
                .targetType(auditEvent.getTargetType())
                .targetId(auditEvent.getTargetId())
                .boardId(auditEvent.getBoardId())
                .cardId(auditEvent.getCardId())
                .threadId(auditEvent.getThreadId())
                .golemId(auditEvent.getGolemId())
                .commandId(auditEvent.getCommandId())
                .runId(auditEvent.getRunId())
                .approvalId(auditEvent.getApprovalId())
                .summary(auditEvent.getSummary())
                .details(auditEvent.getDetails())
                .createdAt(auditEvent.getCreatedAt()));
    }
}
