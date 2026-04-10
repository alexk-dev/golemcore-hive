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
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.execution.application.ExecutionApprovalRequest;
import me.golemcore.hive.execution.application.port.out.ExecutionApprovalPort;
import me.golemcore.hive.governance.application.ApprovalCommandRequest;
import me.golemcore.hive.governance.application.port.in.ApprovalWorkflowUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceExecutionApprovalAdapter implements ExecutionApprovalPort {

    private final ApprovalWorkflowUseCase approvalWorkflowUseCase;

    @Override
    public ApprovalRiskLevel resolveApprovalRisk(ApprovalRiskLevel requestedRiskLevel, long estimatedCostMicros) {
        return approvalWorkflowUseCase.resolveApprovalRisk(requestedRiskLevel, estimatedCostMicros);
    }

    @Override
    public String requestApproval(ExecutionApprovalRequest request, String actorId, String actorName) {
        return approvalWorkflowUseCase.createApproval(
                new ApprovalCommandRequest(
                        request.commandId(),
                        request.runId(),
                        request.threadId(),
                        request.boardId(),
                        request.cardId(),
                        request.golemId(),
                        request.commandBody(),
                        request.approvalRiskLevel(),
                        request.approvalReason(),
                        request.estimatedCostMicros()),
                actorId,
                actorName).getId();
    }
}
