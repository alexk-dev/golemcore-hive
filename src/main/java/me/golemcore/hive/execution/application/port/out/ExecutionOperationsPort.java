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

package me.golemcore.hive.execution.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardControlStateSnapshot;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.RuntimeEventType;

public interface ExecutionOperationsPort {

    List<CommandRecord> listAllCommands();

    List<CommandRecord> listCommands(String threadId);

    CommandRecord createCommand(
            String threadId,
            String body,
            ApprovalRiskLevel requestedRiskLevel,
            long estimatedCostMicros,
            String approvalReason,
            String operatorId,
            String operatorName);

    CommandRecord createDirectCommand(String threadId, String body, String operatorId, String operatorName);

    RunProjection requestRunCancellation(String threadId, String runId, String operatorId, String operatorName);

    List<RunProjection> listAllRuns();

    List<RunProjection> listRuns(String threadId);

    List<CardLifecycleSignal> listSignals(String threadId);

    Map<String, CardControlStateSnapshot> listActiveCardControlStates(List<Card> cards);

    void dispatchPendingCommands(String golemId);

    void dispatchApprovedCommand(String commandId);

    void applyRuntimeEvent(
            String golemId,
            RuntimeEventType runtimeEventType,
            String threadId,
            String cardId,
            String commandId,
            String runId,
            String summary,
            String details,
            Instant createdAt,
            Long inputTokens,
            Long outputTokens,
            Long accumulatedCostMicros);

    void applyLifecycleSignal(CardLifecycleSignal signal);
}
