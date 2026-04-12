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
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
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
                    .filter(card -> canAccessCard(actor, card))
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
            requireCanAccessCard(actor, card);
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
            Card relatedCard = requireRelatedCardAccess(actor, request.parentCardId(), request.reviewOfCardId());
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
                    request.dependsOnCardIds()), actor.getSubjectId(), actor.getName());
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
            requireCanAccessCard(actor, existingCard);
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

    private Card requireRelatedCardAccess(AuthenticatedActor actor, String parentCardId, String reviewOfCardId) {
        String relatedCardId = firstNonBlank(parentCardId, reviewOfCardId);
        if (relatedCardId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Golem-created cards must reference an accessible parent or review target card");
        }
        Card relatedCard = cardWorkflowUseCase.getCard(relatedCardId);
        requireCanAccessCard(actor, relatedCard);
        return relatedCard;
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
        requireCanAccessCard(actor, cardWorkflowUseCase.getCard(thread.getCardId()));
    }

    private void requireCanAccessCard(AuthenticatedActor actor, Card card) {
        if (!canAccessCard(actor, card)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Card access denied");
        }
    }

    private boolean canAccessCard(AuthenticatedActor actor, Card card) {
        if (actor == null || card == null) {
            return false;
        }
        if (actor.getSubjectId().equals(card.getAssigneeGolemId())
                || (card.getReviewerGolemIds() != null && card.getReviewerGolemIds().contains(actor.getSubjectId()))) {
            return true;
        }
        String relatedCardId = firstNonBlank(card.getParentCardId(), card.getReviewOfCardId());
        if (relatedCardId != null) {
            try {
                return canAccessCard(actor, cardWorkflowUseCase.getCard(relatedCardId));
            } catch (IllegalArgumentException exception) { // NOSONAR
                return false;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
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
