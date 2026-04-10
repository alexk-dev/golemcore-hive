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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardTransitionEvent;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.workflow.application.FlowRemapPreview;
import me.golemcore.hive.workflow.application.port.out.CardRepository;

public class FlowRemapApplicationService {

    private final CardRepository cardRepository;

    public FlowRemapApplicationService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public FlowRemapPreview preview(Board board, BoardFlowDefinition newFlow) {
        Set<String> currentColumnIds = board.getFlow().getColumns().stream()
                .map(column -> column.getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> nextColumnIds = newFlow.getColumns().stream()
                .map(column -> column.getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> removedColumnIds = new LinkedHashSet<>(currentColumnIds);
        removedColumnIds.removeAll(nextColumnIds);

        Map<String, Integer> affectedCardCounts = new LinkedHashMap<>();
        if (removedColumnIds.isEmpty()) {
            return new FlowRemapPreview(List.of(), affectedCardCounts);
        }

        for (Card card : loadCardsForBoard(board.getId())) {
            if (card.isArchived()) {
                continue;
            }
            if (removedColumnIds.contains(card.getColumnId())) {
                affectedCardCounts.merge(card.getColumnId(), 1, Integer::sum);
            }
        }
        return new FlowRemapPreview(new ArrayList<>(removedColumnIds), affectedCardCounts);
    }

    public void apply(
            Board board,
            BoardFlowDefinition newFlow,
            Map<String, String> columnRemap,
            String actorId,
            String actorName) {
        FlowRemapPreview preview = preview(board, newFlow);
        if (preview.removedColumnIds().isEmpty()) {
            return;
        }

        Set<String> validTargetColumns = newFlow.getColumns().stream()
                .map(column -> column.getId())
                .collect(java.util.stream.Collectors.toSet());
        for (String removedColumnId : preview.removedColumnIds()) {
            Integer affectedCount = preview.affectedCardCounts().getOrDefault(removedColumnId, 0);
            if (affectedCount > 0 && (columnRemap == null || !columnRemap.containsKey(removedColumnId))) {
                throw new IllegalArgumentException("Flow remap is required for column " + removedColumnId);
            }
            if (columnRemap != null && columnRemap.containsKey(removedColumnId)
                    && !validTargetColumns.contains(columnRemap.get(removedColumnId))) {
                throw new IllegalArgumentException(
                        "Flow remap target does not exist: " + columnRemap.get(removedColumnId));
            }
        }

        Instant now = Instant.now();
        List<Card> boardCards = loadCardsForBoard(board.getId());
        Set<String> affectedTargetColumns = new LinkedHashSet<>();
        for (Card card : boardCards) {
            if (card.isArchived()) {
                continue;
            }
            String targetColumnId = columnRemap != null ? columnRemap.get(card.getColumnId()) : null;
            if (targetColumnId == null || targetColumnId.equals(card.getColumnId())) {
                continue;
            }
            card.getTransitionEvents().add(CardTransitionEvent.builder()
                    .fromColumnId(card.getColumnId())
                    .toColumnId(targetColumnId)
                    .origin(CardTransitionOrigin.FLOW_REMAP)
                    .summary("Flow remap applied after board flow edit")
                    .occurredAt(now)
                    .actorId(actorId)
                    .actorName(actorName)
                    .build());
            card.setColumnId(targetColumnId);
            card.setUpdatedAt(now);
            card.setLastTransitionAt(now);
            affectedTargetColumns.add(targetColumnId);
            cardRepository.save(card);
        }
        renumberAffectedColumns(boardCards, affectedTargetColumns);
    }

    private List<Card> loadCardsForBoard(String boardId) {
        return cardRepository.list().stream()
                .filter(card -> boardId.equals(card.getBoardId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void renumberAffectedColumns(List<Card> boardCards, Set<String> affectedTargetColumns) {
        for (String columnId : affectedTargetColumns) {
            List<Card> columnCards = boardCards.stream()
                    .filter(card -> !card.isArchived() && columnId.equals(card.getColumnId()))
                    .sorted(java.util.Comparator
                            .comparing(Card::getPosition, java.util.Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(Card::getCreatedAt, java.util.Comparator.nullsLast(Instant::compareTo))
                            .thenComparing(Card::getId))
                    .toList();
            for (int index = 0; index < columnCards.size(); index++) {
                Card card = columnCards.get(index);
                card.setPosition(index);
                cardRepository.save(card);
            }
        }
    }
}
