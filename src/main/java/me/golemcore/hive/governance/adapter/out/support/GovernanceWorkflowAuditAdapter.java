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
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceWorkflowAuditAdapter implements WorkflowAuditPort {

    private final AuditLogUseCase auditLogUseCase;

    @Override
    public AuditEvent record(AuditEvent.AuditEventBuilder builder) {
        return auditLogUseCase.record(builder);
    }
}
