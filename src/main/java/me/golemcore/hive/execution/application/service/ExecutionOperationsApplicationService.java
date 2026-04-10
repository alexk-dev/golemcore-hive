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

package me.golemcore.hive.execution.application.service;

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
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.out.ExecutionOperationsPort;

public class ExecutionOperationsApplicationService implements ExecutionOperationsUseCase {

    private final ExecutionOperationsPort executionOperationsPort;

    public ExecutionOperationsApplicationService(ExecutionOperationsPort executionOperationsPort) {
        this.executionOperationsPort = executionOperationsPort;
    }

    @Override
    public List<CommandRecord> listAllCommands() {
        return executionOperationsPort.listAllCommands();
    }

    @Override
    public List<CommandRecord> listCommands(String threadId) {
        return executionOperationsPort.listCommands(threadId);
    }

    @Override
    public CommandRecord createCommand(
            String threadId,
            String body,
            ApprovalRiskLevel requestedRiskLevel,
            long estimatedCostMicros,
            String approvalReason,
            String operatorId,
            String operatorName) {
        return executionOperationsPort.createCommand(
                threadId,
                body,
                requestedRiskLevel,
                estimatedCostMicros,
                approvalReason,
                operatorId,
                operatorName);
    }

    @Override
    public CommandRecord createDirectCommand(String threadId, String body, String operatorId, String operatorName) {
        return executionOperationsPort.createDirectCommand(threadId, body, operatorId, operatorName);
    }

    @Override
    public RunProjection requestRunCancellation(String threadId, String runId, String operatorId, String operatorName) {
        return executionOperationsPort.requestRunCancellation(threadId, runId, operatorId, operatorName);
    }

    @Override
    public List<RunProjection> listAllRuns() {
        return executionOperationsPort.listAllRuns();
    }

    @Override
    public List<RunProjection> listRuns(String threadId) {
        return executionOperationsPort.listRuns(threadId);
    }

    @Override
    public List<CardLifecycleSignal> listSignals(String threadId) {
        return executionOperationsPort.listSignals(threadId);
    }

    @Override
    public Map<String, CardControlStateSnapshot> listActiveCardControlStates(List<Card> cards) {
        return executionOperationsPort.listActiveCardControlStates(cards);
    }

    @Override
    public void dispatchPendingCommands(String golemId) {
        executionOperationsPort.dispatchPendingCommands(golemId);
    }

    @Override
    public void dispatchApprovedCommand(String commandId) {
        executionOperationsPort.dispatchApprovedCommand(commandId);
    }

    @Override
    public void applyRuntimeEvent(
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
            Long accumulatedCostMicros) {
        executionOperationsPort.applyRuntimeEvent(
                golemId,
                runtimeEventType,
                threadId,
                cardId,
                commandId,
                runId,
                summary,
                details,
                createdAt,
                inputTokens,
                outputTokens,
                accumulatedCostMicros);
    }

    @Override
    public void applyLifecycleSignal(CardLifecycleSignal signal) {
        executionOperationsPort.applyLifecycleSignal(signal);
    }
}
