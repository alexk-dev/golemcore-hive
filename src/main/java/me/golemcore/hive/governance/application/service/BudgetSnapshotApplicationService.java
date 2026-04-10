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

package me.golemcore.hive.governance.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.golemcore.hive.domain.model.BudgetScopeType;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.governance.application.BudgetCommandProjectionStatus;
import me.golemcore.hive.governance.application.BudgetProjectionData;
import me.golemcore.hive.governance.application.port.in.BudgetSnapshotUseCase;
import me.golemcore.hive.governance.application.port.out.BudgetProjectionSourcePort;
import me.golemcore.hive.governance.application.port.out.BudgetSnapshotRepositoryPort;

public class BudgetSnapshotApplicationService implements BudgetSnapshotUseCase {

    private final BudgetSnapshotRepositoryPort budgetSnapshotRepositoryPort;
    private final BudgetProjectionSourcePort budgetProjectionSourcePort;

    public BudgetSnapshotApplicationService(
            BudgetSnapshotRepositoryPort budgetSnapshotRepositoryPort,
            BudgetProjectionSourcePort budgetProjectionSourcePort) {
        this.budgetSnapshotRepositoryPort = budgetSnapshotRepositoryPort;
        this.budgetProjectionSourcePort = budgetProjectionSourcePort;
    }

