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

package me.golemcore.hive.adapter.inbound.web.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.threads.CardLifecycleSignalResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.CommandRecordResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.CreateThreadCommandRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.EvidenceRefResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.RunProjectionResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/threads/{threadId}")
@RequiredArgsConstructor
public class CommandsController {

    private final ExecutionOperationsUseCase executionOperationsUseCase;

    @GetMapping("/commands")
    public Mono<ResponseEntity<List<CommandRecordResponse>>> listCommands(Principal principal,
            @PathVariable String threadId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<CommandRecordResponse> response = executionOperationsUseCase.listCommands(threadId).stream()
                    .map(this::toCommandResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/commands")
    public Mono<ResponseEntity<CommandRecordResponse>> createCommand(
            Principal principal,
            @PathVariable String threadId,
            @Valid @RequestBody CreateThreadCommandRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            ApprovalRiskLevel riskLevel = request.approvalRiskLevel() != null && !request.approvalRiskLevel().isBlank()
                    ? ApprovalRiskLevel.valueOf(request.approvalRiskLevel())
                    : null;
            CommandRecord command = executionOperationsUseCase.createCommand(
                    threadId,
                    request.body(),
                    riskLevel,
                    request.estimatedCostMicros() != null ? request.estimatedCostMicros() : 0L,
                    request.approvalReason(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toCommandResponse(command));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/runs/{runId}/cancel")
    public Mono<ResponseEntity<RunProjectionResponse>> cancelRun(
            Principal principal,
            @PathVariable String threadId,
            @PathVariable String runId) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            RunProjection run = executionOperationsUseCase.requestRunCancellation(
                    threadId,
                    runId,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toRunResponse(run));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/runs")
    public Mono<ResponseEntity<List<RunProjectionResponse>>> listRuns(Principal principal,
            @PathVariable String threadId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<RunProjectionResponse> response = executionOperationsUseCase.listRuns(threadId).stream()
                    .map(this::toRunResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/signals")
    public Mono<ResponseEntity<List<CardLifecycleSignalResponse>>> listSignals(Principal principal,
            @PathVariable String threadId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<CardLifecycleSignalResponse> response = executionOperationsUseCase.listSignals(threadId).stream()
                    .map(this::toSignalResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private CommandRecordResponse toCommandResponse(CommandRecord command) {
        return new CommandRecordResponse(
                command.getId(),
                command.getThreadId(),
                command.getCardId(),
                command.getGolemId(),
                command.getRunId(),
                command.getBody(),
                command.getApprovalRequestId(),
                command.getApprovalRiskLevel() != null ? command.getApprovalRiskLevel().name() : null,
                command.getApprovalReason(),
                command.getEstimatedCostMicros(),
                command.getStatus().name(),
                command.getQueueReason(),
                command.getDispatchAttempts(),
                command.getCreatedAt(),
                command.getUpdatedAt(),
                command.getLastDispatchAttemptAt(),
                command.getDeliveredAt(),
                command.getStartedAt(),
                command.getCompletedAt(),
                command.getCancelRequestedAt(),
                command.getCancelRequestedByActorName());
    }

    private RunProjectionResponse toRunResponse(RunProjection run) {
        return new RunProjectionResponse(
                run.getId(),
                run.getThreadId(),
                run.getCardId(),
                run.getCommandId(),
                run.getGolemId(),
                run.getApprovalRequestId(),
                run.getStatus().name(),
                run.getSummary(),
                run.getLastRuntimeEventType(),
                run.getLastSignalType(),
                run.getEventCount(),
                run.getInputTokens(),
                run.getOutputTokens(),
                run.getAccumulatedCostMicros(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCancelRequestedAt(),
                run.getCancelRequestedByActorName());
    }

    private CardLifecycleSignalResponse toSignalResponse(CardLifecycleSignal signal) {
        return new CardLifecycleSignalResponse(
                signal.getId(),
                signal.getCardId(),
                signal.getGolemId(),
                signal.getCommandId(),
                signal.getRunId(),
                signal.getThreadId(),
                signal.getSignalType().name(),
                signal.getSummary(),
                signal.getDetails(),
                signal.getBlockerCode(),
                signal.getEvidenceRefs().stream().map(ref -> new EvidenceRefResponse(ref.getKind(), ref.getRef()))
                        .toList(),
                signal.getCreatedAt(),
                signal.getDecision() != null ? signal.getDecision().name() : null,
                signal.getResolvedTargetColumnId(),
                signal.getResolutionOutcome() != null ? signal.getResolutionOutcome().name() : null,
                signal.getResolutionSummary(),
                signal.getResolvedAt());
    }
}
