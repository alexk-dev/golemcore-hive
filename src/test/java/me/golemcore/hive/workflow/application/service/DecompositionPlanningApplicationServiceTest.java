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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanLink;
import me.golemcore.hive.domain.model.DecompositionPlanLinkType;
import me.golemcore.hive.domain.model.DecompositionPlanStatus;
import me.golemcore.hive.domain.model.DecompositionAssignmentSpec;
import me.golemcore.hive.domain.model.DecompositionReviewSpec;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.DecompositionAssignmentSpecCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanApplicationResult;
import me.golemcore.hive.workflow.application.DecompositionPlanItemCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanLinkCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanProposalCommand;
import me.golemcore.hive.workflow.application.DecompositionReviewSpecCommand;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.DecompositionPlanRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowPolicyPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecompositionPlanningApplicationServiceTest {

    @Test
    void shouldCreateApproveAndApplyPlanInDependencyOrder() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-source")
                .title("Source")
                .columnId("inbox")
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        LinkedHashMap<String, Card> cards = new LinkedHashMap<>();
        cards.put(sourceCard.getId(), sourceCard);
        when(cardWorkflowUseCase.findCard(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(cards.get(invocation.getArgument(0, String.class))));

        AtomicInteger counter = new AtomicInteger();
        when(cardWorkflowUseCase.createCard(
                any(CardCreateCommand.class),
                anyString(),
                anyString())).thenAnswer(invocation -> {
                    int index = counter.incrementAndGet();
                    CardCreateCommand command = invocation.getArgument(0, CardCreateCommand.class);
                    String threadId = "thread-" + index;
                    Card card = Card.builder()
                            .id("card-" + index)
                            .serviceId(command.serviceId())
                            .boardId(command.serviceId())
                            .kind(command.kind())
                            .parentCardId(command.parentCardId())
                            .epicCardId(command.epicCardId())
                            .dependsOnCardIds(command.dependsOnCardIds())
                            .threadId(threadId)
                            .title(command.title())
                            .description(command.description())
                            .prompt(command.prompt())
                            .columnId(command.columnId() != null ? command.columnId() : "inbox")
                            .assignmentPolicy(command.assignmentPolicy() != null
                                    ? command.assignmentPolicy()
                                    : CardAssignmentPolicy.MANUAL)
                            .createdAt(Instant.parse("2026-04-10T00:00:0" + index + "Z"))
                            .updatedAt(Instant.parse("2026-04-10T00:00:0" + index + "Z"))
                            .build();
                    cards.put(card.getId(), card);
                    return card;
                });

        DecompositionPlan plan = service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        null,
                        "Break the source work into two tasks",
                        List.of(
                                new DecompositionPlanItemCommand(
                                        "task-1",
                                        null,
                                        "Analyze source",
                                        "Analyze the source problem",
                                        "Analyze the source problem and produce a concise breakdown.",
                                        List.of("analysis complete"),
                                        new DecompositionAssignmentSpecCommand(
                                                null,
                                                null,
                                                null,
                                                null,
                                                CardAssignmentPolicy.MANUAL,
                                                false),
                                        null),
                                new DecompositionPlanItemCommand(
                                        "task-2",
                                        null,
                                        "Implement slice",
                                        "Implement the first slice",
                                        "Implement the first slice and report completion.",
                                        List.of("implementation complete"),
                                        new DecompositionAssignmentSpecCommand(
                                                null,
                                                null,
                                                null,
                                                null,
                                                CardAssignmentPolicy.MANUAL,
                                                false),
                                        new DecompositionReviewSpecCommand(List.of("golem-reviewer"), null, 1))),
                        List.of(new DecompositionPlanLinkCommand("task-2", "task-1",
                                DecompositionPlanLinkType.DEPENDS_ON))),
                "operator-1",
                "Hive Admin");
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithInboxColumn());

        assertNotNull(plan.getId());
        assertEquals(DecompositionPlanStatus.DRAFT, plan.getStatus());
        assertEquals("service-1", plan.getServiceId());
        verify(workflowAuditPort).record(any(AuditEvent.AuditEventBuilder.class));

        DecompositionPlan approved = service.approvePlan(plan.getId(), "operator-1", "Hive Admin", "Approved");
        assertEquals(DecompositionPlanStatus.APPROVED, approved.getStatus());

        DecompositionPlanApplicationResult result = service.applyPlan(plan.getId(), "operator-1", "Hive Admin");
        assertEquals(DecompositionPlanStatus.APPLIED, result.plan().getStatus());
        assertEquals(2, result.createdCards().size());
        assertEquals("task-1", result.createdCards().get(0).clientItemId());
        assertEquals("card-1", result.createdCards().get(0).cardId());
        assertEquals("task-2", result.createdCards().get(1).clientItemId());
        assertEquals("card-2", result.createdCards().get(1).cardId());

        ArgumentCaptor<CardCreateCommand> commandCaptor = ArgumentCaptor.forClass(CardCreateCommand.class);
        verify(cardWorkflowUseCase, times(2)).createCard(commandCaptor.capture(), anyString(), anyString());
        assertEquals(List.of("Analyze source", "Implement slice"), commandCaptor.getAllValues().stream()
                .map(CardCreateCommand::title)
                .toList());
        verify(reviewWorkflowUseCase).requestReview(
                "card-2",
                List.of("golem-reviewer"),
                null,
                1,
                "operator-1",
                "Hive Admin");
        verify(workflowAuditPort, times(3)).record(any(AuditEvent.AuditEventBuilder.class));
        assertEquals(3, repository.getSaveCount());
        assertEquals("card-1", repository.get(plan.getId()).getItems().get(0).getCreatedCardId());
        assertEquals("card-2", repository.get(plan.getId()).getItems().get(1).getCreatedCardId());

        DecompositionPlanApplicationResult secondResult = service.applyPlan(plan.getId(), "operator-1", "Hive Admin");
        assertTrue(secondResult.alreadyApplied());
        assertEquals(2, secondResult.createdCards().size());
        verify(cardWorkflowUseCase, times(2)).createCard(any(CardCreateCommand.class), anyString(), anyString());
    }

    @Test
    void shouldNotPersistPartialPlanWhenALaterItemFailsDuringMaterialization() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .teamId("team-1")
                .objectiveId("objective-1")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .threadId("thread-source")
                .title("Source")
                .columnId("inbox")
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .build();
        LinkedHashMap<String, Card> cards = new LinkedHashMap<>();
        cards.put(sourceCard.getId(), sourceCard);
        when(cardWorkflowUseCase.findCard(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(cards.get(invocation.getArgument(0, String.class))));
        when(boardWorkflowUseCase.getBoard("service-1")).thenReturn(boardWithInboxColumn());

        AtomicInteger counter = new AtomicInteger();
        when(cardWorkflowUseCase.createCard(any(CardCreateCommand.class), anyString(), anyString())).thenAnswer(
                invocation -> {
                    int index = counter.incrementAndGet();
                    if (index == 2) {
                        throw new IllegalStateException("boom");
                    }
                    CardCreateCommand command = invocation.getArgument(0, CardCreateCommand.class);
                    Card card = Card.builder()
                            .id("card-" + index)
                            .serviceId(command.serviceId())
                            .boardId(command.serviceId())
                            .kind(command.kind())
                            .threadId("thread-" + index)
                            .title(command.title())
                            .description(command.description())
                            .prompt(command.prompt())
                            .columnId(command.columnId() != null ? command.columnId() : "inbox")
                            .assignmentPolicy(command.assignmentPolicy() != null
                                    ? command.assignmentPolicy()
                                    : CardAssignmentPolicy.MANUAL)
                            .createdAt(Instant.parse("2026-04-10T00:00:0" + index + "Z"))
                            .updatedAt(Instant.parse("2026-04-10T00:00:0" + index + "Z"))
                            .build();
                    cards.put("card-" + index, card);
                    return card;
                });

        DecompositionPlan plan = service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        null,
                        "Break work",
                        List.of(
                                new DecompositionPlanItemCommand(
                                        "task-1",
                                        null,
                                        "Task 1",
                                        null,
                                        "Prompt 1",
                                        List.of(),
                                        null,
                                        null),
                                new DecompositionPlanItemCommand(
                                        "task-2",
                                        null,
                                        "Task 2",
                                        null,
                                        "Prompt 2",
                                        List.of(),
                                        null,
                                        null)),
                        List.of()),
                "operator-1",
                "Hive Admin");
        service.approvePlan(plan.getId(), "operator-1", "Hive Admin", "Approved");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.applyPlan(plan.getId(), "operator-1", "Hive Admin"));
        assertEquals("boom", exception.getMessage());
        assertEquals(2, repository.getSaveCount());
        DecompositionPlan persisted = repository.get(plan.getId());
        assertEquals(DecompositionPlanStatus.APPROVED, persisted.getStatus());
        assertNull(persisted.getItems().get(0).getCreatedCardId());
        assertNull(persisted.getItems().get(0).getMaterializedAt());
        assertNull(persisted.getItems().get(1).getCreatedCardId());
        assertNull(persisted.getItems().get(1).getMaterializedAt());
        verify(reviewWorkflowUseCase, never()).requestReview(anyString(), any(), any(), any(), anyString(),
                anyString());
    }

    @Test
    void shouldRejectCyclesInPlanGraph() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .title("Source")
                .columnId("inbox")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .build();
        when(cardWorkflowUseCase.findCard("card-source")).thenReturn(Optional.of(sourceCard));

        assertThrows(IllegalArgumentException.class, () -> service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        null,
                        "Cycle",
                        List.of(
                                new DecompositionPlanItemCommand(
                                        "a",
                                        null,
                                        "A",
                                        null,
                                        "A prompt",
                                        List.of(),
                                        null,
                                        null),
                                new DecompositionPlanItemCommand(
                                        "b",
                                        null,
                                        "B",
                                        null,
                                        "B prompt",
                                        List.of(),
                                        null,
                                        null)),
                        List.of(
                                new DecompositionPlanLinkCommand("a", "b", DecompositionPlanLinkType.DEPENDS_ON),
                                new DecompositionPlanLinkCommand("b", "a", DecompositionPlanLinkType.DEPENDS_ON))),
                "operator-1",
                "Hive Admin"));
    }

    @Test
    void shouldRejectMaterializedReviewItems() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .title("Source")
                .columnId("inbox")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .build();
        when(cardWorkflowUseCase.findCard("card-source")).thenReturn(Optional.of(sourceCard));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        null,
                        "Review item",
                        List.of(new DecompositionPlanItemCommand(
                                "review-1",
                                CardKind.REVIEW,
                                "Review implementation",
                                null,
                                "Review prompt",
                                List.of(),
                                null,
                                null)),
                        List.of()),
                "operator-1",
                "Hive Admin"));
        assertEquals(
                "Use item.review to request review gates instead of materializing review cards",
                exception.getMessage());
    }

    @Test
    void shouldRejectReviewRequirementsWithoutEnoughExplicitReviewers() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .title("Source")
                .columnId("inbox")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .build();
        when(cardWorkflowUseCase.findCard("card-source")).thenReturn(Optional.of(sourceCard));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        null,
                        "Review gate",
                        List.of(new DecompositionPlanItemCommand(
                                "task-1",
                                null,
                                "Task 1",
                                null,
                                "Prompt 1",
                                List.of(),
                                null,
                                new DecompositionReviewSpecCommand(List.of("golem-reviewer"), null, 2))),
                        List.of()),
                "operator-1",
                "Hive Admin"));
        assertEquals("Not enough reviewers for requiredReviewCount", exception.getMessage());
    }

    @Test
    void shouldEnforceSdlcPolicyGateForBoundPlannerGolem() {
        InMemoryDecompositionPlanRepository repository = new InMemoryDecompositionPlanRepository();
        CardWorkflowUseCase cardWorkflowUseCase = mock(CardWorkflowUseCase.class);
        BoardWorkflowUseCase boardWorkflowUseCase = mock(BoardWorkflowUseCase.class);
        ReviewWorkflowUseCase reviewWorkflowUseCase = mock(ReviewWorkflowUseCase.class);
        WorkflowAuditPort workflowAuditPort = mock(WorkflowAuditPort.class);
        WorkflowPolicyPort workflowPolicyPort = mock(WorkflowPolicyPort.class);
        DecompositionPlanningApplicationService service = new DecompositionPlanningApplicationService(
                repository,
                cardWorkflowUseCase,
                boardWorkflowUseCase,
                reviewWorkflowUseCase,
                workflowAuditPort,
                workflowPolicyPort);

        Card sourceCard = Card.builder()
                .id("card-source")
                .serviceId("service-1")
                .boardId("service-1")
                .title("Source")
                .columnId("inbox")
                .assignmentPolicy(CardAssignmentPolicy.MANUAL)
                .build();
        when(cardWorkflowUseCase.findCard("card-source")).thenReturn(Optional.of(sourceCard));

        PolicyGroupSpec.PolicySdlcConfig sdlcPolicy = PolicyGroupSpec.PolicySdlcConfig.builder()
                .maxDecompositionFanOut(1)
                .allowedCardKinds(List.of("TASK"))
                .reviewerAssignmentEnabled(true)
                .requireReviewerSeparationOfDuties(true)
                .build();
        when(workflowPolicyPort.findSdlcPolicyForGolem("planner-1")).thenReturn(Optional.of(sdlcPolicy));

        IllegalArgumentException fanOutException = assertThrows(IllegalArgumentException.class,
                () -> service.proposePlan(
                        new DecompositionPlanProposalCommand(
                                "card-source",
                                null,
                                "planner-1",
                                "Too many items",
                                List.of(
                                        new DecompositionPlanItemCommand(
                                                "task-1",
                                                null,
                                                "Task 1",
                                                null,
                                                "Prompt 1",
                                                List.of(),
                                                null,
                                                null),
                                        new DecompositionPlanItemCommand(
                                                "task-2",
                                                null,
                                                "Task 2",
                                                null,
                                                "Prompt 2",
                                                List.of(),
                                                null,
                                                null)),
                                List.of()),
                        "operator-1",
                        "Hive Admin"));
        assertEquals("Plan fan-out exceeds policy maxDecompositionFanOut", fanOutException.getMessage());

        IllegalArgumentException kindException = assertThrows(IllegalArgumentException.class, () -> service.proposePlan(
                new DecompositionPlanProposalCommand(
                        "card-source",
                        null,
                        "planner-1",
                        "Disallowed kind",
                        List.of(new DecompositionPlanItemCommand(
                                "epic-1",
                                CardKind.EPIC,
                                "Epic 1",
                                null,
                                "Prompt 1",
                                List.of(),
                                null,
                                null)),
                        List.of()),
                "operator-1",
                "Hive Admin"));
        assertEquals("Plan item kind is not allowed by policy: EPIC", kindException.getMessage());

        IllegalArgumentException reviewException = assertThrows(IllegalArgumentException.class,
                () -> service.proposePlan(
                        new DecompositionPlanProposalCommand(
                                "card-source",
                                null,
                                "planner-1",
                                "Self review",
                                List.of(new DecompositionPlanItemCommand(
                                        "task-3",
                                        null,
                                        "Task 3",
                                        null,
                                        "Prompt 3",
                                        List.of(),
                                        null,
                                        new DecompositionReviewSpecCommand(List.of("planner-1"), null, 1))),
                                List.of()),
                        "operator-1",
                        "Hive Admin"));
        assertEquals(
                "Planner golem cannot self-review its own decomposition plan",
                reviewException.getMessage());
    }

    private static final class InMemoryDecompositionPlanRepository implements DecompositionPlanRepository {

        private final LinkedHashMap<String, DecompositionPlan> plans = new LinkedHashMap<>();
        private int saveCount;

        @Override
        public List<DecompositionPlan> list(String sourceCardId, String epicCardId, DecompositionPlanStatus status) {
            return plans.values().stream()
                    .map(InMemoryDecompositionPlanRepository::copyPlan)
                    .filter(plan -> sourceCardId == null || sourceCardId.equals(plan.getSourceCardId()))
                    .filter(plan -> epicCardId == null || epicCardId.equals(plan.getEpicCardId()))
                    .filter(plan -> status == null || status == plan.getStatus())
                    .toList();
        }

        @Override
        public Optional<DecompositionPlan> findById(String planId) {
            return Optional.ofNullable(plans.get(planId)).map(InMemoryDecompositionPlanRepository::copyPlan);
        }

        @Override
        public void save(DecompositionPlan plan) {
            plans.put(plan.getId(), copyPlan(plan));
            saveCount++;
        }

        private DecompositionPlan get(String planId) {
            return copyPlan(plans.get(planId));
        }

        private int getSaveCount() {
            return saveCount;
        }

        private static DecompositionPlan copyPlan(DecompositionPlan plan) {
            if (plan == null) {
                return null;
            }
            DecompositionPlan copy = DecompositionPlan.builder()
                    .schemaVersion(plan.getSchemaVersion())
                    .id(plan.getId())
                    .sourceCardId(plan.getSourceCardId())
                    .epicCardId(plan.getEpicCardId())
                    .serviceId(plan.getServiceId())
                    .objectiveId(plan.getObjectiveId())
                    .teamId(plan.getTeamId())
                    .plannerGolemId(plan.getPlannerGolemId())
                    .plannerDisplayName(plan.getPlannerDisplayName())
                    .status(plan.getStatus())
                    .items(new ArrayList<>())
                    .links(new ArrayList<>())
                    .rationale(plan.getRationale())
                    .createdAt(plan.getCreatedAt())
                    .updatedAt(plan.getUpdatedAt())
                    .approvedAt(plan.getApprovedAt())
                    .approvedByActorId(plan.getApprovedByActorId())
                    .approvedByActorName(plan.getApprovedByActorName())
                    .approvalComment(plan.getApprovalComment())
                    .rejectedAt(plan.getRejectedAt())
                    .rejectedByActorId(plan.getRejectedByActorId())
                    .rejectedByActorName(plan.getRejectedByActorName())
                    .rejectionComment(plan.getRejectionComment())
                    .appliedAt(plan.getAppliedAt())
                    .appliedByActorId(plan.getAppliedByActorId())
                    .appliedByActorName(plan.getAppliedByActorName())
                    .build();
            for (DecompositionPlanItem item : plan.getItems()) {
                copy.getItems().add(copyItem(item));
            }
            for (DecompositionPlanLink link : plan.getLinks()) {
                copy.getLinks().add(DecompositionPlanLink.builder()
                        .fromClientItemId(link.getFromClientItemId())
                        .toClientItemId(link.getToClientItemId())
                        .type(link.getType())
                        .build());
            }
            return copy;
        }

        private static DecompositionPlanItem copyItem(DecompositionPlanItem item) {
            return DecompositionPlanItem.builder()
                    .clientItemId(item.getClientItemId())
                    .kind(item.getKind())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .prompt(item.getPrompt())
                    .acceptanceCriteria(
                            item.getAcceptanceCriteria() != null ? new ArrayList<>(item.getAcceptanceCriteria())
                                    : new ArrayList<>())
                    .assignment(item.getAssignment() != null
                            ? DecompositionAssignmentSpec.builder()
                                    .columnId(item.getAssignment().getColumnId())
                                    .teamId(item.getAssignment().getTeamId())
                                    .objectiveId(item.getAssignment().getObjectiveId())
                                    .assigneeGolemId(item.getAssignment().getAssigneeGolemId())
                                    .assignmentPolicy(item.getAssignment().getAssignmentPolicy())
                                    .autoAssign(item.getAssignment().isAutoAssign())
                                    .build()
                            : null)
                    .review(item.getReview() != null
                            ? DecompositionReviewSpec.builder()
                                    .reviewerGolemIds(item.getReview().getReviewerGolemIds() != null
                                            ? new ArrayList<>(item.getReview().getReviewerGolemIds())
                                            : new ArrayList<>())
                                    .reviewerTeamId(item.getReview().getReviewerTeamId())
                                    .requiredReviewCount(item.getReview().getRequiredReviewCount())
                                    .build()
                            : null)
                    .createdCardId(item.getCreatedCardId())
                    .materializedAt(item.getMaterializedAt())
                    .build();
        }
    }

    private static Board boardWithInboxColumn() {
        return Board.builder()
                .id("service-1")
                .flow(BoardFlowDefinition.builder()
                        .defaultColumnId("inbox")
                        .columns(List.of(BoardColumn.builder().id("inbox").name("Inbox").build()))
                        .build())
                .build();
    }
}
