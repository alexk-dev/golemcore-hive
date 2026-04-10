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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.DecompositionAssignmentSpec;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanLink;
import me.golemcore.hive.domain.model.DecompositionPlanLinkType;
import me.golemcore.hive.domain.model.DecompositionPlanStatus;
import me.golemcore.hive.domain.model.DecompositionReviewSpec;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.DecompositionAssignmentSpecCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanApplicationResult;
import me.golemcore.hive.workflow.application.DecompositionPlanCreatedCard;
import me.golemcore.hive.workflow.application.DecompositionPlanItemCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanLinkCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanProposalCommand;
import me.golemcore.hive.workflow.application.DecompositionReviewSpecCommand;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.DecompositionPlanningUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.DecompositionPlanRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowPolicyPort;

public class DecompositionPlanningApplicationService implements DecompositionPlanningUseCase {

    private final DecompositionPlanRepository decompositionPlanRepository;
    private final CardWorkflowUseCase cardWorkflowUseCase;
    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final ReviewWorkflowUseCase reviewWorkflowUseCase;
    private final WorkflowAuditPort workflowAuditPort;
    private final WorkflowPolicyPort workflowPolicyPort;

    public DecompositionPlanningApplicationService(
            DecompositionPlanRepository decompositionPlanRepository,
            CardWorkflowUseCase cardWorkflowUseCase,
            BoardWorkflowUseCase boardWorkflowUseCase,
            ReviewWorkflowUseCase reviewWorkflowUseCase,
            WorkflowAuditPort workflowAuditPort,
            WorkflowPolicyPort workflowPolicyPort) {
        this.decompositionPlanRepository = decompositionPlanRepository;
        this.cardWorkflowUseCase = cardWorkflowUseCase;
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.reviewWorkflowUseCase = reviewWorkflowUseCase;
        this.workflowAuditPort = workflowAuditPort;
        this.workflowPolicyPort = workflowPolicyPort;
    }

    @Override
    public List<DecompositionPlan> listPlans(String sourceCardId, String epicCardId, DecompositionPlanStatus status) {
        List<DecompositionPlan> plans = new ArrayList<>(decompositionPlanRepository.list(
                normalizeOptionalId(sourceCardId),
                normalizeOptionalId(epicCardId),
                status));
        plans.sort(
                Comparator.comparing(DecompositionPlan::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DecompositionPlan::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DecompositionPlan::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return plans;
    }

    @Override
    public Optional<DecompositionPlan> findPlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return Optional.empty();
        }
        return decompositionPlanRepository.findById(planId).map(this::normalizePlan);
    }

    @Override
    public DecompositionPlan getPlan(String planId) {
        return findPlan(planId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown decomposition plan: " + planId));
    }

    @Override
    public DecompositionPlan proposePlan(DecompositionPlanProposalCommand command, String actorId, String actorName) {
        if (command == null) {
            throw new IllegalArgumentException("Plan proposal is required");
        }
        Card sourceCard = getRequiredCard(command.sourceCardId());
        Card epicCard = normalizeOptionalId(command.epicCardId()) != null
                ? getRequiredCard(command.epicCardId())
                : null;
        if (epicCard != null && !Objects.equals(sourceCard.getServiceId(), epicCard.getServiceId())) {
            throw new IllegalArgumentException("Epic card must belong to the same service as the source card");
        }

        List<DecompositionPlanItem> items = normalizeItems(command.items());
        List<DecompositionPlanLink> links = normalizeLinks(command.links());
        validatePlanGraph(items, links);
        enforcePolicyGate(command, items);

        Instant now = Instant.now();
        DecompositionPlan plan = DecompositionPlan.builder()
                .id("dplan_" + UUID.randomUUID().toString().replace("-", ""))
                .sourceCardId(sourceCard.getId())
                .epicCardId(epicCard != null ? epicCard.getId() : null)
                .serviceId(sourceCard.getServiceId())
                .objectiveId(sourceCard.getObjectiveId())
                .teamId(sourceCard.getTeamId())
                .plannerGolemId(normalizeOptionalId(command.plannerGolemId()))
                .plannerDisplayName(actorName)
                .status(DecompositionPlanStatus.DRAFT)
                .items(items)
                .links(links)
                .rationale(command.rationale())
                .createdAt(now)
                .updatedAt(now)
                .build();
        decompositionPlanRepository.save(plan);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("decomposition_plan.created")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("DECOMPOSITION_PLAN")
                .targetId(plan.getId())
                .boardId(sourceCard.getBoardId())
                .cardId(sourceCard.getId())
                .summary("Decomposition plan created")
                .details(plan.getRationale()));
        return plan;
    }

