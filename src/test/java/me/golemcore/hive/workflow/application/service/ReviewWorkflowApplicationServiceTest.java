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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.BoardSignalDecision;
import me.golemcore.hive.domain.model.BoardSignalMapping;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardReviewDecision;
import me.golemcore.hive.domain.model.CardReviewStatus;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReviewWorkflowApplicationServiceTest {

    @Test
    void requestReviewShouldStoreMetadataAndRejectSelfReview() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("in_progress")
                .assigneeGolemId("golem-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-1")
                .kind(CardKind.TASK)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        cardRepository.save(implementationCard);

        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        when(cardWorkflowUseCase.getCard("card-1")).thenAnswer(invocation -> cardRepository.get("card-1"));
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        ReviewWorkflowApplicationService service = new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                teamWorkflowUseCase,
                golemDirectoryUseCase,
                threadWorkflowUseCase,
                workflowAuditPort);

        when(golemDirectoryUseCase.findGolem("golem-2")).thenReturn(Optional.of(Golem.builder().id("golem-2").build()));
        when(golemDirectoryUseCase.findGolem("golem-3")).thenReturn(Optional.of(Golem.builder().id("golem-3").build()));

        Card reviewed = service.requestReview(
                "card-1",
                List.of("golem-2", "golem-2", "golem-3"),
                null,
                2,
                "operator-1",
                "Hive Admin");

        assertEquals(CardReviewStatus.REQUIRED, reviewed.getReviewStatus());
        assertEquals(List.of("golem-2", "golem-3"), reviewed.getReviewerGolemIds());
        assertEquals(2, reviewed.getRequiredReviewCount());
        assertEquals(CardReviewStatus.REQUIRED, cardRepository.get("card-1").getReviewStatus());
        verify(workflowAuditPort).record(any(AuditEvent.AuditEventBuilder.class));

        assertThrows(IllegalArgumentException.class, () -> service.requestReview(
                "card-1",
                List.of("golem-1"),
                null,
                null,
                "operator-1",
                "Hive Admin"));
    }

    @Test
    void requestReviewShouldRejectNonTaskCards() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        Card reviewCard = Card.builder()
                .id("card-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .title("Review feature")
                .prompt("Review implementation")
                .columnId("review")
                .assigneeGolemId("golem-2")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-review-1")
                .kind(CardKind.REVIEW)
                .reviewOfCardId("card-1")
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        cardRepository.save(reviewCard);

        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        when(cardWorkflowUseCase.getCard("card-review-1"))
                .thenAnswer(invocation -> cardRepository.get("card-review-1"));

        ReviewWorkflowApplicationService service = new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowUseCase,
                mock(BoardWorkflowUseCase.class),
                mock(TeamWorkflowUseCase.class),
                mock(GolemDirectoryUseCase.class),
                mock(ThreadWorkflowUseCase.class),
                mock(WorkflowAuditPort.class));

        assertThrows(IllegalArgumentException.class, () -> service.requestReview(
                "card-review-1",
                List.of("golem-3"),
                null,
                null,
                "operator-1",
                "Hive Admin"));
    }

    @Test
    void activateReviewShouldCreateReviewCardAndPickEligibleReviewer() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Implement feature")
                .description("Deliver the change")
                .prompt("Build the feature")
                .columnId("in_progress")
                .assigneeGolemId("golem-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-1")
                .kind(CardKind.TASK)
                .reviewStatus(CardReviewStatus.REQUIRED)
                .reviewerGolemIds(new ArrayList<>(List.of("golem-2", "golem-3")))
                .reviewerTeamId("team-review")
                .requiredReviewCount(2)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        cardRepository.save(implementationCard);

        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        when(cardWorkflowUseCase.getCard("card-1")).thenAnswer(invocation -> cardRepository.get("card-1"));
        when(cardWorkflowUseCase.createCard(any(CardCreateCommand.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    CardCreateCommand command = invocation.getArgument(0, CardCreateCommand.class);
                    Card reviewCard = Card.builder()
                            .id("card-review-1")
                            .serviceId(command.serviceId())
                            .boardId(command.serviceId())
                            .kind(command.kind())
                            .parentCardId(command.parentCardId())
                            .epicCardId(command.epicCardId())
                            .reviewOfCardId(command.reviewOfCardId())
                            .teamId(command.teamId())
                            .objectiveId(command.objectiveId())
                            .threadId("thread-review-1")
                            .title(command.title())
                            .description(command.description())
                            .prompt(command.prompt())
                            .columnId(command.columnId())
                            .assigneeGolemId(command.assigneeGolemId())
                            .assignmentPolicy(command.assignmentPolicy())
                            .createdAt(Instant.parse("2026-04-10T00:10:00Z"))
                            .updatedAt(Instant.parse("2026-04-10T00:10:00Z"))
                            .build();
                    cardRepository.save(reviewCard);
                    return reviewCard;
                });

        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewColumns());
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        when(teamWorkflowUseCase.getTeam("team-review")).thenReturn(Team.builder()
                .id("team-review")
                .golemIds(Set.of("golem-2", "golem-3"))
                .build());
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder().id("golem-1").build()));
        when(golemDirectoryUseCase.findGolem("golem-2")).thenReturn(Optional.of(Golem.builder().id("golem-2").build()));
        when(golemDirectoryUseCase.findGolem("golem-3")).thenReturn(Optional.of(Golem.builder().id("golem-3").build()));

        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());

        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        when(executionOperationsUseCase.listCommands("thread-review-1")).thenReturn(List.of());
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        ReviewWorkflowApplicationService service = new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                teamWorkflowUseCase,
                golemDirectoryUseCase,
                threadWorkflowUseCase,
                workflowAuditPort);

        Card reviewCard = service.activateReviewForCompletedWork("card-1", "system", "Hive");

        assertNotNull(reviewCard);
        assertEquals(CardKind.REVIEW, reviewCard.getKind());
        assertEquals("card-1", reviewCard.getReviewOfCardId());
        assertEquals("golem-2", reviewCard.getAssigneeGolemId());
        assertEquals(CardReviewStatus.IN_REVIEW, cardRepository.get("card-1").getReviewStatus());
        assertEquals(CardReviewStatus.IN_REVIEW, cardRepository.get("card-review-1").getReviewStatus());
        ArgumentCaptor<CardCreateCommand> commandCaptor = ArgumentCaptor.forClass(CardCreateCommand.class);
        verify(cardWorkflowUseCase).createCard(commandCaptor.capture(), eq("system"), eq("Hive"));
        assertEquals(CardKind.REVIEW, commandCaptor.getValue().kind());
        assertEquals("card-1", commandCaptor.getValue().reviewOfCardId());
        verify(workflowAuditPort).record(any(AuditEvent.AuditEventBuilder.class));
    }

    @Test
    void applyDecisionShouldUpdateImplementationAndReviewCards() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("in_progress")
                .assigneeGolemId("golem-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-1")
                .kind(CardKind.TASK)
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        Card reviewCard = Card.builder()
                .id("card-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Review feature")
                .prompt("Review implementation: Implement feature")
                .columnId("review")
                .assigneeGolemId("golem-2")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-review-1")
                .kind(CardKind.REVIEW)
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .reviewOfCardId("card-1")
                .createdAt(Instant.parse("2026-04-10T00:05:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:05:00Z"))
                .build();
        cardRepository.save(implementationCard);
        cardRepository.save(reviewCard);

        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        when(cardWorkflowUseCase.getCard("card-review-1"))
                .thenAnswer(invocation -> cardRepository.get("card-review-1"));
        when(cardWorkflowUseCase.getCard("card-1")).thenAnswer(invocation -> cardRepository.get("card-1"));
        when(cardWorkflowUseCase.moveCard(anyString(), anyString(), any(), any(), anyString(), anyString(),
                anyString()))
                .thenAnswer(invocation -> {
                    String cardId = invocation.getArgument(0, String.class);
                    String targetColumnId = invocation.getArgument(1, String.class);
                    Card card = cardRepository.get(cardId);
                    card.setColumnId(targetColumnId);
                    card.setUpdatedAt(Instant.parse("2026-04-10T00:20:00Z"));
                    cardRepository.save(card);
                    return card;
                });

        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewColumns());
        TeamWorkflowUseCase teamWorkflowUseCase = mock(TeamWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());

        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);

        ReviewWorkflowApplicationService service = new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                teamWorkflowUseCase,
                golemDirectoryUseCase,
                threadWorkflowUseCase,
                workflowAuditPort);

        Card reviewDecision = service.applyDecision(
                "card-review-1",
                CardReviewDecision.APPROVED,
                "Looks good",
                "Ship it",
                "golem-2",
                "Reviewer");

        assertEquals(CardReviewStatus.APPROVED, reviewDecision.getReviewStatus());
        assertEquals(CardReviewStatus.APPROVED, cardRepository.get("card-1").getReviewStatus());
        assertEquals("done", cardRepository.get("card-1").getColumnId());
        assertEquals("done", cardRepository.get("card-review-1").getColumnId());
        verify(threadWorkflowUseCase).appendMessage(
                any(ThreadRecord.class),
                any(),
                any(),
                any(),
                eq(ThreadMessageType.NOTE),
                eq(ThreadParticipantType.GOLEM),
                eq("golem-2"),
                eq("Reviewer"),
                eq("APPROVED: Looks good\n\nShip it"),
                any());
        verify(workflowAuditPort).record(any(AuditEvent.AuditEventBuilder.class));
    }

    @Test
    void applyDecisionShouldKeepImplementationInReviewUntilRequiredApprovalCountIsMet() {
        InMemoryCardRepository cardRepository = new InMemoryCardRepository();
        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("review")
                .assigneeGolemId("golem-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-1")
                .kind(CardKind.TASK)
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .requiredReviewCount(2)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        Card reviewCard = Card.builder()
                .id("card-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .title("Review feature")
                .prompt("Review implementation: Implement feature")
                .columnId("review")
                .assigneeGolemId("golem-2")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-review-1")
                .kind(CardKind.REVIEW)
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .reviewOfCardId("card-1")
                .requiredReviewCount(2)
                .createdAt(Instant.parse("2026-04-10T00:05:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:05:00Z"))
                .build();
        cardRepository.save(implementationCard);
        cardRepository.save(reviewCard);

        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        when(cardWorkflowUseCase.getCard("card-review-1"))
                .thenAnswer(invocation -> cardRepository.get("card-review-1"));
        when(cardWorkflowUseCase.getCard("card-1")).thenAnswer(invocation -> cardRepository.get("card-1"));
        when(cardWorkflowUseCase.moveCard(anyString(), anyString(), any(), any(), anyString(), anyString(),
                anyString()))
                .thenAnswer(invocation -> {
                    String cardId = invocation.getArgument(0, String.class);
                    String targetColumnId = invocation.getArgument(1, String.class);
                    Card card = cardRepository.get(cardId);
                    card.setColumnId(targetColumnId);
                    cardRepository.save(card);
                    return card;
                });

        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());

        ReviewWorkflowApplicationService service = new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowUseCase,
                boardWorkflowUseCaseReturningReviewColumns(),
                mock(TeamWorkflowUseCase.class),
                mock(GolemDirectoryUseCase.class),
                threadWorkflowUseCase,
                mock(WorkflowAuditPort.class));

        service.applyDecision(
                "card-review-1",
                CardReviewDecision.APPROVED,
                "Looks good",
                null,
                "golem-2",
                "Reviewer");

        assertEquals(CardReviewStatus.APPROVED, cardRepository.get("card-review-1").getReviewStatus());
        assertEquals(CardReviewStatus.IN_REVIEW, cardRepository.get("card-1").getReviewStatus());
        assertEquals("review", cardRepository.get("card-1").getColumnId());
        assertEquals("done", cardRepository.get("card-review-1").getColumnId());
    }

    private Board boardWithReviewColumns() {
        return Board.builder()
                .id("service-1")
                .flow(BoardFlowDefinition.builder()
                        .flowId("engineering")
                        .name("Engineering")
                        .defaultColumnId("inbox")
                        .columns(List.of(
                                BoardColumn.builder().id("inbox").name("Inbox").build(),
                                BoardColumn.builder().id("in_progress").name("In progress").build(),
                                BoardColumn.builder().id("review").name("Review").build(),
                                BoardColumn.builder().id("done").name("Done").build()))
                        .signalMappings(List.of(
                                BoardSignalMapping.builder()
                                        .signalType("WORK_COMPLETED")
                                        .decision(BoardSignalDecision.AUTO_APPLY)
                                        .targetColumnId("review")
                                        .build(),
                                BoardSignalMapping.builder()
                                        .signalType("REVIEW_APPROVED")
                                        .decision(BoardSignalDecision.AUTO_APPLY)
                                        .targetColumnId("done")
                                        .build(),
                                BoardSignalMapping.builder()
                                        .signalType("CHANGES_REQUESTED")
                                        .decision(BoardSignalDecision.AUTO_APPLY)
                                        .targetColumnId("done")
                                        .build()))
                        .build())
                .build();
    }

    private BoardWorkflowUseCase boardWorkflowUseCaseReturningReviewColumns() {
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewColumns());
        return boardWorkflowUseCase;
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

        private Card get(String cardId) {
            return cards.get(cardId);
        }
    }
}
