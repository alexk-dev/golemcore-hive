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
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardDetailResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardFlowPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardTeamPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CreateBoardRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.RemapPreviewResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.UpdateBoardFlowRequest;
import me.golemcore.hive.adapter.inbound.web.dto.boards.UpdateBoardRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.service.BoardService;
import me.golemcore.hive.domain.service.CardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardsController extends BoardMappingSupport {

    private final BoardService boardService;
    private final CardService cardService;

    @GetMapping
    public Mono<ResponseEntity<List<BoardSummaryResponse>>> listBoards(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<BoardSummaryResponse> response = boardService.listBoards().stream()
                    .map(board -> toBoardSummaryResponse(board, cardService.listCards(board.getId(), false)))
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<BoardDetailResponse>> createBoard(
            Principal principal,
            @Valid @RequestBody CreateBoardRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Board board = boardService.createBoard(
                    request.name(),
                    request.description(),
                    request.templateKey(),
                    parseAssignmentPolicy(request.defaultAssignmentPolicy()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toBoardDetailResponse(board, cardService.listCards(board.getId(), false)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{boardId}")
    public Mono<ResponseEntity<BoardDetailResponse>> getBoard(Principal principal, @PathVariable String boardId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Board board = boardService.getBoard(boardId);
            return ResponseEntity.ok(toBoardDetailResponse(board, cardService.listCards(boardId, false)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{boardId}")
    public Mono<ResponseEntity<BoardDetailResponse>> updateBoard(
            Principal principal,
            @PathVariable String boardId,
            @RequestBody UpdateBoardRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Board board = boardService.updateBoard(
                    boardId,
                    request != null ? request.name() : null,
                    request != null ? request.description() : null,
                    request != null ? parseAssignmentPolicy(request.defaultAssignmentPolicy()) : null);
            return ResponseEntity.ok(toBoardDetailResponse(board, cardService.listCards(boardId, false)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{boardId}/flow")
    public Mono<ResponseEntity<BoardDetailResponse>> updateBoardFlow(
            Principal principal,
            @PathVariable String boardId,
            @Valid @RequestBody UpdateBoardFlowRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Board board = boardService.updateBoardFlow(
                    boardId,
                    toBoardFlowDefinition(request.flow()),
                    request.columnRemap(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toBoardDetailResponse(board, cardService.listCards(boardId, false)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{boardId}/flow:preview")
    public Mono<ResponseEntity<RemapPreviewResponse>> previewBoardFlow(
            Principal principal,
            @PathVariable String boardId,
            @RequestBody BoardFlowPayload request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity
                    .ok(toRemapPreviewResponse(boardService.previewFlowRemap(boardId, toBoardFlowDefinition(request))));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{boardId}/team")
    public Mono<ResponseEntity<BoardDetailResponse>> updateBoardTeam(
            Principal principal,
            @PathVariable String boardId,
            @RequestBody BoardTeamPayload request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Board board = boardService.updateBoardTeam(boardId, toBoardTeam(request));
            return ResponseEntity.ok(toBoardDetailResponse(board, cardService.listCards(boardId, false)));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
