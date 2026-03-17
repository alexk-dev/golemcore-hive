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
import me.golemcore.hive.adapter.inbound.web.dto.threads.CardThreadResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.PostThreadMessageRequest;
import me.golemcore.hive.adapter.inbound.web.dto.threads.ThreadMessageResponse;
import me.golemcore.hive.adapter.inbound.web.dto.threads.ThreadTargetGolemResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.domain.service.CardService;
import me.golemcore.hive.domain.service.GolemRegistryService;
import me.golemcore.hive.domain.service.ThreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ThreadsController {

    private final ThreadService threadService;
    private final CardService cardService;
    private final GolemRegistryService golemRegistryService;

    @GetMapping("/cards/{cardId}/thread")
    public Mono<ResponseEntity<CardThreadResponse>> getCardThread(Principal principal, @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Card card = cardService.getCard(cardId);
            ThreadRecord thread = threadService.getThreadByCardId(cardId);
            Golem targetGolem = card.getAssigneeGolemId() != null
                    ? golemRegistryService.findGolem(card.getAssigneeGolemId()).orElse(null)
                    : null;
            return ResponseEntity.ok(toCardThreadResponse(card, thread, targetGolem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/threads/{threadId}/messages")
    public Mono<ResponseEntity<List<ThreadMessageResponse>>> listThreadMessages(Principal principal, @PathVariable String threadId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<ThreadMessageResponse> response = threadService.listMessages(threadId).stream()
                    .map(this::toThreadMessageResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/threads/{threadId}/messages")
    public Mono<ResponseEntity<ThreadMessageResponse>> postThreadMessage(
            Principal principal,
            @PathVariable String threadId,
            @Valid @RequestBody PostThreadMessageRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            ThreadMessage message = threadService.postOperatorMessage(threadId, request.body(), actor.getSubjectId(), actor.getName());
            return ResponseEntity.ok(toThreadMessageResponse(message));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private CardThreadResponse toCardThreadResponse(Card card, ThreadRecord thread, Golem targetGolem) {
        return new CardThreadResponse(
                thread.getId(),
                card.getId(),
                card.getBoardId(),
                thread.getTitle(),
                card.getColumnId(),
                card.getAssigneeGolemId(),
                targetGolem != null ? new ThreadTargetGolemResponse(
                        targetGolem.getId(),
                        targetGolem.getDisplayName(),
                        targetGolem.getState().name(),
                        targetGolem.getRoleBindings().stream().map(binding -> binding.getRoleSlug()).sorted().toList())
                        : null,
                thread.getCreatedAt(),
                thread.getUpdatedAt(),
                thread.getLastMessageAt(),
                thread.getLastCommandAt());
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
