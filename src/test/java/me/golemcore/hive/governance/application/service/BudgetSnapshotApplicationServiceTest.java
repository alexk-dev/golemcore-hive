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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import me.golemcore.hive.domain.model.BudgetScopeType;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.governance.application.BudgetCommandProjectionStatus;
import me.golemcore.hive.governance.application.BudgetProjectionData;
import me.golemcore.hive.governance.application.port.out.BudgetProjectionSourcePort;
import me.golemcore.hive.governance.application.port.out.BudgetSnapshotRepositoryPort;
import org.junit.jupiter.api.Test;

class BudgetSnapshotApplicationServiceTest {

    @Test
    void shouldAggregateBudgetSnapshotsFromProjectionData() {
        InMemoryBudgetSnapshotRepository repository = new InMemoryBudgetSnapshotRepository();
        BudgetProjectionSourcePort projectionSourcePort = () -> new BudgetProjectionData(
                List.of(new BudgetProjectionData.BoardProjection("board-1", "Board 1")),
                List.of(new BudgetProjectionData.CardProjection("card-1", "board-1", "Card 1")),
                List.of(new BudgetProjectionData.GolemProjection("golem-1", "Atlas")),
                List.of(new BudgetProjectionData.CommandProjection(
                        "cmd-1",
                        "card-1",
                        "golem-1",
                        BudgetCommandProjectionStatus.PENDING_APPROVAL,
                        7_000_000L)),
                List.of(new BudgetProjectionData.RunProjection(
                        "run-1",
                        "card-1",
                        "golem-1",
                        100L,
                        40L,
                        120_000L)));
        BudgetSnapshotApplicationService budgetSnapshotApplicationService = new BudgetSnapshotApplicationService(
                repository,
                projectionSourcePort);

        List<BudgetSnapshot> systemSnapshots = budgetSnapshotApplicationService.listSnapshots("SYSTEM", null);
        List<BudgetSnapshot> boardSnapshots = budgetSnapshotApplicationService.listSnapshots("BOARD", null);

        assertEquals(1, systemSnapshots.size());
        assertEquals(7_000_000L, systemSnapshots.getFirst().getEstimatedPendingCostMicros());
        assertEquals(120_000L, systemSnapshots.getFirst().getActualCostMicros());
        assertEquals(1, systemSnapshots.getFirst().getCommandCount());
        assertEquals(1, systemSnapshots.getFirst().getRunCount());
        assertEquals(1, boardSnapshots.size());
        assertEquals(BudgetScopeType.BOARD, boardSnapshots.getFirst().getScopeType());
        assertEquals("board-1", boardSnapshots.getFirst().getScopeId());
    }

    @Test
    void shouldKeepBoardScopeWhenCardReferencesMissingBoardProjection() {
        InMemoryBudgetSnapshotRepository repository = new InMemoryBudgetSnapshotRepository();
        BudgetProjectionSourcePort projectionSourcePort = () -> new BudgetProjectionData(
                List.of(),
                List.of(new BudgetProjectionData.CardProjection("card-1", "board-missing", "Card 1")),
                List.of(),
                List.of(new BudgetProjectionData.CommandProjection(
                        "cmd-1",
                        "card-1",
                        null,
                        BudgetCommandProjectionStatus.QUEUED,
                        15L)),
                List.of());
        BudgetSnapshotApplicationService budgetSnapshotApplicationService = new BudgetSnapshotApplicationService(
                repository,
                projectionSourcePort);

        List<BudgetSnapshot> boardSnapshots = budgetSnapshotApplicationService.listSnapshots("BOARD", null);

        assertEquals(1, boardSnapshots.size());
        assertEquals("board-missing", boardSnapshots.getFirst().getScopeId());
        assertEquals("board-missing", boardSnapshots.getFirst().getScopeLabel());
        assertEquals(1, boardSnapshots.getFirst().getCommandCount());
    }

