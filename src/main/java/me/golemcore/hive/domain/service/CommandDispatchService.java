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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.CardControlStateSnapshot;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.domain.model.OperatorUpdate;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.RunStatus;
import me.golemcore.hive.domain.model.RuntimeEventType;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandDispatchService {

    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";
    private static final String CONTROL_EVENT_TYPE_COMMAND = "command";
    private static final String CONTROL_EVENT_TYPE_CANCEL = "command.cancel";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final ThreadService threadService;
    private final CardService cardService;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final GolemControlChannelService golemControlChannelService;
    private final OperatorUpdatesService operatorUpdatesService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final BudgetService budgetService;
    private final NotificationService notificationService;

    public DispatchResult createCommand(String threadId,
            String body,
            ApprovalRiskLevel requestedRiskLevel,
            long estimatedCostMicros,
            String approvalReason,
            String operatorId,
            String operatorName) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Command body is required");
        }

        ThreadRecord thread = threadService.getThread(threadId);
        Card card = cardService.getCard(thread.getCardId());
        if (card.getAssigneeGolemId() == null || card.getAssigneeGolemId().isBlank()) {
            throw new IllegalArgumentException("Card must be assigned before dispatching commands");
        }
        Golem golem = golemDirectoryUseCase.findGolem(card.getAssigneeGolemId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown assignee golem: " + card.getAssigneeGolemId()));

        Instant now = Instant.now();
        String commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "");
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");

        RunProjection run = RunProjection.builder()
                .id(runId)
                .threadId(thread.getId())
                .cardId(card.getId())
                .commandId(commandId)
                .golemId(golem.getId())
                .status(RunStatus.QUEUED)
                .summary(body)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ApprovalRiskLevel approvalRiskLevel = approvalService.resolveApprovalRisk(requestedRiskLevel,
                estimatedCostMicros);
        if (approvalRiskLevel != null) {
            run.setStatus(RunStatus.PENDING_APPROVAL);
        }

        CommandRecord command = CommandRecord.builder()
                .id(commandId)
                .threadId(thread.getId())
                .cardId(card.getId())
                .golemId(golem.getId())
                .runId(runId)
                .body(body)
                .approvalRiskLevel(approvalRiskLevel)
                .approvalReason(approvalReason)
                .estimatedCostMicros(estimatedCostMicros)
                .status(approvalRiskLevel != null ? CommandStatus.PENDING_APPROVAL : CommandStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .dispatchAttempts(0)
                .build();

        saveRun(run);
        saveCommand(command);

        threadService.appendMessage(thread, commandId, runId, null, ThreadMessageType.COMMAND_REQUEST,
                ThreadParticipantType.OPERATOR, operatorId, operatorName, body, now);

        if (approvalRiskLevel != null) {
            ApprovalRequest approval = approvalService.createApproval(command, run, card, operatorId, operatorName);
            command.setApprovalRequestId(approval.getId());
            run.setApprovalRequestId(approval.getId());
            saveCommand(command);
            saveRun(run);
            auditService.record(AuditEvent.builder()
                    .eventType("command.created")
                    .severity("WARN")
                    .actorType("OPERATOR")
                    .actorId(operatorId)
                    .actorName(operatorName)
                    .targetType("COMMAND")
                    .targetId(command.getId())
                    .boardId(card.getBoardId())
                    .cardId(card.getId())
                    .threadId(thread.getId())
                    .golemId(golem.getId())
                    .commandId(command.getId())
                    .runId(run.getId())
                    .approvalId(approval.getId())
                    .summary("Command queued for approval")
                    .details(approvalRiskLevel + ": " + firstNonBlank(approvalReason, "Approval required")));
        } else {
            dispatchToControlChannel(command, run, now, golem);
            saveCommand(command);
            saveRun(run);
            recordDispatchAudit(command, run, card, operatorId, operatorName);
        }

        threadService.markCommandCreated(thread, golem.getId(), now);

        operatorUpdatesService.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .cardId(card.getId())
                .threadId(thread.getId())
                .commandId(command.getId())
                .runId(run.getId())
                .kinds(List.of("command", "run", "thread_message"))
                .createdAt(now)
                .build());

        budgetService.refreshSnapshots();

        return new DispatchResult(command, run);
    }

    public DispatchResult createDirectCommand(String threadId,
            String body,
            String operatorId,
            String operatorName) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Command body is required");
        }

        ThreadRecord thread = threadService.getThread(threadId);
        if (thread.getAssignedGolemId() == null || thread.getAssignedGolemId().isBlank()) {
            throw new IllegalArgumentException("DM thread has no assigned golem");
        }
        Golem golem = golemDirectoryUseCase.findGolem(thread.getAssignedGolemId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown golem: " + thread.getAssignedGolemId()));

        Instant now = Instant.now();
        String commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "");
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");

        RunProjection run = RunProjection.builder()
                .id(runId)
                .threadId(thread.getId())
                .commandId(commandId)
                .golemId(golem.getId())
                .status(RunStatus.QUEUED)
                .summary(body)
                .createdAt(now)
                .updatedAt(now)
                .build();

        CommandRecord command = CommandRecord.builder()
                .id(commandId)
                .threadId(thread.getId())
                .golemId(golem.getId())
                .runId(runId)
                .body(body)
                .status(CommandStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .dispatchAttempts(0)
                .build();

        saveRun(run);
        saveCommand(command);

        threadService.appendMessage(thread, commandId, runId, null, ThreadMessageType.COMMAND_REQUEST,
                ThreadParticipantType.OPERATOR, operatorId, operatorName, body, now);

        dispatchToControlChannel(command, run, now, golem);
        saveCommand(command);
        saveRun(run);

        auditService.record(AuditEvent.builder()
                .eventType("command.dispatch")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(operatorId)
                .actorName(operatorName)
                .targetType("COMMAND")
                .targetId(command.getId())
                .threadId(thread.getId())
                .golemId(golem.getId())
                .commandId(command.getId())
                .runId(run.getId())
                .summary("Direct command dispatched")
                .details(body));

        threadService.markCommandCreated(thread, golem.getId(), now);

        operatorUpdatesService.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .threadId(thread.getId())
                .commandId(command.getId())
                .runId(run.getId())
                .kinds(List.of("command", "run", "thread_message"))
                .createdAt(now)
                .build());

        budgetService.refreshSnapshots();

        return new DispatchResult(command, run);
    }

    public List<CommandRecord> listCommands(String threadId) {
        List<CommandRecord> commands = new ArrayList<>();
        for (String path : storagePort.listObjects(COMMANDS_DIR, "")) {
            Optional<CommandRecord> commandOptional = loadCommand(path);
            if (commandOptional.isEmpty()) {
                continue;
            }
            CommandRecord command = commandOptional.get();
            if (threadId.equals(command.getThreadId())) {
                commands.add(command);
            }
        }
        commands.sort(Comparator.comparing(CommandRecord::getCreatedAt).thenComparing(CommandRecord::getId));
        return commands;
    }

    public List<RunProjection> listRuns(String threadId) {
        List<RunProjection> runs = new ArrayList<>();
        for (String path : storagePort.listObjects(RUNS_DIR, "")) {
            Optional<RunProjection> runOptional = loadRun(path);
            if (runOptional.isEmpty()) {
                continue;
            }
            RunProjection run = runOptional.get();
            if (threadId.equals(run.getThreadId())) {
                runs.add(run);
            }
        }
        runs.sort(Comparator.comparing(RunProjection::getCreatedAt).thenComparing(RunProjection::getId));
        return runs;
    }

    public Map<String, CardControlStateSnapshot> listActiveCardControlStates(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return Map.of();
        }

        Set<String> cardIds = cards.stream()
                .map(Card::getId)
                .filter(cardId -> cardId != null && !cardId.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (cardIds.isEmpty()) {
            return Map.of();
        }

        Map<String, CommandRecord> commandsById = new HashMap<>();
        for (CommandRecord command : listAllCommands()) {
            if (!cardIds.contains(command.getCardId())) {
                continue;
            }
            commandsById.put(command.getId(), command);
        }

        List<RunProjection> candidateRuns = listAllRuns().stream()
                .filter(run -> cardIds.contains(run.getCardId()))
                .filter(run -> !isTerminal(run.getStatus()))
                .sorted(Comparator
                        .comparing(this::controlStateUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(RunProjection::getId, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<String, CardControlStateSnapshot> snapshots = new HashMap<>();
        for (RunProjection run : candidateRuns) {
            if (snapshots.containsKey(run.getCardId())) {
                continue;
            }
            snapshots.put(run.getCardId(), toCardControlStateSnapshot(run, commandsById.get(run.getCommandId())));
        }
        return snapshots;
    }

    public RunProjection requestRunCancellation(String threadId,
            String runId,
            String operatorId,
            String operatorName) {
        RunProjection run = findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown run: " + runId));
        if (!threadId.equals(run.getThreadId())) {
            throw new IllegalArgumentException("Run does not belong to thread: " + threadId);
        }

        CommandRecord command = resolveCommand(run.getCommandId(), run);
        if (command == null) {
            throw new IllegalStateException("Run has no linked command: " + runId);
        }
        if (command.getStatus() == CommandStatus.PENDING_APPROVAL || run.getStatus() == RunStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Use the approvals workflow to reject commands waiting for approval");
        }
        if (isTerminal(command.getStatus()) || isTerminal(run.getStatus())) {
            throw new IllegalStateException("Run is already finished and cannot be cancelled");
        }
        if (isCancellationPending(command, run)) {
            throw new IllegalStateException("Cancellation is already pending confirmation from the golem");
        }

        ThreadRecord thread = threadService.getThread(threadId);
        Card card = cardService.getCard(run.getCardId());
        Instant now = Instant.now();

        if (command.getStatus() == CommandStatus.QUEUED && run.getStatus() == RunStatus.QUEUED) {
            command.setStatus(CommandStatus.CANCELLED);
            command.setQueueReason("Cancelled by operator before dispatch");
            command.setUpdatedAt(now);
            command.setCompletedAt(now);
            run.setStatus(RunStatus.CANCELLED);
            run.setUpdatedAt(now);
            run.setCompletedAt(now);
            saveCommand(command);
            saveRun(run);

            threadService.appendMessage(thread, command.getId(), run.getId(), null,
                    ThreadMessageType.COMMAND_STATUS, ThreadParticipantType.OPERATOR,
                    operatorId, operatorName, "Cancelled queued command before dispatch", now);
            auditService.record(AuditEvent.builder()
                    .eventType("command.cancelled")
                    .severity("INFO")
                    .actorType("OPERATOR")
                    .actorId(operatorId)
                    .actorName(operatorName)
                    .targetType("RUN")
                    .targetId(run.getId())
                    .boardId(card.getBoardId())
                    .cardId(card.getId())
                    .threadId(thread.getId())
                    .golemId(command.getGolemId())
                    .commandId(command.getId())
                    .runId(run.getId())
                    .summary("Queued command cancelled before dispatch")
                    .details(command.getBody()));
            publishThreadUpdate(card, thread, command, run, now, List.of("command", "run", "thread_message"));
            budgetService.refreshSnapshots();
            return run;
        }

        Golem golem = golemDirectoryUseCase.findGolem(command.getGolemId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + command.getGolemId()));
        ControlCommandEnvelope envelope = buildControlEnvelope(CONTROL_EVENT_TYPE_CANCEL, command, now, null);
        boolean delivered = golemControlChannelService.send(golem.getId(), toJson(envelope));
        if (!delivered) {
            throw new IllegalStateException("Control channel unavailable for golem: " + golem.getDisplayName());
        }

        command.setUpdatedAt(now);
        command.setCancelRequestedAt(now);
        command.setCancelRequestedByActorId(operatorId);
        command.setCancelRequestedByActorName(operatorName);
        run.setUpdatedAt(now);
        run.setCancelRequestedAt(now);
        run.setCancelRequestedByActorId(operatorId);
        run.setCancelRequestedByActorName(operatorName);
        saveCommand(command);
        saveRun(run);

        threadService.appendMessage(thread, command.getId(), run.getId(), null,
                ThreadMessageType.COMMAND_STATUS, ThreadParticipantType.OPERATOR,
                operatorId, operatorName, "Requested stop for active run", now);
        auditService.record(AuditEvent.builder()
                .eventType("command.cancel_requested")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(operatorId)
                .actorName(operatorName)
                .targetType("RUN")
                .targetId(run.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(thread.getId())
                .golemId(golem.getId())
                .commandId(command.getId())
                .runId(run.getId())
                .summary("Cancellation requested for active run")
                .details(command.getBody()));
        publishThreadUpdate(card, thread, command, run, now, List.of("thread_message", "command", "run"));
        return run;
    }

    public void dispatchPendingCommands(String golemId) {
        Golem golem = golemDirectoryUseCase.findGolem(golemId).orElse(null);
        if (golem == null) {
            return;
        }
        for (CommandRecord command : listCommandsForGolem(golemId)) {
            if (command.getStatus() != CommandStatus.QUEUED) {
                continue;
            }
            RunProjection run = findRun(command.getRunId()).orElse(null);
            dispatchToControlChannel(command, run, Instant.now(), golem);
            saveCommand(command);
            if (run != null) {
                saveRun(run);
            }
            if (command.getCardId() != null) {
                Card card = cardService.getCard(command.getCardId());
                recordDispatchAudit(command, run, card, "system", "Hive");
            } else {
                auditService.record(AuditEvent.builder()
                        .eventType("command.dispatch")
                        .severity("INFO")
                        .actorType("SYSTEM")
                        .actorId("system")
                        .actorName("Hive")
                        .targetType("COMMAND")
                        .targetId(command.getId())
                        .threadId(command.getThreadId())
                        .golemId(golem.getId())
                        .commandId(command.getId())
                        .runId(run != null ? run.getId() : null)
                        .summary(command.getStatus() == CommandStatus.DELIVERED
                                ? "Direct command dispatched on reconnect"
                                : "Direct command queued for delivery")
                        .details(command.getBody()));
            }
        }
        budgetService.refreshSnapshots();
    }

    public void applyRuntimeEvent(String golemId,
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
        Instant eventTime = createdAt != null ? createdAt : Instant.now();
        RunProjection run = resolveRunProjection(commandId, runId, threadId, cardId, golemId, eventTime);
        CommandRecord command = resolveCommand(commandId, run);
        ThreadRecord thread = resolveThread(threadId, run);
        String resolvedCardId = cardId != null ? cardId : run.getCardId();
        Card card = resolvedCardId != null ? cardService.getCard(resolvedCardId) : null;

        if (command != null) {
            command.setUpdatedAt(eventTime);
        }
        run.setUpdatedAt(eventTime);
        run.setEventCount(run.getEventCount() + 1);
        run.setLastRuntimeEventType(runtimeEventType.name());
        if (summary != null && !summary.isBlank()) {
            run.setSummary(summary);
        }
        if (inputTokens != null) {
            run.setInputTokens(inputTokens);
        }
        if (outputTokens != null) {
            run.setOutputTokens(outputTokens);
        }
        if (accumulatedCostMicros != null) {
            run.setAccumulatedCostMicros(accumulatedCostMicros);
        }

        switch (runtimeEventType) {
        case COMMAND_ACKNOWLEDGED -> {
            if (command != null && command.getStatus() == CommandStatus.QUEUED) {
                command.setStatus(CommandStatus.DELIVERED);
                command.setDeliveredAt(eventTime);
            }
        }
        case THREAD_MESSAGE ->
            threadService.appendMessage(thread, command != null ? command.getId() : commandId, run.getId(),
                    null, ThreadMessageType.GOLEM_RESPONSE, ThreadParticipantType.GOLEM, golemId, golemId,
                    firstNonBlank(details, summary, "Golem response"), eventTime);
        case RUN_STARTED, RUN_PROGRESS -> {
            run.setStatus(RunStatus.RUNNING);
            if (run.getStartedAt() == null) {
                run.setStartedAt(eventTime);
            }
            if (command != null) {
                command.setStatus(CommandStatus.RUNNING);
                if (command.getStartedAt() == null) {
                    command.setStartedAt(eventTime);
                }
            }
        }
        case RUN_COMPLETED -> {
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.COMPLETED);
                command.setCompletedAt(eventTime);
            }
        }
        case RUN_FAILED -> {
            run.setStatus(RunStatus.FAILED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.FAILED);
                command.setCompletedAt(eventTime);
            }
        }
        case RUN_CANCELLED -> {
            run.setStatus(RunStatus.CANCELLED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.CANCELLED);
                command.setCompletedAt(eventTime);
            }
        }
        case USAGE_REPORTED -> {
            // Usage counters were already applied above.
        }
        }

        if (summary != null && !summary.isBlank() && runtimeEventType != RuntimeEventType.THREAD_MESSAGE) {
            threadService.appendMessage(thread, command != null ? command.getId() : commandId, run.getId(), null,
                    ThreadMessageType.RUNTIME_EVENT, ThreadParticipantType.SYSTEM, golemId, golemId,
                    runtimeEventType.name() + ": " + summary, eventTime);
        }

        saveRun(run);
        if (command != null) {
            saveCommand(command);
        }

        if (runtimeEventType == RuntimeEventType.RUN_FAILED && notificationService.isCommandFailedEnabled()) {
            notificationService.create(NotificationEvent.builder()
                    .type("COMMAND_FAILED")
                    .severity(NotificationSeverity.CRITICAL)
                    .title("Command failed")
                    .message(firstNonBlank(summary, details, "A command execution failed"))
                    .cardId(card != null ? card.getId() : null)
                    .boardId(card != null ? card.getBoardId() : null)
                    .threadId(thread.getId())
                    .golemId(golemId)
                    .commandId(command != null ? command.getId() : commandId));
        }

        if (runtimeEventType == RuntimeEventType.RUN_FAILED
                || runtimeEventType == RuntimeEventType.RUN_COMPLETED
                || runtimeEventType == RuntimeEventType.RUN_CANCELLED) {
            auditService.record(AuditEvent.builder()
                    .eventType("run." + runtimeEventType.name().toLowerCase(Locale.ROOT))
                    .severity(runtimeEventType == RuntimeEventType.RUN_FAILED ? "WARN" : "INFO")
                    .actorType("GOLEM")
                    .actorId(golemId)
                    .actorName(golemId)
                    .targetType("RUN")
                    .targetId(run.getId())
                    .boardId(card != null ? card.getBoardId() : null)
                    .cardId(card != null ? card.getId() : null)
                    .threadId(thread.getId())
                    .golemId(golemId)
                    .commandId(command != null ? command.getId() : commandId)
                    .runId(run.getId())
                    .summary(firstNonBlank(summary, runtimeEventType.name()))
                    .details(details));
        }

        operatorUpdatesService.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .cardId(card != null ? card.getId() : null)
                .threadId(thread.getId())
                .commandId(command != null ? command.getId() : commandId)
                .runId(run.getId())
                .kinds(List.of("run", "command", "thread_message"))
                .createdAt(eventTime)
                .build());
        budgetService.refreshSnapshots();
    }

    public void applyLifecycleSignal(CardLifecycleSignal signal) {
        RunProjection run = resolveRunProjection(signal.getCommandId(), signal.getRunId(), signal.getThreadId(),
                signal.getCardId(), signal.getGolemId(), signal.getCreatedAt());
        CommandRecord command = resolveCommand(signal.getCommandId(), run);
        Instant eventTime = signal.getCreatedAt() != null ? signal.getCreatedAt() : Instant.now();

        run.setUpdatedAt(eventTime);
        run.setEventCount(run.getEventCount() + 1);
        run.setLastSignalType(signal.getSignalType().name());
        run.setSummary(signal.getSummary());

        LifecycleSignalType signalType = signal.getSignalType();
        switch (signalType) {
        case WORK_STARTED, PROGRESS_REPORTED, REVIEW_REQUESTED -> {
            run.setStatus(RunStatus.RUNNING);
            if (run.getStartedAt() == null) {
                run.setStartedAt(eventTime);
            }
            if (command != null) {
                command.setStatus(CommandStatus.RUNNING);
                if (command.getStartedAt() == null) {
                    command.setStartedAt(eventTime);
                }
                command.setUpdatedAt(eventTime);
            }
        }
        case BLOCKER_RAISED -> {
            run.setStatus(RunStatus.BLOCKED);
            if (command != null) {
                command.setStatus(CommandStatus.RUNNING);
                command.setUpdatedAt(eventTime);
            }
        }
        case BLOCKER_CLEARED -> {
            run.setStatus(RunStatus.RUNNING);
            if (command != null) {
                command.setStatus(CommandStatus.RUNNING);
                command.setUpdatedAt(eventTime);
            }
        }
        case WORK_COMPLETED -> {
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.COMPLETED);
                command.setCompletedAt(eventTime);
                command.setUpdatedAt(eventTime);
            }
        }
        case WORK_FAILED -> {
            run.setStatus(RunStatus.FAILED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.FAILED);
                command.setCompletedAt(eventTime);
                command.setUpdatedAt(eventTime);
            }
        }
        case WORK_CANCELLED -> {
            run.setStatus(RunStatus.CANCELLED);
            run.setCompletedAt(eventTime);
            if (command != null) {
                command.setStatus(CommandStatus.CANCELLED);
                command.setCompletedAt(eventTime);
                command.setUpdatedAt(eventTime);
            }
        }
        }

        saveRun(run);
        if (command != null) {
            saveCommand(command);
        }

        Card card = run.getCardId() != null ? cardService.getCard(run.getCardId()) : null;
        if (signalType == LifecycleSignalType.BLOCKER_RAISED && notificationService.isBlockerRaisedEnabled()) {
            notificationService.create(NotificationEvent.builder()
                    .type("BLOCKER_RAISED")
                    .severity(NotificationSeverity.WARN)
                    .title("Blocker raised")
                    .message(signal.getSummary())
                    .boardId(card != null ? card.getBoardId() : null)
                    .cardId(card != null ? card.getId() : null)
                    .threadId(signal.getThreadId())
                    .golemId(signal.getGolemId())
                    .commandId(signal.getCommandId()));
        }
        if (signalType == LifecycleSignalType.WORK_FAILED && notificationService.isCommandFailedEnabled()) {
            notificationService.create(NotificationEvent.builder()
                    .type("COMMAND_FAILED")
                    .severity(NotificationSeverity.CRITICAL)
                    .title("Work failed")
                    .message(signal.getSummary())
                    .boardId(card != null ? card.getBoardId() : null)
                    .cardId(card != null ? card.getId() : null)
                    .threadId(signal.getThreadId())
                    .golemId(signal.getGolemId())
                    .commandId(signal.getCommandId()));
        }

        auditService.record(AuditEvent.builder()
                .eventType("signal." + signalType.name().toLowerCase(Locale.ROOT))
                .severity(signalType == LifecycleSignalType.BLOCKER_RAISED
                        || signalType == LifecycleSignalType.WORK_FAILED
                                ? "WARN"
                                : "INFO")
                .actorType("GOLEM")
                .actorId(signal.getGolemId())
                .actorName(signal.getGolemId())
                .targetType("RUN")
                .targetId(run.getId())
                .boardId(card != null ? card.getBoardId() : null)
                .cardId(card != null ? card.getId() : null)
                .threadId(signal.getThreadId())
                .golemId(signal.getGolemId())
                .commandId(signal.getCommandId())
                .runId(run.getId())
                .summary(signal.getSummary())
                .details(signal.getDetails()));
        budgetService.refreshSnapshots();
    }

    @EventListener
    public void onApprovalApproved(ApprovalApprovedEvent event) {
        dispatchApprovedCommand(event.commandId());
    }

    public void dispatchApprovedCommand(String commandId) {
        CommandRecord command = findCommand(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown command: " + commandId));
        if (command.getStatus() != CommandStatus.QUEUED) {
            return;
        }
        RunProjection run = findRun(command.getRunId()).orElse(null);
        Card card = cardService.getCard(command.getCardId());
        Golem golem = golemDirectoryUseCase.findGolem(command.getGolemId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + command.getGolemId()));
        dispatchToControlChannel(command, run, Instant.now(), golem);
        saveCommand(command);
        if (run != null) {
            saveRun(run);
        }
        recordDispatchAudit(command, run, card, "system", "Hive");
        budgetService.refreshSnapshots();
    }

    private List<CommandRecord> listCommandsForGolem(String golemId) {
        return listAllCommands().stream()
                .filter(command -> golemId.equals(command.getGolemId()))
                .sorted(Comparator.comparing(CommandRecord::getCreatedAt))
                .toList();
    }

    private List<CommandRecord> listAllCommands() {
        List<CommandRecord> commands = new ArrayList<>();
        for (String path : storagePort.listObjects(COMMANDS_DIR, "")) {
            Optional<CommandRecord> commandOptional = loadCommand(path);
            commandOptional.ifPresent(commands::add);
        }
        return commands;
    }

    private List<RunProjection> listAllRuns() {
        List<RunProjection> runs = new ArrayList<>();
        for (String path : storagePort.listObjects(RUNS_DIR, "")) {
            Optional<RunProjection> runOptional = loadRun(path);
            runOptional.ifPresent(runs::add);
        }
        return runs;
    }

    private void dispatchToControlChannel(CommandRecord command, RunProjection run, Instant now, Golem golem) {
        command.setDispatchAttempts(command.getDispatchAttempts() + 1);
        command.setLastDispatchAttemptAt(now);
        command.setUpdatedAt(now);
        command.setQueueReason(null);

        ControlCommandEnvelope envelope = buildControlEnvelope(CONTROL_EVENT_TYPE_COMMAND, command, now,
                command.getBody());
        boolean delivered = golemControlChannelService.send(golem.getId(), toJson(envelope));
        if (delivered) {
            command.setStatus(CommandStatus.DELIVERED);
            command.setDeliveredAt(now);
            if (run != null) {
                run.setUpdatedAt(now);
            }
            return;
        }

        command.setStatus(CommandStatus.QUEUED);
        command.setQueueReason(golemControlChannelService.isConnected(golem.getId())
                ? "Command delivery failed"
                : "Control channel unavailable");
    }

    private ControlCommandEnvelope buildControlEnvelope(String eventType,
            CommandRecord command,
            Instant createdAt,
            String body) {
        return ControlCommandEnvelope.builder()
                .eventType(eventType)
                .commandId(command.getId())
                .threadId(command.getThreadId())
                .cardId(command.getCardId())
                .golemId(command.getGolemId())
                .runId(command.getRunId())
                .body(body)
                .createdAt(createdAt)
                .build();
    }

    private void recordDispatchAudit(CommandRecord command,
            RunProjection run,
            Card card,
            String actorId,
            String actorName) {
        String summary = command.getStatus() == CommandStatus.DELIVERED
                ? "Command dispatched"
                : "Command queued for delivery";
        auditService.record(AuditEvent.builder()
                .eventType("command.dispatch")
                .severity(command.getStatus() == CommandStatus.DELIVERED ? "INFO" : "WARN")
                .actorType("SYSTEM")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("COMMAND")
                .targetId(command.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(command.getThreadId())
                .golemId(command.getGolemId())
                .commandId(command.getId())
                .runId(run != null ? run.getId() : command.getRunId())
                .approvalId(command.getApprovalRequestId())
                .summary(summary)
                .details(command.getQueueReason()));
    }

    private CardControlStateSnapshot toCardControlStateSnapshot(RunProjection run, CommandRecord command) {
        RunProjection currentRun = Objects.requireNonNull(run, "run");
        Instant cancelRequestedAt = latestInstant(
                currentRun.getCancelRequestedAt(),
                command != null ? command.getCancelRequestedAt() : null);
        String cancelRequestedByActorName = firstNonBlank(
                currentRun.getCancelRequestedByActorName(),
                command != null ? command.getCancelRequestedByActorName() : null);
        boolean cancelRequestedPending = cancelRequestedAt != null
                && !isTerminal(currentRun.getStatus())
                && (command == null || !isTerminal(command.getStatus()));
        boolean canCancel = command != null
                && command.getStatus() != CommandStatus.PENDING_APPROVAL
                && !isTerminal(command.getStatus())
                && !isTerminal(currentRun.getStatus())
                && !cancelRequestedPending;
        return new CardControlStateSnapshot(
                command != null ? command.getId() : currentRun.getCommandId(),
                currentRun.getId(),
                firstNonBlank(currentRun.getGolemId(), command != null ? command.getGolemId() : null),
                command != null ? command.getStatus() : null,
                currentRun.getStatus(),
                firstNonBlank(currentRun.getSummary(), command != null ? command.getBody() : null),
                command != null ? command.getQueueReason() : null,
                controlStateUpdatedAt(currentRun),
                cancelRequestedAt,
                cancelRequestedByActorName,
                cancelRequestedPending,
                canCancel);
    }

    private Instant controlStateUpdatedAt(RunProjection run) {
        return latestInstant(run.getUpdatedAt(), run.getCreatedAt());
    }

    private Optional<CommandRecord> findCommand(String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return Optional.empty();
        }
        return loadCommand(commandId + ".json");
    }

    private Optional<RunProjection> findRun(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return loadRun(runId + ".json");
    }

    private Optional<CommandRecord> loadCommand(String path) {
        String content = storagePort.getText(COMMANDS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, CommandRecord.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize command " + path, exception);
        }
    }

    private Optional<RunProjection> loadRun(String path) {
        String content = storagePort.getText(RUNS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, RunProjection.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize run " + path, exception);
        }
    }

    private void saveCommand(CommandRecord command) {
        try {
            storagePort.putTextAtomic(COMMANDS_DIR, command.getId() + ".json",
                    objectMapper.writeValueAsString(command));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize command " + command.getId(), exception);
        }
    }

    private void saveRun(RunProjection run) {
        try {
            storagePort.putTextAtomic(RUNS_DIR, run.getId() + ".json", objectMapper.writeValueAsString(run));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize run " + run.getId(), exception);
        }
    }

    private RunProjection resolveRunProjection(String commandId,
            String runId,
            String threadId,
            String cardId,
            String golemId,
            Instant createdAt) {
        Optional<RunProjection> byRunId = findRun(runId);
        if (byRunId.isPresent()) {
            return byRunId.get();
        }
        if (commandId != null && !commandId.isBlank()) {
            Optional<CommandRecord> commandOptional = findCommand(commandId);
            if (commandOptional.isPresent()) {
                CommandRecord command = commandOptional.get();
                Optional<RunProjection> byCommandRunId = findRun(command.getRunId());
                if (byCommandRunId.isPresent()) {
                    return byCommandRunId.get();
                }
            }
        }

        Instant now = createdAt != null ? createdAt : Instant.now();
        RunProjection run = RunProjection.builder()
                .id(runId != null && !runId.isBlank() ? runId : "run_" + UUID.randomUUID().toString().replace("-", ""))
                .threadId(threadId)
                .cardId(cardId)
                .commandId(commandId)
                .golemId(golemId)
                .status(RunStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        saveRun(run);
        return run;
    }

    private CommandRecord resolveCommand(String commandId, RunProjection run) {
        if (commandId != null && !commandId.isBlank()) {
            return findCommand(commandId).orElse(null);
        }
        if (run.getCommandId() != null && !run.getCommandId().isBlank()) {
            return findCommand(run.getCommandId()).orElse(null);
        }
        return null;
    }

    private ThreadRecord resolveThread(String threadId, RunProjection run) {
        if (threadId != null && !threadId.isBlank()) {
            return threadService.getThread(threadId);
        }
        return threadService.getThread(run.getThreadId());
    }

    private String toJson(ControlCommandEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize control command envelope", exception);
        }
    }

    private void publishThreadUpdate(Card card,
            ThreadRecord thread,
            CommandRecord command,
            RunProjection run,
            Instant createdAt,
            List<String> kinds) {
        operatorUpdatesService.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .cardId(card.getId())
                .threadId(thread.getId())
                .commandId(command.getId())
                .runId(run.getId())
                .kinds(kinds)
                .createdAt(createdAt)
                .build());
    }

    private boolean isTerminal(CommandStatus status) {
        return status == CommandStatus.COMPLETED
                || status == CommandStatus.REJECTED
                || status == CommandStatus.FAILED
                || status == CommandStatus.CANCELLED;
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean isTerminal(RunStatus status) {
        return status == RunStatus.COMPLETED
                || status == RunStatus.REJECTED
                || status == RunStatus.FAILED
                || status == RunStatus.CANCELLED;
    }

    private boolean isCancellationPending(CommandRecord command, RunProjection run) {
        Instant cancelRequestedAt = latestInstant(
                run != null ? run.getCancelRequestedAt() : null,
                command != null ? command.getCancelRequestedAt() : null);
        return cancelRequestedAt != null
                && (run == null || !isTerminal(run.getStatus()))
                && (command == null || !isTerminal(command.getStatus()));
    }

    private Instant latestInstant(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    @Getter
    @RequiredArgsConstructor
    public static class DispatchResult {
        private final CommandRecord command;
        private final RunProjection run;
    }
}
