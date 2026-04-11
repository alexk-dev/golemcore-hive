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

package me.golemcore.hive.workflow.adapter.out.support;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.shared.budget.BudgetCardProjection;
import me.golemcore.hive.shared.budget.BudgetCustomerProjection;
import me.golemcore.hive.shared.budget.BudgetObjectiveProjection;
import me.golemcore.hive.shared.budget.BudgetServiceProjection;
import me.golemcore.hive.shared.budget.BudgetTeamProjection;
import me.golemcore.hive.shared.budget.BudgetWorkflowProjectionPort;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.ObjectiveRepository;
import me.golemcore.hive.workflow.application.port.out.OrganizationRepository;
import me.golemcore.hive.workflow.application.port.out.TeamRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowBudgetProjectionAdapter implements BudgetWorkflowProjectionPort {

    private static final BudgetCustomerProjection DEFAULT_CUSTOMER = new BudgetCustomerProjection("org_primary",
            "Hive Organization");

    private final OrganizationRepository organizationRepository;
    private final BoardRepository boardRepository;
    private final TeamRepository teamRepository;
    private final ObjectiveRepository objectiveRepository;
    private final CardRepository cardRepository;

    @Override
    public List<BudgetCustomerProjection> listCustomers() {
        return List.of(organizationRepository.findPrimary()
                .map(organization -> new BudgetCustomerProjection(organization.getId(), organization.getName()))
                .orElse(DEFAULT_CUSTOMER));
    }

    @Override
    public List<BudgetServiceProjection> listServices() {
        return boardRepository.list().stream()
                .map(board -> new BudgetServiceProjection(board.getId(), board.getName()))
                .toList();
    }

    @Override
    public List<BudgetTeamProjection> listTeams() {
        return teamRepository.list().stream()
                .map(team -> new BudgetTeamProjection(team.getId(), team.getName()))
                .toList();
    }

    @Override
    public List<BudgetObjectiveProjection> listObjectives() {
        return objectiveRepository.list().stream()
                .map(objective -> new BudgetObjectiveProjection(
                        objective.getId(),
                        objective.getName(),
                        objective.getOwnerTeamId()))
                .toList();
    }

    @Override
    public List<BudgetCardProjection> listCards() {
        return cardRepository.list().stream()
                .map(card -> new BudgetCardProjection(
                        card.getId(),
                        card.getServiceId(),
                        card.getBoardId(),
                        card.getTeamId(),
                        card.getObjectiveId(),
                        card.getTitle()))
                .toList();
    }
}
