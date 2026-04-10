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

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.governance.application.port.out.ApprovalThreadPort;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceApprovalThreadAdapter implements ApprovalThreadPort {

    private final ThreadWorkflowUseCase threadWorkflowUseCase;

    @Override
    public void appendCommandStatusMessage(
            String threadId,
            String commandId,
            String runId,
            String actorId,
            String actorName,
            String message,
            Instant createdAt) {
        ThreadRecord thread = threadWorkflowUseCase.getThread(threadId);
        threadWorkflowUseCase.appendMessage(
                thread,
                commandId,
                runId,
                null,
                ThreadMessageType.COMMAND_STATUS,
                ThreadParticipantType.SYSTEM,
                actorId,
                actorName,
                message,
                createdAt);
    }
}
