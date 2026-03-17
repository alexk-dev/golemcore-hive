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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.OperatorUpdate;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.RunStatus;
import me.golemcore.hive.domain.model.RuntimeEventType;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandDispatchService {

    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final ThreadService threadService;
    private final CardService cardService;
    private final GolemRegistryService golemRegistryService;
    private final GolemControlChannelService golemControlChannelService;
    private final OperatorUpdatesService operatorUpdatesService;

    public DispatchResult createCommand(String threadId, String body, String operatorId, String operatorName) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Command body is required");
        }

        ThreadRecord thread = threadService.getThread(threadId);
        Card card = cardService.getCard(thread.getCardId());
        if (card.getAssigneeGolemId() == null || card.getAssigneeGolemId().isBlank()) {
            throw new IllegalArgumentException("Card must be assigned before dispatching commands");
        }
        Golem golem = golemRegistryService.findGolem(card.getAssigneeGolemId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + card.getAssigneeGolemId()));

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
        saveRun(run);

        CommandRecord command = CommandRecord.builder()
                .id(commandId)
                .threadId(thread.getId())
                .cardId(card.getId())
                .golemId(golem.getId())
                .runId(runId)
                .body(body)
                .status(CommandStatus.QUEUED)
                .createdAt(now)
                .updatedAt(now)
                .dispatchAttempts(0)
                .build();

        threadService.appendMessage(thread, commandId, runId, null, ThreadMessageType.COMMAND_REQUEST,
                ThreadParticipantType.OPERATOR, operatorId, operatorName, body, now);
        dispatchToControlChannel(command, run, now, golem);
        saveCommand(command);
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

    public void dispatchPendingCommands(String golemId) {
        Golem golem = golemRegistryService.findGolem(golemId).orElse(null);
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
        }
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
        Card card = cardId != null ? cardService.getCard(cardId) : cardService.getCard(run.getCardId());

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
            case THREAD_MESSAGE -> threadService.appendMessage(thread, command != null ? command.getId() : commandId, run.getId(),
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

        operatorUpdatesService.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .cardId(card.getId())
                .threadId(thread.getId())
                .commandId(command != null ? command.getId() : commandId)
                .runId(run.getId())
                .kinds(List.of("run", "command", "thread_message"))
                .createdAt(eventTime)
                .build());
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

    private void dispatchToControlChannel(CommandRecord command, RunProjection run, Instant now, Golem golem) {
        command.setDispatchAttempts(command.getDispatchAttempts() + 1);
        command.setLastDispatchAttemptAt(now);
        command.setUpdatedAt(now);
        command.setQueueReason(null);

        ControlCommandEnvelope envelope = ControlCommandEnvelope.builder()
                .eventType("command")
                .commandId(command.getId())
                .threadId(command.getThreadId())
                .cardId(command.getCardId())
                .golemId(command.getGolemId())
                .runId(command.getRunId())
                .body(command.getBody())
                .createdAt(now)
                .build();
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
            storagePort.putTextAtomic(COMMANDS_DIR, command.getId() + ".json", objectMapper.writeValueAsString(command));
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
