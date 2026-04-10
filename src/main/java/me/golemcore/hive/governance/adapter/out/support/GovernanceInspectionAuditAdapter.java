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
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionRpcResponse;
import me.golemcore.hive.execution.application.InspectionActor;
import me.golemcore.hive.execution.application.InspectionOperationException;
import me.golemcore.hive.execution.application.port.out.InspectionAuditPort;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceInspectionAuditAdapter implements InspectionAuditPort {

    private final AuditLogUseCase auditLogUseCase;

    @Override
    public void recordSuccess(
            InspectionActor actor,
            String golemId,
            InspectionRequestBody requestBody,
            InspectionRpcResponse response) {
        auditLogUseCase.record(AuditEvent.builder()
                .eventType("golem.inspection.requested")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actor.actorId())
                .actorName(actor.actorName())
                .targetType("GOLEM")
                .targetId(golemId)
                .golemId(golemId)
                .summary("Inspection operation completed")
                .details(requestBody.getOperation() + " requestId=" + response.requestId()));
    }

    @Override
    public void recordFailure(
            InspectionActor actor,
            String golemId,
            InspectionRequestBody requestBody,
            InspectionOperationException exception) {
        auditLogUseCase.record(AuditEvent.builder()
                .eventType("golem.inspection.failed")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actor.actorId())
                .actorName(actor.actorName())
                .targetType("GOLEM")
                .targetId(golemId)
                .golemId(golemId)
                .summary("Inspection operation failed")
                .details(requestBody.getOperation() + " " + exception.getCode() + " " + exception.getMessage()));
    }
}