    @Override
    public DecompositionPlan approvePlan(String planId, String actorId, String actorName, String comment) {
        DecompositionPlan plan = getPlan(planId);
        if (plan.getStatus() == DecompositionPlanStatus.APPROVED) {
            return plan;
        }
        if (plan.getStatus() != DecompositionPlanStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft plans can be approved");
        }
        Instant now = Instant.now();
        plan.setStatus(DecompositionPlanStatus.APPROVED);
        plan.setApprovedAt(now);
        plan.setApprovedByActorId(actorId);
        plan.setApprovedByActorName(actorName);
        plan.setApprovalComment(comment);
        plan.setUpdatedAt(now);
        decompositionPlanRepository.save(plan);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("decomposition_plan.approved")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("DECOMPOSITION_PLAN")
                .targetId(plan.getId())
                .summary("Decomposition plan approved")
                .details(comment));
        return plan;
    }

    @Override
    public DecompositionPlan rejectPlan(String planId, String actorId, String actorName, String comment) {
        DecompositionPlan plan = getPlan(planId);
        if (plan.getStatus() == DecompositionPlanStatus.REJECTED) {
            return plan;
        }
        if (plan.getStatus() != DecompositionPlanStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft plans can be rejected");
        }
        Instant now = Instant.now();
        plan.setStatus(DecompositionPlanStatus.REJECTED);
        plan.setRejectedAt(now);
        plan.setRejectedByActorId(actorId);
        plan.setRejectedByActorName(actorName);
        plan.setRejectionComment(comment);
        plan.setUpdatedAt(now);
        decompositionPlanRepository.save(plan);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("decomposition_plan.rejected")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("DECOMPOSITION_PLAN")
                .targetId(plan.getId())
                .summary("Decomposition plan rejected")
                .details(comment));
        return plan;
    }

    @Override
    public DecompositionPlanApplicationResult applyPlan(String planId, String actorId, String actorName) {
        DecompositionPlan plan = getPlan(planId);
        if (plan.getStatus() == DecompositionPlanStatus.APPLIED) {
            return new DecompositionPlanApplicationResult(copyPlan(plan), collectCreatedCards(plan), true);
        }
        if (plan.getStatus() != DecompositionPlanStatus.APPROVED) {
            throw new IllegalArgumentException("Plan must be approved before it can be applied");
        }

        DecompositionPlan workingPlan = copyPlan(plan);
        Card sourceCard = getRequiredCard(workingPlan.getSourceCardId());
        Board board = boardWorkflowUseCase.getBoard(sourceCard.getBoardId());
        List<DecompositionPlanItem> orderedItems = orderItems(workingPlan);
        validateApplyPlan(sourceCard, board, orderedItems);
        List<DecompositionPlanCreatedCard> createdCards = new ArrayList<>();
        Map<String, String> createdCardIdsByClientItemId = new LinkedHashMap<>();
        Instant now = Instant.now();
        String effectiveEpicCardId = workingPlan.getEpicCardId() != null ? workingPlan.getEpicCardId()
                : (sourceCard.getKind() == CardKind.EPIC ? sourceCard.getId() : null);

        for (DecompositionPlanItem item : orderedItems) {
            if (item.getCreatedCardId() != null && !item.getCreatedCardId().isBlank()) {
                createdCardIdsByClientItemId.put(item.getClientItemId(), item.getCreatedCardId());
                createdCards.add(new DecompositionPlanCreatedCard(
                        item.getClientItemId(),
                        item.getCreatedCardId(),
                        getRequiredCard(item.getCreatedCardId()).getThreadId(),
                        item.getTitle(),
                        item.getKind()));
                continue;
            }

            CardKind effectiveKind = item.getKind() != null ? item.getKind() : CardKind.TASK;
            String targetColumnId = resolveTargetColumnId(board, item);
            String parentCardId = resolveParentCardId(item.getClientItemId(), sourceCard.getId(), workingPlan,
                    createdCardIdsByClientItemId);
            List<String> dependsOnCardIds = resolveDependsOnCardIds(
                    item.getClientItemId(),
                    workingPlan,
                    createdCardIdsByClientItemId);
            CardCreateCommand command = new CardCreateCommand(
                    sourceCard.getServiceId(),
                    item.getTitle(),
                    item.getDescription(),
                    item.getPrompt(),
                    targetColumnId,
                    resolveTeamId(plan, item),
                    resolveObjectiveId(plan, item),
                    resolveAssigneeId(item),
                    resolveAssignmentPolicy(sourceCard.getAssignmentPolicy(), item),
                    resolveAutoAssign(item),
                    effectiveKind,
                    parentCardId,
                    effectiveEpicCardId,
                    null,
                    dependsOnCardIds);
            Card createdCard = cardWorkflowUseCase.createCard(command, actorId, actorName);
            if (item.getReview() != null) {
                reviewWorkflowUseCase.requestReview(
                        createdCard.getId(),
                        item.getReview().getReviewerGolemIds(),
                        item.getReview().getReviewerTeamId(),
                        item.getReview().getRequiredReviewCount(),
                        actorId,
                        actorName);
            }
            item.setCreatedCardId(createdCard.getId());
            item.setMaterializedAt(now);
            createdCardIdsByClientItemId.put(item.getClientItemId(), createdCard.getId());
            createdCards.add(new DecompositionPlanCreatedCard(
                    item.getClientItemId(),
                    createdCard.getId(),
                    createdCard.getThreadId(),
                    createdCard.getTitle(),
                    effectiveKind));
        }

        workingPlan.setStatus(DecompositionPlanStatus.APPLIED);
        workingPlan.setAppliedAt(now);
        workingPlan.setAppliedByActorId(actorId);
        workingPlan.setAppliedByActorName(actorName);
        workingPlan.setUpdatedAt(now);
        decompositionPlanRepository.save(workingPlan);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("decomposition_plan.applied")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("DECOMPOSITION_PLAN")
                .targetId(workingPlan.getId())
                .boardId(sourceCard.getBoardId())
                .cardId(sourceCard.getId())
                .summary("Decomposition plan applied")
                .details(workingPlan.getRationale()));
        return new DecompositionPlanApplicationResult(workingPlan, createdCards, false);
    }

    private void validateApplyPlan(Card sourceCard, Board board, List<DecompositionPlanItem> orderedItems) {
        for (DecompositionPlanItem item : orderedItems) {
            if (item.getCreatedCardId() != null && !item.getCreatedCardId().isBlank()) {
                Card createdCard = getRequiredCard(item.getCreatedCardId());
                if (!Objects.equals(sourceCard.getServiceId(), createdCard.getServiceId())) {
                    throw new IllegalArgumentException(
                            "Applied card must belong to the same service as the source card");
                }
                continue;
            }

            resolveTargetColumnId(board, item);
        }
    }

    private DecompositionPlan copyPlan(DecompositionPlan plan) {
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
            copy.getLinks().add(copyLink(link));
        }
        return copy;
    }

    private DecompositionPlanItem copyItem(DecompositionPlanItem item) {
        if (item == null) {
            return null;
        }
        return DecompositionPlanItem.builder()
                .clientItemId(item.getClientItemId())
                .kind(item.getKind())
                .title(item.getTitle())
                .description(item.getDescription())
                .prompt(item.getPrompt())
                .acceptanceCriteria(item.getAcceptanceCriteria() != null ? new ArrayList<>(item.getAcceptanceCriteria())
                        : new ArrayList<>())
                .assignment(copyAssignment(item.getAssignment()))
                .review(copyReview(item.getReview()))
                .createdCardId(item.getCreatedCardId())
                .materializedAt(item.getMaterializedAt())
                .build();
    }

    private DecompositionAssignmentSpec copyAssignment(DecompositionAssignmentSpec assignment) {
        if (assignment == null) {
            return null;
        }
        return DecompositionAssignmentSpec.builder()
                .columnId(assignment.getColumnId())
                .teamId(assignment.getTeamId())
                .objectiveId(assignment.getObjectiveId())
                .assigneeGolemId(assignment.getAssigneeGolemId())
                .assignmentPolicy(assignment.getAssignmentPolicy())
                .autoAssign(assignment.isAutoAssign())
                .build();
    }

    private DecompositionReviewSpec copyReview(DecompositionReviewSpec review) {
        if (review == null) {
            return null;
        }
        return DecompositionReviewSpec.builder()
                .reviewerGolemIds(review.getReviewerGolemIds() != null ? new ArrayList<>(review.getReviewerGolemIds())
                        : new ArrayList<>())
                .reviewerTeamId(review.getReviewerTeamId())
                .requiredReviewCount(review.getRequiredReviewCount())
                .build();
    }

    private DecompositionPlanLink copyLink(DecompositionPlanLink link) {
        if (link == null) {
            return null;
        }
        return DecompositionPlanLink.builder()
                .fromClientItemId(link.getFromClientItemId())
                .toClientItemId(link.getToClientItemId())
                .type(link.getType())
                .build();
    }

    private DecompositionPlan normalizePlan(DecompositionPlan plan) {
        if (plan == null) {
            return null;
        }
        if (plan.getItems() == null) {
            plan.setItems(new ArrayList<>());
        }
        if (plan.getLinks() == null) {
            plan.setLinks(new ArrayList<>());
        }
        for (DecompositionPlanItem item : plan.getItems()) {
            if (item.getAcceptanceCriteria() == null) {
                item.setAcceptanceCriteria(new ArrayList<>());
            }
            if (item.getReview() != null) {
                item.getReview().setReviewerGolemIds(normalizeIds(item.getReview().getReviewerGolemIds()));
                if (item.getReview().getRequiredReviewCount() <= 0) {
                    item.getReview().setRequiredReviewCount(1);
                }
            }
        }
        return plan;
    }

    private List<DecompositionPlanItem> normalizeItems(List<DecompositionPlanItemCommand> itemCommands) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new IllegalArgumentException("At least one plan item is required");
        }
        List<DecompositionPlanItem> items = new ArrayList<>();
        LinkedHashSet<String> clientIds = new LinkedHashSet<>();
        for (DecompositionPlanItemCommand itemCommand : itemCommands) {
            if (itemCommand == null) {
                throw new IllegalArgumentException("Plan item is required");
            }
            if (itemCommand.clientItemId() == null || itemCommand.clientItemId().isBlank()) {
                throw new IllegalArgumentException("Plan item clientItemId is required");
            }
            if (!clientIds.add(itemCommand.clientItemId())) {
                throw new IllegalArgumentException("Duplicate plan item clientItemId: " + itemCommand.clientItemId());
            }
            if (itemCommand.title() == null || itemCommand.title().isBlank()) {
                throw new IllegalArgumentException("Plan item title is required");
            }
            if (itemCommand.prompt() == null || itemCommand.prompt().isBlank()) {
                throw new IllegalArgumentException("Plan item prompt is required");
            }
            if (itemCommand.kind() == CardKind.REVIEW) {
                throw new IllegalArgumentException(
                        "Use item.review to request review gates instead of materializing review cards");
            }
            DecompositionPlanItem item = DecompositionPlanItem.builder()
                    .clientItemId(itemCommand.clientItemId())
                    .kind(itemCommand.kind())
                    .title(itemCommand.title())
                    .description(itemCommand.description())
                    .prompt(itemCommand.prompt())
                    .acceptanceCriteria(itemCommand.acceptanceCriteria() != null
                            ? new ArrayList<>(itemCommand.acceptanceCriteria())
                            : new ArrayList<>())
                    .assignment(normalizeAssignment(itemCommand.assignment()))
                    .review(normalizeReview(itemCommand.review()))
                    .build();
            items.add(item);
        }
        return items;
    }

    private List<DecompositionPlanLink> normalizeLinks(List<DecompositionPlanLinkCommand> linkCommands) {
        if (linkCommands == null) {
            return new ArrayList<>();
        }
        List<DecompositionPlanLink> links = new ArrayList<>();
        for (DecompositionPlanLinkCommand linkCommand : linkCommands) {
            if (linkCommand == null) {
                throw new IllegalArgumentException("Plan link is required");
            }
            if (linkCommand.fromClientItemId() == null || linkCommand.fromClientItemId().isBlank()) {
                throw new IllegalArgumentException("Plan link fromClientItemId is required");
            }
            if (linkCommand.toClientItemId() == null || linkCommand.toClientItemId().isBlank()) {
                throw new IllegalArgumentException("Plan link toClientItemId is required");
            }
            if (linkCommand.type() == null) {
                throw new IllegalArgumentException("Plan link type is required");
            }
            links.add(DecompositionPlanLink.builder()
                    .fromClientItemId(linkCommand.fromClientItemId())
                    .toClientItemId(linkCommand.toClientItemId())
                    .type(linkCommand.type())
                    .build());
        }
        return links;
    }

    private DecompositionAssignmentSpec normalizeAssignment(DecompositionAssignmentSpecCommand command) {
        if (command == null) {
            return null;
        }
        return DecompositionAssignmentSpec.builder()
                .columnId(normalizeOptionalId(command.columnId()))
                .teamId(normalizeOptionalId(command.teamId()))
                .objectiveId(normalizeOptionalId(command.objectiveId()))
                .assigneeGolemId(normalizeOptionalId(command.assigneeGolemId()))
                .assignmentPolicy(command.assignmentPolicy())
                .autoAssign(command.autoAssign())
                .build();
    }

    private DecompositionReviewSpec normalizeReview(DecompositionReviewSpecCommand command) {
        if (command == null) {
            return null;
        }
        List<String> reviewerGolemIds = normalizeIds(command.reviewerGolemIds());
        String reviewerTeamId = normalizeOptionalId(command.reviewerTeamId());
        if (reviewerGolemIds.isEmpty() && reviewerTeamId == null) {
            return null;
        }
        int requiredReviewCount = command.requiredReviewCount() != null && command.requiredReviewCount() > 0
                ? command.requiredReviewCount()
                : 1;
        if (reviewerTeamId == null && reviewerGolemIds.size() < requiredReviewCount) {
            throw new IllegalArgumentException("Not enough reviewers for requiredReviewCount");
        }
        return DecompositionReviewSpec.builder()
                .reviewerGolemIds(reviewerGolemIds)
                .reviewerTeamId(reviewerTeamId)
                .requiredReviewCount(requiredReviewCount)
                .build();
    }

    private List<String> normalizeIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeOptionalId(value);
            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void validatePlanGraph(List<DecompositionPlanItem> items, List<DecompositionPlanLink> links) {
        Map<String, DecompositionPlanItem> itemsById = new LinkedHashMap<>();
        for (DecompositionPlanItem item : items) {
            itemsById.put(item.getClientItemId(), item);
        }
        for (DecompositionPlanLink link : links) {
            if (!itemsById.containsKey(link.getFromClientItemId())) {
                throw new IllegalArgumentException(
                        "Unknown fromClientItemId in plan link: " + link.getFromClientItemId());
            }
            if (!itemsById.containsKey(link.getToClientItemId())) {
                throw new IllegalArgumentException("Unknown toClientItemId in plan link: " + link.getToClientItemId());
            }
        }
        for (DecompositionPlanLink link : links) {
            if (link.getType() == DecompositionPlanLinkType.CHILD_OF
                    && Objects.equals(link.getFromClientItemId(), link.getToClientItemId())) {
                throw new IllegalArgumentException("A plan item cannot be a child of itself");
            }
        }
        orderItems(items, links);
    }

    private void enforcePolicyGate(DecompositionPlanProposalCommand command, List<DecompositionPlanItem> items) {
        String plannerGolemId = normalizeOptionalId(command.plannerGolemId());
        if (plannerGolemId == null) {
            return;
        }

        Optional<PolicyGroupSpec.PolicySdlcConfig> policy = workflowPolicyPort.findSdlcPolicyForGolem(plannerGolemId);
        if (policy.isEmpty()) {
            return;
        }

        PolicyGroupSpec.PolicySdlcConfig sdlc = policy.get();

        if (sdlc.getMaxDecompositionFanOut() != null && sdlc.getMaxDecompositionFanOut() > 0
                && items.size() > sdlc.getMaxDecompositionFanOut()) {
            throw new IllegalArgumentException("Plan fan-out exceeds policy maxDecompositionFanOut");
        }

        if (sdlc.getAllowedCardKinds() != null && !sdlc.getAllowedCardKinds().isEmpty()) {
            for (DecompositionPlanItem item : items) {
                CardKind effectiveKind = item.getKind() != null ? item.getKind() : CardKind.TASK;
                if (!isAllowedCardKind(sdlc.getAllowedCardKinds(), effectiveKind)) {
                    throw new IllegalArgumentException("Plan item kind is not allowed by policy: "
                            + effectiveKind.name());
                }
            }
        }

        if (Boolean.FALSE.equals(sdlc.getReviewerAssignmentEnabled())) {
            for (DecompositionPlanItem item : items) {
                if (item.getReview() != null) {
                    throw new IllegalArgumentException("Reviewer assignment is disabled by policy");
                }
            }
        }

        if (Boolean.TRUE.equals(sdlc.getRequireReviewerSeparationOfDuties())) {
            for (DecompositionPlanItem item : items) {
                if (item.getReview() == null) {
                    continue;
                }
                if (item.getReview().getReviewerGolemIds().contains(plannerGolemId)) {
                    throw new IllegalArgumentException("Planner golem cannot self-review its own decomposition plan");
                }
            }
        }
    }

    private boolean isAllowedCardKind(List<String> allowedCardKinds, CardKind kind) {
        String normalizedKind = kind.name().toUpperCase(Locale.ROOT);
        for (String allowedCardKind : allowedCardKinds) {
            if (allowedCardKind != null && normalizedKind.equals(allowedCardKind.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<DecompositionPlanItem> orderItems(DecompositionPlan plan) {
        return orderItems(plan.getItems(), plan.getLinks());
    }

    private List<DecompositionPlanItem> orderItems(List<DecompositionPlanItem> items,
            List<DecompositionPlanLink> links) {
        Map<String, DecompositionPlanItem> itemsById = new LinkedHashMap<>();
        for (DecompositionPlanItem item : items) {
            itemsById.put(item.getClientItemId(), item);
        }

        Map<String, Integer> incomingEdges = new HashMap<>();
        Map<String, List<String>> outgoingEdges = new HashMap<>();
        for (DecompositionPlanItem item : items) {
            incomingEdges.put(item.getClientItemId(), 0);
            outgoingEdges.put(item.getClientItemId(), new ArrayList<>());
        }
        for (DecompositionPlanLink link : links) {
            String prerequisiteId = link.getToClientItemId();
            String dependentId = link.getFromClientItemId();
            outgoingEdges.get(prerequisiteId).add(dependentId);
            incomingEdges.put(dependentId, incomingEdges.get(dependentId) + 1);
        }

        List<DecompositionPlanItem> ordered = new ArrayList<>();
        LinkedList<String> ready = new LinkedList<>();
        for (DecompositionPlanItem item : items) {
            if (incomingEdges.get(item.getClientItemId()) == 0) {
                ready.add(item.getClientItemId());
            }
        }

        while (!ready.isEmpty()) {
            String itemId = ready.removeFirst();
            ordered.add(itemsById.get(itemId));
            for (String dependentId : outgoingEdges.get(itemId)) {
                incomingEdges.put(dependentId, incomingEdges.get(dependentId) - 1);
                if (incomingEdges.get(dependentId) == 0) {
                    ready.add(dependentId);
                }
            }
        }

        if (ordered.size() != items.size()) {
            throw new IllegalArgumentException("Plan item dependencies contain a cycle");
        }
        return ordered;
    }

    private List<DecompositionPlanCreatedCard> collectCreatedCards(DecompositionPlan plan) {
        List<DecompositionPlanCreatedCard> createdCards = new ArrayList<>();
        for (DecompositionPlanItem item : plan.getItems()) {
            if (item.getCreatedCardId() != null && !item.getCreatedCardId().isBlank()) {
                Card createdCard = getRequiredCard(item.getCreatedCardId());
                createdCards.add(new DecompositionPlanCreatedCard(
                        item.getClientItemId(),
                        createdCard.getId(),
                        createdCard.getThreadId(),
                        createdCard.getTitle(),
                        item.getKind()));
            }
        }
        return createdCards;
    }

    private Card getRequiredCard(String cardId) {
        return cardWorkflowUseCase.findCard(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown card: " + cardId));
    }

    private String resolveColumnId(DecompositionPlanItem item) {
        if (item.getAssignment() == null || item.getAssignment().getColumnId() == null
                || item.getAssignment().getColumnId().isBlank()) {
            return null;
        }
        return item.getAssignment().getColumnId();
    }

    private String resolveTargetColumnId(Board board, DecompositionPlanItem item) {
        String targetColumnId = resolveColumnId(item);
        if (targetColumnId == null) {
            targetColumnId = board.getFlow() != null ? board.getFlow().getDefaultColumnId() : null;
        }
        String resolvedTargetColumnId = targetColumnId;
        if (targetColumnId == null || targetColumnId.isBlank() || board.getFlow() == null
                || board.getFlow().getColumns() == null
                || board.getFlow().getColumns().stream()
                        .noneMatch(column -> resolvedTargetColumnId.equals(column.getId()))) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }
        return targetColumnId;
    }

    private String resolveTeamId(DecompositionPlan plan, DecompositionPlanItem item) {
        if (item.getAssignment() != null && item.getAssignment().getTeamId() != null
                && !item.getAssignment().getTeamId().isBlank()) {
            return item.getAssignment().getTeamId();
        }
        return plan.getTeamId();
    }

    private String resolveObjectiveId(DecompositionPlan plan, DecompositionPlanItem item) {
        if (item.getAssignment() != null && item.getAssignment().getObjectiveId() != null
                && !item.getAssignment().getObjectiveId().isBlank()) {
            return item.getAssignment().getObjectiveId();
        }
        return plan.getObjectiveId();
    }

    private String resolveAssigneeId(DecompositionPlanItem item) {
        if (item.getAssignment() == null) {
            return null;
        }
        return normalizeOptionalId(item.getAssignment().getAssigneeGolemId());
    }

    private CardAssignmentPolicy resolveAssignmentPolicy(CardAssignmentPolicy sourcePolicy,
            DecompositionPlanItem item) {
        if (item.getAssignment() != null && item.getAssignment().getAssignmentPolicy() != null) {
            return item.getAssignment().getAssignmentPolicy();
        }
        return sourcePolicy;
    }

    private String resolveParentCardId(
            String clientItemId,
            String defaultParentCardId,
            DecompositionPlan plan,
            Map<String, String> createdCardIdsByClientItemId) {
        for (DecompositionPlanLink link : plan.getLinks()) {
            if (link.getType() != DecompositionPlanLinkType.CHILD_OF) {
                continue;
            }
            if (!clientItemId.equals(link.getFromClientItemId())) {
                continue;
            }
            String parentCardId = createdCardIdsByClientItemId.get(link.getToClientItemId());
            if (parentCardId != null && !parentCardId.isBlank()) {
                return parentCardId;
            }
        }
        return defaultParentCardId;
    }

    private List<String> resolveDependsOnCardIds(
            String clientItemId,
            DecompositionPlan plan,
            Map<String, String> createdCardIdsByClientItemId) {
        List<String> dependsOnCardIds = new ArrayList<>();
        for (DecompositionPlanLink link : plan.getLinks()) {
            if (!clientItemId.equals(link.getFromClientItemId())) {
                continue;
            }
            if (link.getType() != DecompositionPlanLinkType.DEPENDS_ON
                    && link.getType() != DecompositionPlanLinkType.REVIEWS) {
                continue;
            }
            String prerequisiteCardId = createdCardIdsByClientItemId.get(link.getToClientItemId());
            if (prerequisiteCardId != null && !prerequisiteCardId.isBlank()) {
                dependsOnCardIds.add(prerequisiteCardId);
            }
        }
        return dependsOnCardIds;
    }

    private boolean resolveAutoAssign(DecompositionPlanItem item) {
        return item.getAssignment() != null && item.getAssignment().isAutoAssign();
    }

    private String normalizeOptionalId(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
