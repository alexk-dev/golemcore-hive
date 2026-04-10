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

package me.golemcore.hive.adapter.inbound.web.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.CreateDecompositionPlanRequest;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionAssignmentResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanApplicationResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanCreatedCardResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanItemResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanLinkResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionReviewResponse;
import me.golemcore.hive.adapter.inbound.web.dto.decomposition.PlanDecisionRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.DecompositionAssignmentSpec;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanLink;
import me.golemcore.hive.domain.model.DecompositionPlanStatus;
import me.golemcore.hive.domain.model.DecompositionReviewSpec;
import me.golemcore.hive.workflow.application.DecompositionAssignmentSpecCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanApplicationResult;
import me.golemcore.hive.workflow.application.DecompositionPlanCreatedCard;
import me.golemcore.hive.workflow.application.DecompositionPlanItemCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanLinkCommand;
import me.golemcore.hive.workflow.application.DecompositionPlanProposalCommand;
import me.golemcore.hive.workflow.application.DecompositionReviewSpecCommand;
import me.golemcore.hive.workflow.application.port.in.DecompositionPlanningUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DecompositionPlansController {

    private final DecompositionPlanningUseCase decompositionPlanningUseCase;

    @GetMapping("/cards/{cardId}/decomposition-plans")
    public Mono<ResponseEntity<List<DecompositionPlanResponse>>> listPlansForCard(
            Principal principal,
            @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<DecompositionPlanResponse> response = decompositionPlanningUseCase.listPlans(cardId, null, null)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/decomposition-plans")
    public Mono<ResponseEntity<List<DecompositionPlanResponse>>> listPlans(
            Principal principal,
            @RequestParam(required = false) String sourceCardId,
            @RequestParam(required = false) String epicCardId,
            @RequestParam(required = false) String status) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            DecompositionPlanStatus resolvedStatus = status != null && !status.isBlank()
                    ? DecompositionPlanStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT))
                    : null;
            List<DecompositionPlanResponse> response = decompositionPlanningUseCase
                    .listPlans(sourceCardId, epicCardId, resolvedStatus)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/cards/{cardId}/decomposition-plans")
    public Mono<ResponseEntity<DecompositionPlanResponse>> createPlan(
            Principal principal,
            @PathVariable String cardId,
            @Valid @RequestBody CreateDecompositionPlanRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            DecompositionPlan plan = decompositionPlanningUseCase.proposePlan(
                    new DecompositionPlanProposalCommand(
                            cardId,
                            request.epicCardId(),
                            request.plannerGolemId(),
                            request.rationale(),
                            request.items() != null ? request.items().stream().map(this::toItemCommand).toList()
                                    : List.of(),
                            request.links() != null ? request.links().stream().map(this::toLinkCommand).toList()
                                    : List.of()),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(plan));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/decomposition-plans/{planId}")
    public Mono<ResponseEntity<DecompositionPlanResponse>> getPlan(Principal principal, @PathVariable String planId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toResponse(decompositionPlanningUseCase.getPlan(planId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/decomposition-plans/{planId}:approve")
    public Mono<ResponseEntity<DecompositionPlanResponse>> approvePlan(
            Principal principal,
            @PathVariable String planId,
            @RequestBody(required = false) PlanDecisionRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            DecompositionPlan plan = decompositionPlanningUseCase.approvePlan(
                    planId,
                    actor.getSubjectId(),
                    actor.getName(),
                    request != null ? request.comment() : null);
            return ResponseEntity.ok(toResponse(plan));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/decomposition-plans/{planId}:reject")
    public Mono<ResponseEntity<DecompositionPlanResponse>> rejectPlan(
            Principal principal,
            @PathVariable String planId,
            @RequestBody(required = false) PlanDecisionRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            DecompositionPlan plan = decompositionPlanningUseCase.rejectPlan(
                    planId,
                    actor.getSubjectId(),
                    actor.getName(),
                    request != null ? request.comment() : null);
            return ResponseEntity.ok(toResponse(plan));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/decomposition-plans/{planId}:apply")
    public Mono<ResponseEntity<DecompositionPlanApplicationResponse>> applyPlan(
            Principal principal,
            @PathVariable String planId) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            DecompositionPlanApplicationResult result = decompositionPlanningUseCase.applyPlan(
                    planId,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(new DecompositionPlanApplicationResponse(
                    toResponse(result.plan()),
                    result.createdCards().stream().map(this::toCreatedCardResponse).toList(),
                    result.alreadyApplied()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private DecompositionPlanItemCommand toItemCommand(
            me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanItemRequest request) {
        return new DecompositionPlanItemCommand(
                request.clientItemId(),
                request.kind(),
                request.title(),
                request.description(),
                request.prompt(),
                request.acceptanceCriteria(),
                request.assignment() != null ? new DecompositionAssignmentSpecCommand(
                        request.assignment().columnId(),
                        request.assignment().teamId(),
                        request.assignment().objectiveId(),
                        request.assignment().assigneeGolemId(),
                        request.assignment().assignmentPolicy(),
                        request.assignment().autoAssign()) : null,
                request.review() != null ? new DecompositionReviewSpecCommand(
                        request.review().reviewerGolemIds(),
                        request.review().reviewerTeamId(),
                        request.review().requiredReviewCount()) : null);
    }

    private DecompositionPlanLinkCommand toLinkCommand(
            me.golemcore.hive.adapter.inbound.web.dto.decomposition.DecompositionPlanLinkRequest request) {
        return new DecompositionPlanLinkCommand(request.fromClientItemId(), request.toClientItemId(), request.type());
    }

    private DecompositionPlanResponse toResponse(DecompositionPlan plan) {
        return new DecompositionPlanResponse(
                plan.getId(),
                plan.getSourceCardId(),
                plan.getEpicCardId(),
                plan.getServiceId(),
                plan.getObjectiveId(),
                plan.getTeamId(),
                plan.getPlannerGolemId(),
                plan.getPlannerDisplayName(),
                plan.getStatus() != null ? plan.getStatus().name() : null,
                plan.getRationale(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getApprovedAt(),
                plan.getApprovedByActorId(),
                plan.getApprovedByActorName(),
                plan.getApprovalComment(),
                plan.getRejectedAt(),
                plan.getRejectedByActorId(),
                plan.getRejectedByActorName(),
                plan.getRejectionComment(),
                plan.getAppliedAt(),
                plan.getAppliedByActorId(),
                plan.getAppliedByActorName(),
                plan.getItems().stream().map(this::toItemResponse).toList(),
                plan.getLinks().stream().map(this::toLinkResponse).toList());
    }

    private DecompositionPlanItemResponse toItemResponse(DecompositionPlanItem item) {
        return new DecompositionPlanItemResponse(
                item.getClientItemId(),
                item.getKind() != null ? item.getKind().name() : null,
                item.getTitle(),
                item.getDescription(),
                item.getPrompt(),
                item.getAcceptanceCriteria(),
                toAssignmentResponse(item.getAssignment()),
                toReviewResponse(item.getReview()),
                item.getCreatedCardId(),
                item.getMaterializedAt());
    }

    private DecompositionAssignmentResponse toAssignmentResponse(DecompositionAssignmentSpec assignment) {
        if (assignment == null) {
            return null;
        }
        return new DecompositionAssignmentResponse(
                assignment.getColumnId(),
                assignment.getTeamId(),
                assignment.getObjectiveId(),
                assignment.getAssigneeGolemId(),
                assignment.getAssignmentPolicy(),
                assignment.isAutoAssign());
    }

    private DecompositionReviewResponse toReviewResponse(DecompositionReviewSpec review) {
        if (review == null) {
            return null;
        }
        return new DecompositionReviewResponse(
                review.getReviewerGolemIds(),
                review.getReviewerTeamId(),
                review.getRequiredReviewCount());
    }

    private DecompositionPlanLinkResponse toLinkResponse(DecompositionPlanLink link) {
        return new DecompositionPlanLinkResponse(
                link.getFromClientItemId(),
                link.getToClientItemId(),
                link.getType() != null ? link.getType().name() : null);
    }

    private DecompositionPlanCreatedCardResponse toCreatedCardResponse(DecompositionPlanCreatedCard createdCard) {
        return new DecompositionPlanCreatedCardResponse(
                createdCard.clientItemId(),
                createdCard.cardId(),
                createdCard.threadId(),
                createdCard.title(),
                createdCard.kind() != null ? createdCard.kind().name() : null);
    }
}
