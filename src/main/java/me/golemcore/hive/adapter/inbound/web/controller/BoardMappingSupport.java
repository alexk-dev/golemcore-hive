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

import java.util.List;
import java.util.Map;
import java.util.Set;
import me.golemcore.hive.adapter.inbound.web.dto.boards.AssignmentSuggestionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardColumnPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardCountsResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardDetailResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardFlowPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardSignalMappingPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardTeamFilterPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardTeamPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.BoardTransitionPayload;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardControlStateResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardDetailResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.CardTransitionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.boards.RemapPreviewResponse;
import me.golemcore.hive.domain.model.AssignmentSuggestion;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.BoardSignalDecision;
import me.golemcore.hive.domain.model.BoardSignalMapping;
import me.golemcore.hive.domain.model.BoardTeam;
import me.golemcore.hive.domain.model.BoardTeamFilter;
import me.golemcore.hive.domain.model.BoardTeamFilterType;
import me.golemcore.hive.domain.model.BoardTransitionRule;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardControlStateSnapshot;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardTransitionEvent;
import me.golemcore.hive.workflow.application.FlowRemapPreview;

abstract class BoardMappingSupport {

    protected BoardSummaryResponse toBoardSummaryResponse(Board board, List<Card> cards) {
        return new BoardSummaryResponse(
                board.getId(),
                board.getSlug(),
                board.getName(),
                board.getDescription(),
                board.getTemplateKey(),
                board.getDefaultAssignmentPolicy().name(),
                board.getUpdatedAt(),
                toBoardCounts(cards));
    }

    protected BoardDetailResponse toBoardDetailResponse(Board board, List<Card> cards) {
        return new BoardDetailResponse(
                board.getId(),
                board.getSlug(),
                board.getName(),
                board.getDescription(),
                board.getTemplateKey(),
                board.getDefaultAssignmentPolicy().name(),
                toBoardFlowPayload(board.getFlow()),
                toBoardTeamPayload(board.getTeam()),
                board.getCreatedAt(),
                board.getUpdatedAt(),
                toBoardCounts(cards));
    }

    protected CardSummaryResponse toCardSummaryResponse(Card card, CardControlStateSnapshot controlState) {
        return new CardSummaryResponse(
                card.getId(),
                card.getServiceId(),
                card.getBoardId(),
                cardKindName(card),
                card.getParentCardId(),
                card.getEpicCardId(),
                card.getDependsOnCardIds() != null ? card.getDependsOnCardIds() : List.of(),
                card.getReviewOfCardId(),
                card.getReviewerGolemIds() != null ? card.getReviewerGolemIds() : List.of(),
                card.getReviewerTeamId(),
                card.getRequiredReviewCount(),
                cardReviewStatusName(card),
                card.getTeamId(),
                card.getObjectiveId(),
                card.getThreadId(),
                card.getTitle(),
                card.getColumnId(),
                card.getAssigneeGolemId(),
                cardAssignmentPolicyName(card),
                card.getPosition(),
                card.isArchived(),
                toCardControlStateResponse(controlState));
    }

