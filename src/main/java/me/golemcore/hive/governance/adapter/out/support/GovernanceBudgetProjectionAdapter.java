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

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.governance.application.BudgetCommandProjectionStatus;
import me.golemcore.hive.governance.application.BudgetProjectionData;
import me.golemcore.hive.governance.application.port.out.BudgetProjectionSourcePort;
import me.golemcore.hive.shared.budget.BudgetExecutionProjectionPort;
import me.golemcore.hive.shared.budget.BudgetFleetProjectionPort;
import me.golemcore.hive.shared.budget.BudgetWorkflowProjectionPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceBudgetProjectionAdapter implements BudgetProjectionSourcePort {

    private final BudgetWorkflowProjectionPort budgetWorkflowProjectionPort;
    private final BudgetFleetProjectionPort budgetFleetProjectionPort;
    private final BudgetExecutionProjectionPort budgetExecutionProjectionPort;

    @Override
    public BudgetProjectionData loadProjectionData() {
        return new BudgetProjectionData(
                budgetWorkflowProjectionPort.listBoards().stream()
                        .map(board -> new BudgetProjectionData.BoardProjection(board.id(), board.name()))
                        .toList(),
                budgetWorkflowProjectionPort.listCards().stream()
                        .map(card -> new BudgetProjectionData.CardProjection(card.id(), card.boardId(), card.title()))
                        .toList(),
                budgetFleetProjectionPort.listGolems().stream()
                        .map(golem -> new BudgetProjectionData.GolemProjection(golem.id(), golem.displayName()))
                        .toList(),
                budgetExecutionProjectionPort.listCommands().stream()
                        .map(command -> new BudgetProjectionData.CommandProjection(
                                command.id(),
                                command.cardId(),
                                command.golemId(),
                                toBudgetCommandProjectionStatus(command.status()),
                                command.estimatedCostMicros()))
                        .toList(),
                budgetExecutionProjectionPort.listRuns().stream()
                        .map(run -> new BudgetProjectionData.RunProjection(
                                run.id(),
                                run.cardId(),
                                run.golemId(),
                                run.inputTokens(),
                                run.outputTokens(),
                                run.accumulatedCostMicros()))
                        .toList());
    }

    private BudgetCommandProjectionStatus toBudgetCommandProjectionStatus(
            me.golemcore.hive.shared.budget.BudgetCommandProjectionStatus status) {
        return switch (status) {
        case PENDING_APPROVAL -> BudgetCommandProjectionStatus.PENDING_APPROVAL;
        case QUEUED -> BudgetCommandProjectionStatus.QUEUED;
        case DELIVERED -> BudgetCommandProjectionStatus.DELIVERED;
        case RUNNING -> BudgetCommandProjectionStatus.RUNNING;
        default -> BudgetCommandProjectionStatus.OTHER;
        };
    }
}
