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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardDetailResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CreateCardRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.RequestReviewRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.PostThreadMessageRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.ThreadMessageResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardControlStateSnapshot;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.CardQuery;
import me.golemcore.hive.workflow.application.WorkflowActor;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import me.golemcore.hive.workflow.application.service.GolemCardAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/golems/{golemId}/sdlc")
@RequiredArgsConstructor
public class GolemSdlcController extends BoardMappingSupport {

    private final CardWorkflowUseCase cardWorkflowUseCase;
    private final ExecutionOperationsUseCase executionOperationsUseCase;
    private final ReviewWorkflowUseCase reviewWorkflowUseCase;
    private final ThreadWorkflowUseCase threadWorkflowUseCase;
    private final GolemCardAccessPolicy golemCardAccessPolicy;

    @GetMapping("/cards")
    public Mono<ResponseEntity<List<CardSummaryResponse>>> listCards(
            Principal principal,
            @PathVariable String golemId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String boardId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String parentCardId,
            @RequestParam(required = false) String epicCardId,
            @RequestParam(required = false) String reviewOfCardId,
            @RequestParam(required = false) String objectiveId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requireGolemScope(
                    principal,
                    golemId,
                    GolemScope.SDLC_READ.value());
            String resolvedServiceId = resolveOptionalServiceId(serviceId, boardId);
            List<Card> cards = cardWorkflowUseCase.listCards(new CardQuery(
                    resolvedServiceId,
                    includeArchived,
                    parseCardKind(kind),
                    parentCardId,
                    epicCardId,
                    reviewOfCardId,
                    objectiveId)).stream()
                    .filter(card -> golemCardAccessPolicy.canAccessCard(actor.getSubjectId(), card))
                    .toList();
            Map<String, CardControlStateSnapshot> controlStates = executionOperationsUseCase
                    .listActiveCardControlStates(cards);
            List<CardSummaryResponse> response = cards.stream()
                    .map(card -> toCardSummaryResponse(card, controlStates.get(card.getId())))
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/cards/{cardId}")
    public Mono<ResponseEntity<CardDetailResponse>> getCard(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requireGolemScope(
                    principal,
                    golemId,
                    GolemScope.SDLC_READ.value());
            Card card = cardWorkflowUseCase.getCard(cardId);
            golemCardAccessPolicy.requireCanAccessCard(actor.getSubjectId(), card);
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/cards")
    public Mono<ResponseEntity<CardDetailResponse>> createCard(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody CreateCardRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requireGolemScope(
                    principal,
                    golemId,
                    GolemScope.SDLC_WRITE.value());
            Card relatedCard = requireRelatedCardAccess(actor, request);
            Card card = cardWorkflowUseCase.createCard(new CardCreateCommand(
                    resolveCreateServiceId(request, relatedCard),
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
                    request.dependsOnCardIds()), WorkflowActor.golem(actor.getSubjectId(), actor.getName()));
            return ResponseEntity.status(HttpStatus.CREATED).body(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/cards/{cardId}:request-review")
    public Mono<ResponseEntity<CardDetailResponse>> requestReview(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String cardId,
            @Valid @RequestBody RequestReviewRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requireGolemScope(
                    principal,
                    golemId,
                    GolemScope.SDLC_WRITE.value());
            Card existingCard = cardWorkflowUseCase.getCard(cardId);
            golemCardAccessPolicy.requireCanAccessCard(actor.getSubjectId(), existingCard);
            Card card = reviewWorkflowUseCase.requestReview(
                    cardId,
                    request != null ? request.reviewerGolemIds() : null,
                    request != null ? request.reviewerTeamId() : null,
                    request != null ? request.requiredReviewCount() : null,
                    WorkflowActor.golem(actor.getSubjectId(), actor.getName()));
            return ResponseEntity.ok(toCardDetailResponse(card, findControlState(card)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/threads/{threadId}/messages")
    public Mono<ResponseEntity<ThreadMessageResponse>> postThreadMessage(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String threadId,
            @Valid @RequestBody PostThreadMessageRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requireGolemScope(
                    principal,
                    golemId,
                    GolemScope.SDLC_WRITE.value());
            ThreadRecord thread = threadWorkflowUseCase.getThread(threadId);
            requireThreadCardAccess(actor, thread);
            ThreadMessage message = threadWorkflowUseCase.appendMessage(
                    thread,
                    null,
                    null,
                    null,
                    ThreadMessageType.NOTE,
                    ThreadParticipantType.GOLEM,
                    actor.getSubjectId(),
                    actor.getName(),
                    request.body(),
                    Instant.now());
            return ResponseEntity.ok(toThreadMessageResponse(message));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveOptionalServiceId(String serviceId, String boardId) {
        if ((serviceId == null || serviceId.isBlank()) && (boardId == null || boardId.isBlank())) {
            return null;
        }
        return resolveServiceId(serviceId, boardId);
    }

    private Card requireRelatedCardAccess(AuthenticatedActor actor, CreateCardRequest request) {
        Card relatedCard = null;
        if (hasText(request.parentCardId())) {
            relatedCard = requireRelatedCardAccess(actor, request.parentCardId());
        }
        if (hasText(request.reviewOfCardId())) {
            Card reviewTargetCard = requireRelatedCardAccess(actor, request.reviewOfCardId());
            if (relatedCard == null) {
                relatedCard = reviewTargetCard;
            }
        }
        requireRelatedCardAccess(actor, request.epicCardId());
        if (request.dependsOnCardIds() != null) {
            for (String dependsOnCardId : request.dependsOnCardIds()) {
                requireRelatedCardAccess(actor, dependsOnCardId);
            }
        }
        if (relatedCard == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Golem-created cards must reference an accessible parent or review target card");
        }
        return relatedCard;
    }

    private Card requireRelatedCardAccess(AuthenticatedActor actor, String cardId) {
        if (!hasText(cardId)) {
            return null;
        }
        Card card = cardWorkflowUseCase.getCard(cardId);
        golemCardAccessPolicy.requireCanAccessCard(actor.getSubjectId(), card);
        return card;
    }

    private String resolveCreateServiceId(CreateCardRequest request, Card relatedCard) {
        if ((request.serviceId() != null && !request.serviceId().isBlank())
                || (request.boardId() != null && !request.boardId().isBlank())) {
            return resolveServiceId(request.serviceId(), request.boardId());
        }
        return relatedCard.getServiceId();
    }

    private void requireThreadCardAccess(AuthenticatedActor actor, ThreadRecord thread) {
        if (thread == null || thread.getCardId() == null || thread.getCardId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Thread access denied");
        }
        golemCardAccessPolicy.requireCanAccessCard(actor.getSubjectId(),
                cardWorkflowUseCase.getCard(thread.getCardId()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CardControlStateSnapshot findControlState(Card card) {
        return executionOperationsUseCase.listActiveCardControlStates(List.of(card)).get(card.getId());
    }

    private ThreadMessageResponse toThreadMessageResponse(ThreadMessage message) {
        return new ThreadMessageResponse(
                message.getId(),
                message.getThreadId(),
                message.getCardId(),
                message.getCommandId(),
                message.getRunId(),
                message.getSignalId(),
                message.getType().name(),
                message.getParticipantType().name(),
                message.getAuthorId(),
                message.getAuthorName(),
                message.getBody(),
                message.getCreatedAt());
    }
}
