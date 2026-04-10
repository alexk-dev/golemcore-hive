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

package me.golemcore.hive.execution.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.BoardSignalDecision;
import me.golemcore.hive.domain.model.BoardSignalMapping;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CardReviewDecision;
import me.golemcore.hive.domain.model.CardReviewStatus;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.SignalResolutionOutcome;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LifecycleSignalResolutionApplicationServiceTest {

    @Test
    void shouldActivateReviewAndDispatchReviewerCommandOnWorkCompleted() {
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);

        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.TASK)
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("in_progress")
                .threadId("thread-1")
                .reviewStatus(CardReviewStatus.REQUIRED)
                .reviewerGolemIds(List.of("golem-reviewer"))
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        Card reviewCard = Card.builder()
                .id("card-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.REVIEW)
                .title("Review feature")
                .prompt("Review implementation: Implement feature")
                .columnId("review")
                .threadId("thread-review-1")
                .assigneeGolemId("golem-reviewer")
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .reviewOfCardId("card-1")
                .createdAt(Instant.parse("2026-04-10T00:10:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:10:00Z"))
                .build();

        when(cardWorkflowUseCase.getCard("card-1")).thenReturn(implementationCard);
        when(cardWorkflowUseCase.moveCard(
                eq("card-1"),
                eq("review"),
                any(),
                eq(CardTransitionOrigin.BOARD_AUTOMATION),
                eq("golem-1"),
                eq("Runner"),
                anyString())).thenAnswer(invocation -> {
                    implementationCard.setColumnId("review");
                    implementationCard.setUpdatedAt(Instant.parse("2026-04-10T00:20:00Z"));
                    return implementationCard;
                });
        when(reviewWorkflowUseCase.activateReviewForCompletedWork("card-1", "golem-1", "Runner"))
                .thenReturn(reviewCard);
        when(executionOperationsUseCase.listCommands("thread-review-1")).thenReturn(List.of());
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder()
                .id("golem-1")
                .displayName("Runner")
                .build()));
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewSignals());
        when(boardWorkflowUseCase.isTransitionAllowed(any(), eq("in_progress"), eq("review"))).thenReturn(true);
        when(boardWorkflowUseCase.isTransitionReachable(any(), eq("in_progress"), eq("review"))).thenReturn(true);

        LifecycleSignalResolutionApplicationService service = new LifecycleSignalResolutionApplicationService(
                boardWorkflowUseCase,
                cardWorkflowUseCase,
                reviewWorkflowUseCase,
                threadWorkflowUseCase,
                golemDirectoryUseCase,
                executionOperationsUseCase,
                operatorUpdatePublisherPort);

        CardLifecycleSignal signal = CardLifecycleSignal.builder()
                .id("signal-1")
                .cardId("card-1")
                .golemId("golem-1")
                .signalType(LifecycleSignalType.WORK_COMPLETED)
                .summary("Work completed")
                .createdAt(Instant.parse("2026-04-10T00:15:00Z"))
                .build();

        CardLifecycleSignal resolved = service.resolve(signal);

        assertEquals(SignalResolutionOutcome.AUTO_APPLIED, resolved.getResolutionOutcome());
        assertEquals("review", resolved.getResolvedTargetColumnId());
        verify(reviewWorkflowUseCase).activateReviewForCompletedWork("card-1", "golem-1", "Runner");
        verify(executionOperationsUseCase).createCommand(
                eq("thread-review-1"),
                eq("Review implementation: Implement feature"),
                isNull(),
                eq(0L),
                isNull(),
                eq("system"),
                eq("Hive"));
        verify(threadWorkflowUseCase).appendMessage(
                any(ThreadRecord.class),
                isNull(),
                isNull(),
                eq("signal-1"),
                eq(ThreadMessageType.SIGNAL),
                eq(ThreadParticipantType.SYSTEM),
                eq("golem-1"),
                eq("Runner"),
                anyString(),
                eq(Instant.parse("2026-04-10T00:15:00Z")));
        verify(operatorUpdatePublisherPort).publish(any());
    }

    @Test
    void shouldNotActivateReviewWhenWorkCompletedIsOnlySuggested() {
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);

        Card implementationCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.TASK)
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("in_progress")
                .threadId("thread-1")
                .reviewStatus(CardReviewStatus.REQUIRED)
                .reviewerGolemIds(List.of("golem-reviewer"))
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();

        when(cardWorkflowUseCase.getCard("card-1")).thenReturn(implementationCard);
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder()
                .id("golem-1")
                .displayName("Runner")
                .build()));
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithSuggestedWorkCompleted());
        when(boardWorkflowUseCase.isTransitionAllowed(any(), eq("in_progress"), eq("review"))).thenReturn(true);

        LifecycleSignalResolutionApplicationService service = new LifecycleSignalResolutionApplicationService(
                boardWorkflowUseCase,
                cardWorkflowUseCase,
                reviewWorkflowUseCase,
                threadWorkflowUseCase,
                golemDirectoryUseCase,
                executionOperationsUseCase,
                operatorUpdatePublisherPort);

        CardLifecycleSignal signal = CardLifecycleSignal.builder()
                .id("signal-1")
                .cardId("card-1")
                .golemId("golem-1")
                .signalType(LifecycleSignalType.WORK_COMPLETED)
                .summary("Work completed")
                .createdAt(Instant.parse("2026-04-10T00:15:00Z"))
                .build();

        CardLifecycleSignal resolved = service.resolve(signal);

        assertEquals(SignalResolutionOutcome.SUGGESTED, resolved.getResolutionOutcome());
        verify(reviewWorkflowUseCase, org.mockito.Mockito.never()).activateReviewForCompletedWork(
                anyString(),
                anyString(),
                anyString());
        verify(executionOperationsUseCase, org.mockito.Mockito.never()).createCommand(
                anyString(),
                anyString(),
                any(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(),
                anyString(),
                anyString());
    }

    @Test
    void shouldDispatchNextReviewCommandWhenApprovalCountIsNotMet() {
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);

        Card reviewCard = Card.builder()
                .id("card-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.REVIEW)
                .title("Review feature")
                .prompt("Review implementation: Implement feature")
                .columnId("review")
                .threadId("thread-review-1")
                .assigneeGolemId("golem-2")
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .reviewOfCardId("card-1")
                .createdAt(Instant.parse("2026-04-10T00:05:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:05:00Z"))
                .build();
        Card nextReviewCard = Card.builder()
                .id("card-review-2")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.REVIEW)
                .title("Review feature")
                .prompt("Second review")
                .columnId("review")
                .threadId("thread-review-2")
                .assigneeGolemId("golem-3")
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .reviewOfCardId("card-1")
                .createdAt(Instant.parse("2026-04-10T00:10:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:10:00Z"))
                .build();

        when(cardWorkflowUseCase.getCard("card-review-1")).thenReturn(reviewCard);
        when(reviewWorkflowUseCase.applyDecision(
                eq("card-review-1"),
                eq(CardReviewDecision.APPROVED),
                eq("Approved"),
                isNull(),
                eq("golem-2"),
                eq("Reviewer"))).thenReturn(reviewCard);
        when(reviewWorkflowUseCase.activateReviewForCompletedWork("card-1", "golem-2", "Reviewer"))
                .thenReturn(nextReviewCard);
        when(executionOperationsUseCase.listCommands("thread-review-2")).thenReturn(List.of());
        when(golemDirectoryUseCase.findGolem("golem-2")).thenReturn(Optional.of(Golem.builder()
                .id("golem-2")
                .displayName("Reviewer")
                .build()));
        when(threadWorkflowUseCase.getThreadByCardId("card-review-1")).thenReturn(ThreadRecord.builder()
                .id("thread-review-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-review-1")
                .title("Review feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewSignals());
        when(boardWorkflowUseCase.isTransitionReachable(any(), eq("review"), eq("done"))).thenReturn(true);

        LifecycleSignalResolutionApplicationService service = new LifecycleSignalResolutionApplicationService(
                boardWorkflowUseCase,
                cardWorkflowUseCase,
                reviewWorkflowUseCase,
                threadWorkflowUseCase,
                golemDirectoryUseCase,
                executionOperationsUseCase,
                operatorUpdatePublisherPort);

        CardLifecycleSignal signal = CardLifecycleSignal.builder()
                .id("signal-2")
                .cardId("card-review-1")
                .golemId("golem-2")
                .signalType(LifecycleSignalType.REVIEW_APPROVED)
                .summary("Approved")
                .createdAt(Instant.parse("2026-04-10T00:30:00Z"))
                .build();

        CardLifecycleSignal resolved = service.resolve(signal);

        assertEquals(SignalResolutionOutcome.AUTO_APPLIED, resolved.getResolutionOutcome());
        verify(executionOperationsUseCase).createCommand(
                eq("thread-review-2"),
                eq("Second review"),
                isNull(),
                eq(0L),
                isNull(),
                eq("system"),
                eq("Hive"));
    }

    @Test
    void shouldRejectReviewDecisionSignalsForNonReviewCards() {
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        ThreadWorkflowUseCase threadWorkflowUseCase = mock(ThreadWorkflowUseCase.class);
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);

        Card taskCard = Card.builder()
                .id("card-1")
                .serviceId("service-1")
                .boardId("service-1")
                .kind(CardKind.TASK)
                .title("Implement feature")
                .prompt("Build the feature")
                .columnId("review")
                .threadId("thread-1")
                .reviewStatus(CardReviewStatus.IN_REVIEW)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();

        when(cardWorkflowUseCase.getCard("card-1")).thenReturn(taskCard);
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithReviewSignals());
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder()
                .id("golem-1")
                .displayName("Runner")
                .build()));
        when(threadWorkflowUseCase.getThreadByCardId("card-1")).thenReturn(ThreadRecord.builder()
                .id("thread-1")
                .serviceId("service-1")
                .boardId("service-1")
                .cardId("card-1")
                .title("Implement feature")
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build());

        LifecycleSignalResolutionApplicationService service = new LifecycleSignalResolutionApplicationService(
                boardWorkflowUseCase,
                cardWorkflowUseCase,
                reviewWorkflowUseCase,
                threadWorkflowUseCase,
                golemDirectoryUseCase,
                executionOperationsUseCase,
                operatorUpdatePublisherPort);

        CardLifecycleSignal signal = CardLifecycleSignal.builder()
                .id("signal-2")
                .cardId("card-1")
                .golemId("golem-1")
                .signalType(LifecycleSignalType.REVIEW_APPROVED)
                .summary("Approved")
                .createdAt(Instant.parse("2026-04-10T00:30:00Z"))
                .build();

        CardLifecycleSignal resolved = service.resolve(signal);

        assertEquals(SignalResolutionOutcome.REJECTED, resolved.getResolutionOutcome());
        assertNull(resolved.getResolvedTargetColumnId());
        verify(reviewWorkflowUseCase, org.mockito.Mockito.never()).applyDecision(
                anyString(),
                any(CardReviewDecision.class),
                any(),
                any(),
                anyString(),
                anyString());
        verify(threadWorkflowUseCase).appendMessage(
                any(ThreadRecord.class),
                isNull(),
                isNull(),
                eq("signal-2"),
                eq(ThreadMessageType.SIGNAL),
                eq(ThreadParticipantType.SYSTEM),
                eq("golem-1"),
                eq("Runner"),
                anyString(),
                eq(Instant.parse("2026-04-10T00:30:00Z")));
    }

    private Board boardWithReviewSignals() {
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

    private Board boardWithSuggestedWorkCompleted() {
        return Board.builder()
                .id("service-1")
                .flow(BoardFlowDefinition.builder()
                        .flowId("engineering")
                        .name("Engineering")
                        .defaultColumnId("inbox")
                        .columns(List.of(
                                BoardColumn.builder().id("inbox").name("Inbox").build(),
                                BoardColumn.builder().id("in_progress").name("In progress").build(),
                                BoardColumn.builder().id("review").name("Review").build()))
                        .signalMappings(List.of(BoardSignalMapping.builder()
                                .signalType("WORK_COMPLETED")
                                .decision(BoardSignalDecision.SUGGEST_ONLY)
                                .targetColumnId("review")
                                .build()))
                        .build())
                .build();
    }
}