    @Test
    void shouldRetainBoardScopeWhenBoardProjectionIsMissingButCardProjectionExists() {
        InMemoryBudgetSnapshotRepository repository = new InMemoryBudgetSnapshotRepository();
        BudgetProjectionSourcePort projectionSourcePort = () -> new BudgetProjectionData(
                List.of(),
                List.of(new BudgetProjectionData.CardProjection("card-1", "board-missing", "Recovered card")),
                List.of(),
                List.of(new BudgetProjectionData.CommandProjection(
                        "cmd-1",
                        "card-1",
                        null,
                        BudgetCommandProjectionStatus.QUEUED,
                        50L)),
                List.of());
        BudgetSnapshotApplicationService budgetSnapshotApplicationService = new BudgetSnapshotApplicationService(
                repository,
                projectionSourcePort);

        List<BudgetSnapshot> boardSnapshots = budgetSnapshotApplicationService.listSnapshots("BOARD", null);
        List<BudgetSnapshot> cardSnapshots = budgetSnapshotApplicationService.listSnapshots("CARD", null);

        assertEquals(1, boardSnapshots.size());
        assertEquals("board-missing", boardSnapshots.getFirst().getScopeId());
        assertEquals("board-missing", boardSnapshots.getFirst().getScopeLabel());
        assertEquals(1, cardSnapshots.size());
        assertEquals("card-1", cardSnapshots.getFirst().getScopeId());
    }

    @Test
    void shouldRemoveStaleSnapshotsWhenProjectionShrinks() {
        InMemoryBudgetSnapshotRepository repository = new InMemoryBudgetSnapshotRepository();
        BudgetSnapshotApplicationService budgetSnapshotApplicationService = new BudgetSnapshotApplicationService(
                repository,
                new MutableBudgetProjectionSourcePort(
                        new BudgetProjectionData(
                                List.of(new BudgetProjectionData.BoardProjection("board-1", "Board 1")),
                                List.of(new BudgetProjectionData.CardProjection("card-1", "board-1", "Card 1")),
                                List.of(),
                                List.of(new BudgetProjectionData.CommandProjection(
                                        "cmd-1",
                                        "card-1",
                                        null,
                                        BudgetCommandProjectionStatus.QUEUED,
                                        10L)),
                                List.of())));

        budgetSnapshotApplicationService.refreshSnapshots();
        assertEquals(3, repository.findAll().size());

        MutableBudgetProjectionSourcePort projectionSourcePort = new MutableBudgetProjectionSourcePort(
                new BudgetProjectionData(List.of(), List.of(), List.of(), List.of(), List.of()));
        budgetSnapshotApplicationService = new BudgetSnapshotApplicationService(repository, projectionSourcePort);

        budgetSnapshotApplicationService.refreshSnapshots();

        assertEquals(0, repository.findAll().size());
    }

    private static final class MutableBudgetProjectionSourcePort implements BudgetProjectionSourcePort {

        private final BudgetProjectionData budgetProjectionData;

        private MutableBudgetProjectionSourcePort(BudgetProjectionData budgetProjectionData) {
            this.budgetProjectionData = budgetProjectionData;
        }

        @Override
        public BudgetProjectionData loadProjectionData() {
            return budgetProjectionData;
        }
    }

    private static final class InMemoryBudgetSnapshotRepository implements BudgetSnapshotRepositoryPort {

        private final List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();

        @Override
        public List<BudgetSnapshot> findAll() {
            return new ArrayList<>(budgetSnapshots);
        }

        @Override
        public void save(BudgetSnapshot budgetSnapshot) {
            budgetSnapshots.removeIf(existingSnapshot -> existingSnapshot.getId().equals(budgetSnapshot.getId()));
            budgetSnapshots.add(BudgetSnapshot.builder()
                    .id(budgetSnapshot.getId())
                    .scopeType(budgetSnapshot.getScopeType())
                    .scopeId(budgetSnapshot.getScopeId())
                    .scopeLabel(budgetSnapshot.getScopeLabel())
                    .boardId(budgetSnapshot.getBoardId())
                    .cardId(budgetSnapshot.getCardId())
                    .golemId(budgetSnapshot.getGolemId())
                    .commandCount(budgetSnapshot.getCommandCount())
                    .runCount(budgetSnapshot.getRunCount())
                    .inputTokens(budgetSnapshot.getInputTokens())
                    .outputTokens(budgetSnapshot.getOutputTokens())
                    .actualCostMicros(budgetSnapshot.getActualCostMicros())
                    .estimatedPendingCostMicros(budgetSnapshot.getEstimatedPendingCostMicros())
                    .updatedAt(budgetSnapshot.getUpdatedAt() != null ? budgetSnapshot.getUpdatedAt() : Instant.now())
                    .build());
        }

        @Override
        public void replaceAll(List<BudgetSnapshot> budgetSnapshots) {
            this.budgetSnapshots.clear();
            for (BudgetSnapshot budgetSnapshot : budgetSnapshots) {
                save(budgetSnapshot);
            }
        }
    }
}