    protected CardDetailResponse toCardDetailResponse(Card card, CardControlStateSnapshot controlState) {
        return new CardDetailResponse(
                card.getId(),
                card.getServiceId(),
                card.getBoardId(),
                cardKindName(card),
                card.getParentCardId(),
                card.getEpicCardId(),
                card.getDependsOnCardIds() != null ? card.getDependsOnCardIds() : List.of(),
                card.getReviewOfCardId(),
                card.getReviewerGolemIds() != null ? card.getReviewerGolemIds() : List.of(),
                card.getReviewerTeamId(),
                card.getRequiredReviewCount(),
                cardReviewStatusName(card),
                card.getTeamId(),
                card.getObjectiveId(),
                card.getThreadId(),
                card.getTitle(),
                card.getDescription(),
                card.getPrompt(),
                card.getColumnId(),
                card.getAssigneeGolemId(),
                cardAssignmentPolicyName(card),
                card.getPosition(),
                card.isArchived(),
                card.getArchivedAt(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                card.getLastTransitionAt(),
                toCardControlStateResponse(controlState),
                card.getTransitionEvents().stream().map(this::toCardTransitionResponse).toList());
    }

    protected AssignmentSuggestionResponse toAssignmentSuggestionResponse(AssignmentSuggestion suggestion) {
        return new AssignmentSuggestionResponse(
                suggestion.getGolemId(),
                suggestion.getDisplayName(),
                suggestion.getState().name(),
                suggestion.getScore(),
                suggestion.getReasons(),
                suggestion.getRoleSlugs(),
                suggestion.isInBoardTeam());
    }

    protected BoardFlowDefinition toBoardFlowDefinition(BoardFlowPayload payload) {
        return BoardFlowDefinition.builder()
                .flowId(payload.flowId())
                .name(payload.name())
                .defaultColumnId(payload.defaultColumnId())
                .columns(payload.columns().stream().map(this::toBoardColumn).toList())
                .transitions(payload.transitions().stream().map(this::toBoardTransitionRule).toList())
                .signalMappings(payload.signalMappings().stream().map(this::toBoardSignalMapping).toList())
                .build();
    }

    protected BoardTeam toBoardTeam(BoardTeamPayload payload) {
        return BoardTeam.builder()
                .explicitGolemIds(
                        payload != null && payload.explicitGolemIds() != null ? payload.explicitGolemIds() : Set.of())
                .filters(payload != null && payload.filters() != null
                        ? payload.filters().stream().map(this::toBoardTeamFilter).toList()
                        : List.of())
                .build();
    }

    protected CardAssignmentPolicy parseAssignmentPolicy(String value) {
        return value != null && !value.isBlank()
                ? CardAssignmentPolicy.valueOf(value.toUpperCase(java.util.Locale.ROOT))
                : null;
    }

    protected CardKind parseCardKind(String value) {
        return value != null && !value.isBlank()
                ? CardKind.valueOf(value.toUpperCase(java.util.Locale.ROOT))
                : null;
    }

    protected String resolveServiceId(String serviceId, String boardId) {
        if (serviceId != null && !serviceId.isBlank()) {
            return serviceId;
        }
        if (boardId != null && !boardId.isBlank()) {
            return boardId;
        }
        throw new IllegalArgumentException("serviceId is required");
    }

    protected RemapPreviewResponse toRemapPreviewResponse(FlowRemapPreview preview) {
        return new RemapPreviewResponse(preview.removedColumnIds(), preview.affectedCardCounts());
    }

    private BoardColumn toBoardColumn(BoardColumnPayload payload) {
        return BoardColumn.builder()
                .id(payload.id())
                .name(payload.name())
                .description(payload.description())
                .wipLimit(payload.wipLimit())
                .terminal(payload.terminal())
                .build();
    }

    private BoardTransitionRule toBoardTransitionRule(BoardTransitionPayload payload) {
        return BoardTransitionRule.builder()
                .fromColumnId(payload.fromColumnId())
                .toColumnId(payload.toColumnId())
                .build();
    }

    private BoardSignalMapping toBoardSignalMapping(BoardSignalMappingPayload payload) {
        return BoardSignalMapping.builder()
                .signalType(payload.signalType())
                .decision(BoardSignalDecision.valueOf(payload.decision()))
                .targetColumnId(payload.targetColumnId())
                .build();
    }

    private BoardTeamFilter toBoardTeamFilter(BoardTeamFilterPayload payload) {
        return BoardTeamFilter.builder()
                .type(BoardTeamFilterType.valueOf(payload.type()))
                .value(payload.value())
                .build();
    }

    private BoardFlowPayload toBoardFlowPayload(BoardFlowDefinition flow) {
        return new BoardFlowPayload(
                flow.getFlowId(),
                flow.getName(),
                flow.getDefaultColumnId(),
                flow.getColumns().stream().map(column -> new BoardColumnPayload(
                        column.getId(),
                        column.getName(),
                        column.getDescription(),
                        column.getWipLimit(),
                        column.isTerminal())).toList(),
                flow.getTransitions().stream().map(transition -> new BoardTransitionPayload(
                        transition.getFromColumnId(),
                        transition.getToColumnId())).toList(),
                flow.getSignalMappings().stream().map(signalMapping -> new BoardSignalMappingPayload(
                        signalMapping.getSignalType(),
                        signalMapping.getDecision().name(),
                        signalMapping.getTargetColumnId())).toList());
    }

    private BoardTeamPayload toBoardTeamPayload(BoardTeam team) {
        return new BoardTeamPayload(
                team != null ? team.getExplicitGolemIds() : Set.of(),
                team != null ? team.getFilters().stream().map(filter -> new BoardTeamFilterPayload(
                        filter.getType().name(),
                        filter.getValue())).toList() : List.of());
    }

    private List<BoardCountsResponse> toBoardCounts(List<Card> cards) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Card card : cards) {
            counts.merge(card.getColumnId(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> new BoardCountsResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private CardTransitionResponse toCardTransitionResponse(CardTransitionEvent event) {
        return new CardTransitionResponse(
                event.getFromColumnId(),
                event.getToColumnId(),
                event.getOrigin().name(),
                event.getSummary(),
                event.getOccurredAt(),
                event.getActorName());
    }

    private CardControlStateResponse toCardControlStateResponse(CardControlStateSnapshot controlState) {
        if (controlState == null) {
            return null;
        }
        return new CardControlStateResponse(
                controlState.commandId(),
                controlState.runId(),
                controlState.golemId(),
                controlState.commandStatus() != null ? controlState.commandStatus().name() : null,
                controlState.runStatus() != null ? controlState.runStatus().name() : null,
                controlState.summary(),
                controlState.queueReason(),
                controlState.updatedAt(),
                controlState.cancelRequestedAt(),
                controlState.cancelRequestedByActorName(),
                controlState.cancelRequestedPending(),
                controlState.canCancel());
    }

    private String cardKindName(Card card) {
        return card.getKind() != null ? card.getKind().name() : CardKind.TASK.name();
    }

    private String cardAssignmentPolicyName(Card card) {
        return card.getAssignmentPolicy() != null ? card.getAssignmentPolicy().name()
                : CardAssignmentPolicy.MANUAL.name();
    }

    private String cardReviewStatusName(Card card) {
        return card.getReviewStatus() != null ? card.getReviewStatus().name() : null;
    }
}
