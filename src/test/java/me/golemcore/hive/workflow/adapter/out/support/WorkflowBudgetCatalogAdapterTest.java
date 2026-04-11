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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.domain.model.Organization;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.shared.budget.BudgetCardProjection;
import me.golemcore.hive.shared.budget.BudgetCustomerProjection;
import me.golemcore.hive.shared.budget.BudgetObjectiveProjection;
import me.golemcore.hive.shared.budget.BudgetServiceProjection;
import me.golemcore.hive.shared.budget.BudgetTeamProjection;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.ObjectiveRepository;
import me.golemcore.hive.workflow.application.port.out.OrganizationRepository;
import me.golemcore.hive.workflow.application.port.out.TeamRepository;
import org.junit.jupiter.api.Test;

class WorkflowBudgetCatalogAdapterTest {

    @Test
    void shouldListAllCardsWithoutBoardByBoardScanning() {
        BoardRepository boardRepository = mock(BoardRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        ObjectiveRepository objectiveRepository = mock(ObjectiveRepository.class);
        CardRepository cardRepository = mock(CardRepository.class);
        WorkflowBudgetProjectionAdapter adapter = new WorkflowBudgetProjectionAdapter(
                organizationRepository,
                boardRepository,
                teamRepository,
                objectiveRepository,
                cardRepository);
        when(organizationRepository.findPrimary()).thenReturn(java.util.Optional.of(Organization.builder()
                .id("customer-1")
                .name("Customer 1")
                .build()));
        when(boardRepository.list()).thenReturn(List.of(Board.builder()
                .id("service-1")
                .name("Service 1")
                .build()));
        when(teamRepository.list()).thenReturn(List.of(Team.builder()
                .id("team-1")
                .name("Team 1")
                .build()));
        when(objectiveRepository.list()).thenReturn(List.of(Objective.builder()
                .id("objective-1")
                .name("Objective 1")
                .ownerTeamId("team-1")
                .build()));
        when(cardRepository.list()).thenReturn(List.of(
                Card.builder()
                        .id("card-1")
                        .serviceId("service-1")
                        .boardId("service-1")
                        .teamId("team-1")
                        .objectiveId("objective-1")
                        .title("Card 1")
                        .build(),
                Card.builder().id("card-2").boardId("missing-service").title("Card 2").build()));

        List<BudgetCustomerProjection> customers = adapter.listCustomers();
        List<BudgetServiceProjection> services = adapter.listServices();
        List<BudgetTeamProjection> teams = adapter.listTeams();
        List<BudgetObjectiveProjection> objectives = adapter.listObjectives();
        List<BudgetCardProjection> cards = adapter.listCards();

        assertEquals(1, customers.size());
        assertEquals("customer-1", customers.getFirst().id());
        assertEquals(1, services.size());
        assertEquals("service-1", services.getFirst().id());
        assertEquals(1, teams.size());
        assertEquals("team-1", teams.getFirst().id());
        assertEquals(1, objectives.size());
        assertEquals("objective-1", objectives.getFirst().id());
        assertEquals(2, cards.size());
        assertEquals("service-1", cards.getFirst().serviceId());
        assertEquals("missing-service", cards.get(1).boardId());
        verify(organizationRepository).findPrimary();
        verify(boardRepository).list();
        verify(teamRepository).list();
        verify(objectiveRepository).list();
        verify(cardRepository).list();
    }
}
