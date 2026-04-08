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
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.threads.CommandRecordResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.CreateThreadCommandRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.DirectThreadResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.PaginatedMessagesResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.PostThreadMessageRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.RunProjectionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.ThreadMessageResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.domain.service.CommandDispatchService;
import me.golemcore.hive.domain.service.ThreadService;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/golems/{golemId}/dm")
@RequiredArgsConstructor
public class DirectMessagesController {

    private final ThreadService threadService;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final CommandDispatchService commandDispatchService;

    @GetMapping
    public Mono<ResponseEntity<DirectThreadResponse>> getDirectThread(Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            return ResponseEntity.ok(toDirectThreadResponse(thread, golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/messages")
    public Mono<ResponseEntity<PaginatedMessagesResponse>> listMessages(
            Principal principal,
            @PathVariable String golemId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            Instant beforeInstant = before != null ? Instant.parse(before) : null;
            ThreadService.MessagePage page = threadService.listMessagesPaginated(
                    thread.getId(), Math.min(limit, 200), beforeInstant);
            List<ThreadMessageResponse> messages = page.messages().stream()
                    .map(this::toThreadMessageResponse)
                    .toList();
            return ResponseEntity.ok(new PaginatedMessagesResponse(messages, page.hasMore()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/messages")
    public Mono<ResponseEntity<ThreadMessageResponse>> postMessage(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody PostThreadMessageRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            ThreadMessage message = threadService.postOperatorMessage(thread.getId(), request.body(),
                    actor.getSubjectId(), actor.getName());
            return ResponseEntity.ok(toThreadMessageResponse(message));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/commands")
    public Mono<ResponseEntity<CommandRecordResponse>> createCommand(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody CreateThreadCommandRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            CommandDispatchService.DispatchResult result = commandDispatchService.createDirectCommand(
                    thread.getId(), request.body(), actor.getSubjectId(), actor.getName());
            return ResponseEntity.ok(toCommandResponse(result.getCommand()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/commands")
    public Mono<ResponseEntity<List<CommandRecordResponse>>> listCommands(Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            List<CommandRecordResponse> response = commandDispatchService.listCommands(thread.getId()).stream()
                    .map(this::toCommandResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/runs")
    public Mono<ResponseEntity<List<RunProjectionResponse>>> listRuns(Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Golem golem = golemDirectoryUseCase.findGolem(golemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
            ThreadRecord thread = threadService.getOrCreateDirectThread(golem.getId(), golem.getDisplayName());
            List<RunProjectionResponse> response = commandDispatchService.listRuns(thread.getId()).stream()
                    .map(this::toRunResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private DirectThreadResponse toDirectThreadResponse(ThreadRecord thread, Golem golem) {
        return new DirectThreadResponse(
                thread.getId(),
                golem.getId(),
                golem.getDisplayName(),
                golem.getState().name(),
                thread.getTitle(),
                thread.getCreatedAt(),
                thread.getUpdatedAt(),
                thread.getLastMessageAt(),
                thread.getLastCommandAt());
    }

    private ThreadMessageResponse toThreadMessageResponse(ThreadMessage message) {
        return new ThreadMessageResponse(
                message.getId(),
                message.getThreadId(),
                message.getCardId(),
                message.getCommandId(),
                message.getRunId(),
                message.getSignalId(),
                message.getType().name(),
                message.getParticipantType().name(),
                message.getAuthorId(),
                message.getAuthorName(),
                message.getBody(),
                message.getCreatedAt());
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
}
