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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BudgetScopeType;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final String BUDGETS_DIR = "budgets";
    private static final String BOARDS_DIR = "boards";
    private static final String CARDS_DIR = "cards";
    private static final String GOLEMS_DIR = "golems";
    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    public List<BudgetSnapshot> listSnapshots(String scopeType, String query) {
        refreshSnapshots();
        List<BudgetSnapshot> snapshots = new ArrayList<>();
        for (String path : storagePort.listObjects(BUDGETS_DIR, "")) {
            Optional<BudgetSnapshot> snapshotOptional = loadSnapshot(path);
            if (snapshotOptional.isEmpty()) {
                continue;
            }
            BudgetSnapshot snapshot = snapshotOptional.get();
            if (scopeType != null && !scopeType.isBlank() && !snapshot.getScopeType().name().equalsIgnoreCase(scopeType)) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String normalized = query.toLowerCase();
                String label = snapshot.getScopeLabel() != null ? snapshot.getScopeLabel().toLowerCase() : "";
                String scopeId = snapshot.getScopeId() != null ? snapshot.getScopeId().toLowerCase() : "";
                if (!label.contains(normalized) && !scopeId.contains(normalized)) {
                    continue;
                }
            }
            snapshots.add(snapshot);
        }
        snapshots.sort(Comparator.comparing(BudgetSnapshot::getActualCostMicros).reversed()
                .thenComparing(BudgetSnapshot::getScopeLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return snapshots;
    }

    public void refreshSnapshots() {
        Map<String, Board> boardsById = loadById(BOARDS_DIR, Board.class);
        Map<String, Card> cardsById = loadById(CARDS_DIR, Card.class);
        Map<String, Golem> golemsById = loadById(GOLEMS_DIR, Golem.class);
        Map<String, CommandRecord> commandsById = loadById(COMMANDS_DIR, CommandRecord.class);
        Map<String, RunProjection> runsById = loadById(RUNS_DIR, RunProjection.class);
        Map<String, BudgetAccumulator> accumulators = new LinkedHashMap<>();

        for (CommandRecord command : commandsById.values()) {
            Card card = command.getCardId() != null ? cardsById.get(command.getCardId()) : null;
            Golem golem = command.getGolemId() != null ? golemsById.get(command.getGolemId()) : null;
            addCommand(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, command);
            if (card != null && card.getBoardId() != null) {
                Board board = boardsById.get(card.getBoardId());
                addCommand(accumulators, BudgetScopeType.BOARD, card.getBoardId(),
                        board != null ? board.getName() : card.getBoardId(), card.getBoardId(), null, null, command);
                addCommand(accumulators, BudgetScopeType.CARD, card.getId(), card.getTitle(),
                        card.getBoardId(), card.getId(), null, command);
            }
            if (golem != null) {
                addCommand(accumulators, BudgetScopeType.GOLEM, golem.getId(), golem.getDisplayName(),
                        card != null ? card.getBoardId() : null, card != null ? card.getId() : null, golem.getId(), command);
            }
        }

        for (RunProjection run : runsById.values()) {
            Card card = run.getCardId() != null ? cardsById.get(run.getCardId()) : null;
            Golem golem = run.getGolemId() != null ? golemsById.get(run.getGolemId()) : null;
            addRun(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, run);
            if (card != null && card.getBoardId() != null) {
                Board board = boardsById.get(card.getBoardId());
                addRun(accumulators, BudgetScopeType.BOARD, card.getBoardId(),
                        board != null ? board.getName() : card.getBoardId(), card.getBoardId(), null, null, run);
                addRun(accumulators, BudgetScopeType.CARD, card.getId(), card.getTitle(),
                        card.getBoardId(), card.getId(), null, run);
            }
            if (golem != null) {
                addRun(accumulators, BudgetScopeType.GOLEM, golem.getId(), golem.getDisplayName(),
                        card != null ? card.getBoardId() : null, card != null ? card.getId() : null, golem.getId(), run);
            }
        }

        for (BudgetAccumulator accumulator : accumulators.values()) {
            saveSnapshot(accumulator.toSnapshot());
        }
    }

    private void addCommand(Map<String, BudgetAccumulator> accumulators,
                            BudgetScopeType scopeType,
                            String scopeId,
                            String scopeLabel,
                            String boardId,
                            String cardId,
                            String golemId,
                            CommandRecord command) {
        BudgetAccumulator accumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, boardId, cardId, golemId));
        accumulator.commandCount++;
        if (command.getStatus() == CommandStatus.PENDING_APPROVAL
                || command.getStatus() == CommandStatus.QUEUED
                || command.getStatus() == CommandStatus.DELIVERED
                || command.getStatus() == CommandStatus.RUNNING) {
            accumulator.estimatedPendingCostMicros += Math.max(command.getEstimatedCostMicros(), 0L);
        }
    }

    private void addRun(Map<String, BudgetAccumulator> accumulators,
                        BudgetScopeType scopeType,
                        String scopeId,
                        String scopeLabel,
                        String boardId,
                        String cardId,
                        String golemId,
                        RunProjection run) {
        BudgetAccumulator accumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, boardId, cardId, golemId));
        accumulator.runCount++;
        accumulator.inputTokens += Math.max(run.getInputTokens(), 0L);
        accumulator.outputTokens += Math.max(run.getOutputTokens(), 0L);
        accumulator.actualCostMicros += Math.max(run.getAccumulatedCostMicros(), 0L);
    }

    private String key(BudgetScopeType scopeType, String scopeId) {
        return scopeType.name() + ":" + scopeId;
    }

    private <T> Map<String, T> loadById(String directory, Class<T> type) {
        Map<String, T> items = new LinkedHashMap<>();
        for (String path : storagePort.listObjects(directory, "")) {
            String content = storagePort.getText(directory, path);
            if (content == null) {
                continue;
            }
            try {
                T item = objectMapper.readValue(content, type);
                items.put(stripJsonSuffix(path), item);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to deserialize " + directory + "/" + path, exception);
            }
        }
        return items;
    }

    private Optional<BudgetSnapshot> loadSnapshot(String path) {
        String content = storagePort.getText(BUDGETS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, BudgetSnapshot.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize budget snapshot " + path, exception);
        }
    }

    private void saveSnapshot(BudgetSnapshot snapshot) {
        try {
            storagePort.putTextAtomic(BUDGETS_DIR, snapshot.getId() + ".json", objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize budget snapshot " + snapshot.getId(), exception);
        }
    }

    private String stripJsonSuffix(String path) {
        return path.endsWith(".json") ? path.substring(0, path.length() - 5) : path;
    }

    private static class BudgetAccumulator {
        private final BudgetScopeType scopeType;
        private final String scopeId;
        private final String scopeLabel;
        private final String boardId;
        private final String cardId;
        private final String golemId;
        private long commandCount;
        private long runCount;
        private long inputTokens;
        private long outputTokens;
        private long actualCostMicros;
        private long estimatedPendingCostMicros;

        private BudgetAccumulator(BudgetScopeType scopeType,
                                  String scopeId,
                                  String scopeLabel,
                                  String boardId,
                                  String cardId,
                                  String golemId) {
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.scopeLabel = scopeLabel;
            this.boardId = boardId;
            this.cardId = cardId;
            this.golemId = golemId;
        }

        private BudgetSnapshot toSnapshot() {
            return BudgetSnapshot.builder()
                    .id(scopeType.name().toLowerCase() + "_" + scopeId)
                    .scopeType(scopeType)
                    .scopeId(scopeId)
                    .scopeLabel(scopeLabel)
                    .boardId(boardId)
                    .cardId(cardId)
                    .golemId(golemId)
                    .commandCount(commandCount)
                    .runCount(runCount)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .actualCostMicros(actualCostMicros)
                    .estimatedPendingCostMicros(estimatedPendingCostMicros)
                    .updatedAt(Instant.now())
                    .build();
        }
    }
}
