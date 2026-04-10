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

package me.golemcore.hive.workflow.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardTeam;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.CardQuery;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ObjectiveWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.ThreadRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAssignmentPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import org.junit.jupiter.api.Test;

class CardWorkflowApplicationServiceTest {

    @Test
    void shouldDefaultCardsToTaskAndInheritEpicFromParentEpic() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        ThreadRepository threadRepository = mock(ThreadRepository.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        WorkflowAssignmentPort workflowAssignmentPort = mock(WorkflowAssignmentPort.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        ObjectiveWorkflowUseCase objectiveWorkflowUseCase = mock(ObjectiveWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(board());

        CardWorkflowApplicationService service = new CardWorkflowApplicationService(
                cardRepository,
                threadRepository,
                boardWorkflowUseCase,
                workflowAssignmentPort,
                golemDirectoryUseCase,
                teamWorkflowUseCase,
                objectiveWorkflowUseCase,
                workflowAuditPort);

        Card epic = service.createCard(new CardCreateCommand(
                "service-1",
                "Launch epic",
                "Big delivery container",
                "Coordinate the epic",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.EPIC,
                null,
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin");

        Card child = service.createCard(new CardCreateCommand(
                "service-1",
                "Implementation task",
                "Child work item",
                "Implement the child task",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                null,
                epic.getId(),
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin");

        Card review = service.createCard(new CardCreateCommand(
                "service-1",
                "Review implementation",
                "Review child work",
                "Review the child task",
                "review",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.REVIEW,
                null,
                null,
                child.getId(),
                List.of()),
                "operator-1",
                "Hive Admin");

        assertEquals(CardKind.EPIC, epic.getKind());
        assertEquals(CardKind.TASK, child.getKind());
        assertEquals(CardKind.REVIEW, review.getKind());
        assertEquals(epic.getId(), child.getParentCardId());
        assertEquals(epic.getId(), child.getEpicCardId());
        assertEquals(child.getId(), review.getReviewOfCardId());
        assertEquals(List.of(child),
                service.listCards(new CardQuery("service-1", false, CardKind.TASK, null, epic.getId(), null, null)));
        assertEquals(List.of(epic),
                service.listCards(new CardQuery("service-1", false, CardKind.EPIC, null, null, null, null)));
        assertEquals(List.of(review),
                service.listCards(new CardQuery("service-1", false, CardKind.REVIEW, null, null, child.getId(), null)));
    }

    @Test
    void shouldRejectEpicCardsWithParentReferences() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        ThreadRepository threadRepository = mock(ThreadRepository.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        WorkflowAssignmentPort workflowAssignmentPort = mock(WorkflowAssignmentPort.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        ObjectiveWorkflowUseCase objectiveWorkflowUseCase = mock(ObjectiveWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(board());

        CardWorkflowApplicationService service = new CardWorkflowApplicationService(
                cardRepository,
                threadRepository,
                boardWorkflowUseCase,
                workflowAssignmentPort,
                golemDirectoryUseCase,
                teamWorkflowUseCase,
                objectiveWorkflowUseCase,
                workflowAuditPort);

        Card epic = service.createCard(new CardCreateCommand(
                "service-1",
                "Launch epic",
                "Big delivery container",
                "Coordinate the epic",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.EPIC,
                null,
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin");

        assertThrows(IllegalArgumentException.class, () -> service.createCard(new CardCreateCommand(
                "service-1",
                "Invalid epic child",
                "Should fail",
                "Try to nest an epic",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.EPIC,
                epic.getId(),
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin"));
    }

    @Test
    void shouldRejectReviewCardsPointingAtReviewCards() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        ThreadRepository threadRepository = mock(ThreadRepository.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        WorkflowAssignmentPort workflowAssignmentPort = mock(WorkflowAssignmentPort.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        ObjectiveWorkflowUseCase objectiveWorkflowUseCase = mock(ObjectiveWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(board());

        CardWorkflowApplicationService service = new CardWorkflowApplicationService(
                cardRepository,
                threadRepository,
                boardWorkflowUseCase,
                workflowAssignmentPort,
                golemDirectoryUseCase,
                teamWorkflowUseCase,
                objectiveWorkflowUseCase,
                workflowAuditPort);

        Card epic = service.createCard(new CardCreateCommand(
                "service-1",
                "Launch epic",
                "Big delivery container",
                "Coordinate the epic",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.EPIC,
                null,
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin");

        Card task = service.createCard(new CardCreateCommand(
                "service-1",
                "Implementation task",
                "Child work item",
                "Implement the child task",
                "inbox",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                null,
                epic.getId(),
                null,
                null,
                List.of()),
                "operator-1",
                "Hive Admin");

        Card review = service.createCard(new CardCreateCommand(
                "service-1",
                "Review implementation",
                "Review child work",
                "Review the child task",
                "review",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.REVIEW,
                null,
                null,
                task.getId(),
                List.of()),
                "operator-1",
                "Hive Admin");

        assertThrows(IllegalArgumentException.class, () -> service.createCard(new CardCreateCommand(
                "service-1",
                "Invalid nested review",
                "Should fail",
                "Review the review",
                "review",
                null,
                null,
                null,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.REVIEW,
                null,
                null,
                review.getId(),
                List.of()),
                "operator-1",
                "Hive Admin"));
    }

    private Board board() {
        return Board.builder()
                .id("service-1")
                .slug("service-1")
                .name("Service 1")
                .defaultAssignmentPolicy(CardAssignmentPolicy.MANUAL)
                .flow(BoardFlowDefinition.builder()
                        .flowId("engineering")
                        .name("Engineering")
                        .defaultColumnId("inbox")
                        .columns(List.of(
                                BoardColumn.builder().id("inbox").name("Inbox").build(),
                                BoardColumn.builder().id("in_progress").name("In progress").build(),
                                BoardColumn.builder().id("review").name("Review").build(),
                                BoardColumn.builder().id("done").name("Done").build()))
                        .build())
                .team(BoardTeam.builder().build())
                .build();
    }

    private static final class InMemoryCardRepository implements CardRepository {

        private final LinkedHashMap<String, Card> cards = new LinkedHashMap<>();

        @Override
        public List<Card> list() {
            return new ArrayList<>(cards.values());
        }

        @Override
        public Optional<Card> findById(String cardId) {
            return Optional.ofNullable(cards.get(cardId));
        }

        @Override
        public void save(Card card) {
            cards.put(card.getId(), card);
        }
    }
}