    @Override
    public List<BudgetSnapshot> listSnapshots(String scopeType, String query) {
        refreshSnapshots();
        List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();
        for (BudgetSnapshot budgetSnapshot : budgetSnapshotRepositoryPort.findAll()) {
            if (scopeType != null && !scopeType.isBlank()
                    && !budgetSnapshot.getScopeType().name().equalsIgnoreCase(scopeType)) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String normalizedQuery = query.toLowerCase(Locale.ROOT);
                String scopeLabel = budgetSnapshot.getScopeLabel() != null
                        ? budgetSnapshot.getScopeLabel().toLowerCase(Locale.ROOT)
                        : "";
                String scopeId = budgetSnapshot.getScopeId() != null
                        ? budgetSnapshot.getScopeId().toLowerCase(Locale.ROOT)
                        : "";
                if (!scopeLabel.contains(normalizedQuery) && !scopeId.contains(normalizedQuery)) {
                    continue;
                }
            }
            budgetSnapshots.add(budgetSnapshot);
        }
        budgetSnapshots.sort(Comparator.comparing(BudgetSnapshot::getActualCostMicros).reversed()
                .thenComparing(BudgetSnapshot::getScopeLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return budgetSnapshots;
    }

    @Override
    public void refreshSnapshots() {
        BudgetProjectionData projectionData = budgetProjectionSourcePort.loadProjectionData();
        Map<String, BudgetProjectionData.BoardProjection> boardsById = indexBoardsById(projectionData.boards());
        Map<String, BudgetProjectionData.CardProjection> cardsById = indexCardsById(projectionData.cards());
        Map<String, BudgetProjectionData.GolemProjection> golemsById = indexGolemsById(projectionData.golems());
        Map<String, BudgetProjectionData.CommandProjection> commandsById = indexCommandsById(projectionData.commands());
        Map<String, BudgetProjectionData.RunProjection> runsById = indexRunsById(projectionData.runs());
        Map<String, BudgetAccumulator> accumulators = new LinkedHashMap<>();

        for (BudgetProjectionData.CommandProjection commandProjection : commandsById.values()) {
            BudgetProjectionData.CardProjection card = commandProjection.cardId() != null
                    ? cardsById.get(commandProjection.cardId())
                    : null;
            BudgetProjectionData.GolemProjection golem = commandProjection.golemId() != null
                    ? golemsById.get(commandProjection.golemId())
                    : null;
            addCommand(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, commandProjection);
            if (card != null && card.boardId() != null) {
                BudgetProjectionData.BoardProjection board = boardsById.get(card.boardId());
                addCommand(accumulators, BudgetScopeType.BOARD, card.boardId(),
                        board != null ? board.name() : card.boardId(), card.boardId(), null, null,
                        commandProjection);
                addCommand(accumulators, BudgetScopeType.CARD, card.id(), card.title(), card.boardId(), card.id(),
                        null, commandProjection);
            }
            if (golem != null) {
                addCommand(accumulators, BudgetScopeType.GOLEM, golem.id(), golem.displayName(),
                        card != null ? card.boardId() : null, card != null ? card.id() : null, golem.id(),
                        commandProjection);
            }
        }

        for (BudgetProjectionData.RunProjection runProjection : runsById.values()) {
            BudgetProjectionData.CardProjection card = runProjection.cardId() != null
                    ? cardsById.get(runProjection.cardId())
                    : null;
            BudgetProjectionData.GolemProjection golem = runProjection.golemId() != null
                    ? golemsById.get(runProjection.golemId())
                    : null;
            addRun(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, runProjection);
            if (card != null && card.boardId() != null) {
                BudgetProjectionData.BoardProjection board = boardsById.get(card.boardId());
                addRun(accumulators, BudgetScopeType.BOARD, card.boardId(),
                        board != null ? board.name() : card.boardId(), card.boardId(), null, null,
                        runProjection);
                addRun(accumulators, BudgetScopeType.CARD, card.id(), card.title(), card.boardId(), card.id(),
                        null, runProjection);
            }
            if (golem != null) {
                addRun(accumulators, BudgetScopeType.GOLEM, golem.id(), golem.displayName(),
                        card != null ? card.boardId() : null, card != null ? card.id() : null, golem.id(),
                        runProjection);
            }
        }

        List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();
        for (BudgetAccumulator budgetAccumulator : accumulators.values()) {
            budgetSnapshots.add(budgetAccumulator.toSnapshot());
        }
        budgetSnapshotRepositoryPort.replaceAll(budgetSnapshots);
    }

    private void addCommand(
            Map<String, BudgetAccumulator> accumulators,
            BudgetScopeType scopeType,
            String scopeId,
            String scopeLabel,
            String boardId,
            String cardId,
            String golemId,
            BudgetProjectionData.CommandProjection commandProjection) {
        BudgetAccumulator budgetAccumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, boardId, cardId, golemId));
        budgetAccumulator.commandCount = budgetAccumulator.commandCount + 1;
        if (commandProjection.status() == BudgetCommandProjectionStatus.PENDING_APPROVAL
                || commandProjection.status() == BudgetCommandProjectionStatus.QUEUED
                || commandProjection.status() == BudgetCommandProjectionStatus.DELIVERED
                || commandProjection.status() == BudgetCommandProjectionStatus.RUNNING) {
            budgetAccumulator.estimatedPendingCostMicros = budgetAccumulator.estimatedPendingCostMicros
                    + Math.max(commandProjection.estimatedCostMicros(), 0L);
        }
    }

    private void addRun(
            Map<String, BudgetAccumulator> accumulators,
            BudgetScopeType scopeType,
            String scopeId,
            String scopeLabel,
            String boardId,
            String cardId,
            String golemId,
            BudgetProjectionData.RunProjection runProjection) {
        BudgetAccumulator budgetAccumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, boardId, cardId, golemId));
        budgetAccumulator.runCount = budgetAccumulator.runCount + 1;
        budgetAccumulator.inputTokens = budgetAccumulator.inputTokens + Math.max(runProjection.inputTokens(), 0L);
        budgetAccumulator.outputTokens = budgetAccumulator.outputTokens
                + Math.max(runProjection.outputTokens(), 0L);
        budgetAccumulator.actualCostMicros = budgetAccumulator.actualCostMicros
                + Math.max(runProjection.accumulatedCostMicros(), 0L);
    }

    private String key(BudgetScopeType scopeType, String scopeId) {
        return scopeType.name() + ":" + scopeId;
    }

    private Map<String, BudgetProjectionData.BoardProjection> indexBoardsById(
            List<BudgetProjectionData.BoardProjection> boards) {
        Map<String, BudgetProjectionData.BoardProjection> boardsById = new LinkedHashMap<>();
        for (BudgetProjectionData.BoardProjection board : boards) {
            if (board.id() != null && !board.id().isBlank()) {
                boardsById.put(board.id(), board);
            }
        }
        return boardsById;
    }

    private Map<String, BudgetProjectionData.CardProjection> indexCardsById(
            List<BudgetProjectionData.CardProjection> cards) {
        Map<String, BudgetProjectionData.CardProjection> cardsById = new LinkedHashMap<>();
        for (BudgetProjectionData.CardProjection card : cards) {
            if (card.id() != null && !card.id().isBlank()) {
                cardsById.put(card.id(), card);
            }
        }
        return cardsById;
    }

    private Map<String, BudgetProjectionData.GolemProjection> indexGolemsById(
            List<BudgetProjectionData.GolemProjection> golems) {
        Map<String, BudgetProjectionData.GolemProjection> golemsById = new LinkedHashMap<>();
        for (BudgetProjectionData.GolemProjection golem : golems) {
            if (golem.id() != null && !golem.id().isBlank()) {
                golemsById.put(golem.id(), golem);
            }
        }
        return golemsById;
    }

    private Map<String, BudgetProjectionData.CommandProjection> indexCommandsById(
            List<BudgetProjectionData.CommandProjection> commandRecords) {
        Map<String, BudgetProjectionData.CommandProjection> commandsById = new LinkedHashMap<>();
        for (BudgetProjectionData.CommandProjection commandRecord : commandRecords) {
            if (commandRecord.id() != null && !commandRecord.id().isBlank()) {
                commandsById.put(commandRecord.id(), commandRecord);
            }
        }
        return commandsById;
    }

    private Map<String, BudgetProjectionData.RunProjection> indexRunsById(
            List<BudgetProjectionData.RunProjection> runProjections) {
        Map<String, BudgetProjectionData.RunProjection> runsById = new LinkedHashMap<>();
        for (BudgetProjectionData.RunProjection runProjection : runProjections) {
            if (runProjection.id() != null && !runProjection.id().isBlank()) {
                runsById.put(runProjection.id(), runProjection);
            }
        }
        return runsById;
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

        private BudgetAccumulator(
                BudgetScopeType scopeType,
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
                    .id(scopeType.name().toLowerCase(Locale.ROOT) + "_" + scopeId)
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
