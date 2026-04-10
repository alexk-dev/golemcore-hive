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

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardTeamResolvedResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardAssigneeOptionsResponse;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.service.AssignmentService;
import me.golemcore.hive.domain.service.BoardService;
import me.golemcore.hive.domain.service.CardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
public class AssignmentsController extends BoardMappingSupport {

    private final AssignmentService assignmentService;
    private final BoardService boardService;
    private final CardService cardService;

    @GetMapping("/api/v1/boards/{boardId}/team")
    public Mono<ResponseEntity<BoardTeamResolvedResponse>> getBoardTeam(Principal principal,
            @PathVariable String boardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Board board = boardService.getBoard(boardId);
            return ResponseEntity.ok(new BoardTeamResolvedResponse(
                    boardId,
                    boardId,
                    assignmentService.getTeamCandidates(board).stream().map(this::toAssignmentSuggestionResponse)
                            .toList()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/v1/services/{serviceId}/team")
    public Mono<ResponseEntity<BoardTeamResolvedResponse>> getServiceTeam(Principal principal,
            @PathVariable String serviceId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Board board = boardService.getBoard(serviceId);
            return ResponseEntity.ok(new BoardTeamResolvedResponse(
                    serviceId,
                    serviceId,
                    assignmentService.getTeamCandidates(board).stream().map(this::toAssignmentSuggestionResponse)
                            .toList()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/v1/cards/{cardId}/assignees")
    public Mono<ResponseEntity<CardAssigneeOptionsResponse>> getCardAssignees(Principal principal,
            @PathVariable String cardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Card card = cardService.getCard(cardId);
            Board board = boardService.getBoard(card.getServiceId());
            return ResponseEntity.ok(new CardAssigneeOptionsResponse(
                    cardId,
                    board.getId(),
                    board.getId(),
                    assignmentService.getTeamCandidates(board).stream().map(this::toAssignmentSuggestionResponse)
                            .toList(),
                    assignmentService.getAllCandidates(board).stream().map(this::toAssignmentSuggestionResponse)
                            .toList()));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
