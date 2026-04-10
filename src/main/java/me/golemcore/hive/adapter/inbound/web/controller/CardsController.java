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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CreateCardRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardDetailResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.MoveCardRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.RequestReviewRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.UpdateCardAssigneeRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.UpdateCardRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardControlStateSnapshot;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.CardQuery;
import me.golemcore.hive.workflow.application.CardUpdateCommand;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardsController extends BoardMappingSupport {

    private final CardWorkflowUseCase cardWorkflowUseCase;
    private final ExecutionOperationsUseCase executionOperationsUseCase;
    private final ReviewWorkflowUseCase reviewWorkflowUseCase;

    @GetMapping
    public Mono<ResponseEntity<List<CardSummaryResponse>>> listCards(
            Principal principal,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String boardId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String parentCardId,
            @RequestParam(required = false) String epicCardId,
            @RequestParam(required = false) String reviewOfCardId,
            @RequestParam(required = false) String objectiveId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            String resolvedServiceId = null;
            if ((serviceId != null && !serviceId.isBlank()) || (boardId != null && !boardId.isBlank())) {
                resolvedServiceId = resolveServiceId(serviceId, boardId);
            }
            List<Card> cards = cardWorkflowUseCase.listCards(new CardQuery(
                    resolvedServiceId,
                    includeArchived,
                    parseCardKind(kind),
                    parentCardId,
                    epicCardId,
                    reviewOfCardId,
                    objectiveId));
            Map<String, CardControlStateSnapshot> controlStates = executionOperationsUseCase
                    .listActiveCardControlStates(cards);
            List<CardSummaryResponse> response = cards.stream()
                    .map(card -> toCardSummaryResponse(card, controlStates.get(card.getId())))
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<CardDetailResponse>> createCard(
            Principal principal,
            @Valid @RequestBody CreateCardRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Card card = cardWorkflowUseCase.createCard(new CardCreateCommand(
                    resolveServiceId(request.serviceId(), request.boardId()),
                    request.title(),
                    request.description(),
                    request.prompt(),
                    request.columnId(),
                    request.teamId(),
                    request.objectiveId(),
                    request.assigneeGolemId(),
                    parseAssignmentPolicy(request.assignmentPolicy()),
                    request.autoAssign(),
                    parseCardKind(request.kind()),
                    request.parentCardId(),
                    request.epicCardId(),
                    request.reviewOfCardId(),
                    request.dependsOnCardIds()), actor.getSubjectId(), actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{cardId}")
    public Mono<ResponseEntity<CardDetailResponse>> getCard(Principal principal, @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Card card = cardWorkflowUseCase.getCard(cardId);
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{cardId}")
    public Mono<ResponseEntity<CardDetailResponse>> updateCard(
            Principal principal,
            @PathVariable String cardId,
            @RequestBody UpdateCardRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Card card = cardWorkflowUseCase.updateCard(cardId, new CardUpdateCommand(
                    request != null ? request.title() : null,
                    request != null ? request.description() : null,
                    request != null ? request.prompt() : null,
                    request != null ? request.teamId() : null,
                    request != null ? request.objectiveId() : null,
                    request != null ? parseAssignmentPolicy(request.assignmentPolicy()) : null,
                    request != null ? parseCardKind(request.kind()) : null,
                    request != null ? request.parentCardId() : null,
                    request != null ? request.epicCardId() : null,
                    request != null ? request.reviewOfCardId() : null,
                    request != null ? request.dependsOnCardIds() : null));
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{cardId}:request-review")
    public Mono<ResponseEntity<CardDetailResponse>> requestReview(
            Principal principal,
            @PathVariable String cardId,
            @Valid @RequestBody RequestReviewRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Card card = reviewWorkflowUseCase.requestReview(
                    cardId,
                    request != null ? request.reviewerGolemIds() : null,
                    request != null ? request.reviewerTeamId() : null,
                    request != null ? request.requiredReviewCount() : null,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{cardId}:move")
    public Mono<ResponseEntity<CardDetailResponse>> moveCard(
            Principal principal,
            @PathVariable String cardId,
            @Valid @RequestBody MoveCardRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Card existingCard = cardWorkflowUseCase.getCard(cardId);
            boolean shouldAutoDispatchPrompt = shouldAutoDispatchPromptOnMove(existingCard, request.targetColumnId());
            if (shouldAutoDispatchPrompt
                    && (existingCard.getAssigneeGolemId() == null || existingCard.getAssigneeGolemId().isBlank())) {
                throw new IllegalArgumentException("Card must be assigned before the first move to in_progress");
            }
            Card card = cardWorkflowUseCase.moveCard(
                    cardId,
                    request.targetColumnId(),
                    request.targetIndex(),
                    CardTransitionOrigin.MANUAL,
                    actor.getSubjectId(),
                    actor.getName(),
                    request.summary());
            if (shouldAutoDispatchPrompt) {
                executionOperationsUseCase.createCommand(
                        card.getThreadId(),
                        card.getPrompt(),
                        null,
                        0,
                        null,
                        actor.getSubjectId(),
                        actor.getName());
            }
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{cardId}:transition")
    public Mono<ResponseEntity<CardDetailResponse>> transitionCard(
            Principal principal,
            @PathVariable String cardId,
            @Valid @RequestBody MoveCardRequest request) {
        return moveCard(principal, cardId, request);
    }

    @PostMapping("/{cardId}:assign")
    public Mono<ResponseEntity<CardDetailResponse>> assignCard(
            Principal principal,
            @PathVariable String cardId,
            @RequestBody UpdateCardAssigneeRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Card card = cardWorkflowUseCase.assignCard(cardId, request != null ? request.assigneeGolemId() : null);
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{cardId}:archive")
    public Mono<ResponseEntity<CardDetailResponse>> archiveCard(Principal principal, @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Card card = cardWorkflowUseCase.archiveCard(cardId);
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private CardControlStateSnapshot findControlState(Card card) {
        return executionOperationsUseCase.listActiveCardControlStates(List.of(card)).get(card.getId());
    }

    private boolean shouldAutoDispatchPromptOnMove(Card card, String targetColumnId) {
        if (card == null || !"in_progress".equals(targetColumnId) || "in_progress".equals(card.getColumnId())) {
            return false;
        }
        return card.getTransitionEvents().stream()
                .noneMatch(event -> "in_progress".equals(event.getToColumnId()));
    }
}
