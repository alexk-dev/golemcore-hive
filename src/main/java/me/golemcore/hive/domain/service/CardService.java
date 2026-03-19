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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AssignmentSuggestion;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardTransitionEvent;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final String CARDS_DIR = "cards";
    private static final String THREADS_DIR = "threads";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final BoardService boardService;
    private final AssignmentService assignmentService;
    private final GolemRegistryService golemRegistryService;
    private final AuditService auditService;

    public List<Card> listCards(String boardId, boolean includeArchived) {
        List<Card> cards = new ArrayList<>();
        for (String path : storagePort.listObjects(CARDS_DIR, "")) {
            Optional<Card> cardOptional = loadCardByPath(path);
            if (cardOptional.isEmpty()) {
                continue;
            }
            Card card = cardOptional.get();
            if (boardId != null && !boardId.equals(card.getBoardId())) {
                continue;
            }
            if (!includeArchived && card.isArchived()) {
                continue;
            }
            cards.add(card);
        }
        cards.sort(Comparator.comparing(Card::getColumnId).thenComparing(Card::getPosition, Comparator.nullsLast(Integer::compareTo)));
        return cards;
    }

    public Optional<Card> findCard(String cardId) {
        String content = storagePort.getText(CARDS_DIR, cardId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Card.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize card " + cardId, exception);
        }
    }

    public Card getCard(String cardId) {
        return findCard(cardId).orElseThrow(() -> new IllegalArgumentException("Unknown card: " + cardId));
    }

    public Card createCard(String boardId,
                           String title,
                           String description,
                           String columnId,
                           String assigneeGolemId,
                           CardAssignmentPolicy assignmentPolicy,
                           boolean autoAssign,
                           String actorId,
                           String actorName) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Card title is required");
        }
        Board board = boardService.getBoard(boardId);
        String targetColumnId = columnId != null && !columnId.isBlank() ? columnId : board.getFlow().getDefaultColumnId();
        boolean columnExists = board.getFlow().getColumns().stream().anyMatch(column -> column.getId().equals(targetColumnId));
        if (!columnExists) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }

        Instant now = Instant.now();
        String cardId = "card_" + UUID.randomUUID().toString().replace("-", "");
        String threadId = "thread_" + UUID.randomUUID().toString().replace("-", "");
        CardAssignmentPolicy effectivePolicy = assignmentPolicy != null ? assignmentPolicy : board.getDefaultAssignmentPolicy();
        String effectiveAssigneeId = assigneeGolemId;

        if (effectiveAssigneeId != null && !effectiveAssigneeId.isBlank()) {
            String requestedAssigneeId = effectiveAssigneeId;
            golemRegistryService.findGolem(requestedAssigneeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + requestedAssigneeId));
        } else if (autoAssign && effectivePolicy == CardAssignmentPolicy.AUTOMATIC) {
            AssignmentSuggestion suggestion = assignmentService.suggestDefaultAssignee(board);
            effectiveAssigneeId = suggestion != null ? suggestion.getGolemId() : null;
        }

        Card card = Card.builder()
                .id(cardId)
                .boardId(boardId)
                .threadId(threadId)
                .title(title)
                .description(description)
                .columnId(targetColumnId)
                .assigneeGolemId(effectiveAssigneeId)
                .assignmentPolicy(effectivePolicy)
                .position(nextPosition(boardId, targetColumnId))
                .createdAt(now)
                .updatedAt(now)
                .lastTransitionAt(now)
                .createdByOperatorId(actorId)
                .createdByOperatorUsername(actorName)
                .transitionEvents(new ArrayList<>(List.of(CardTransitionEvent.builder()
                        .fromColumnId(null)
                        .toColumnId(targetColumnId)
                        .origin(CardTransitionOrigin.MANUAL)
                        .summary("Card created")
                        .occurredAt(now)
                        .actorId(actorId)
                        .actorName(actorName)
                        .build())))
                .build();
        saveCard(card);
        saveThread(ThreadRecord.builder()
                .id(threadId)
                .boardId(boardId)
                .cardId(cardId)
                .title(title)
                .assignedGolemId(effectiveAssigneeId)
                .createdAt(now)
                .updatedAt(now)
                .build());
        auditService.record(AuditEvent.builder()
                .eventType("card.created")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("CARD")
                .targetId(card.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(card.getThreadId())
                .golemId(card.getAssigneeGolemId())
                .summary("Card created")
                .details(card.getTitle()));
        return card;
    }

    public Card updateCard(String cardId,
                           String title,
                           String description,
                           CardAssignmentPolicy assignmentPolicy) {
        Card card = getCard(cardId);
        if (title != null && !title.isBlank()) {
            card.setTitle(title);
        }
        if (description != null) {
            card.setDescription(description);
        }
        if (assignmentPolicy != null) {
            card.setAssignmentPolicy(assignmentPolicy);
        }
        card.setUpdatedAt(Instant.now());
        saveCard(card);
        syncThread(card);
        auditService.record(AuditEvent.builder()
                .eventType("card.updated")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("CARD")
                .targetId(card.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(card.getThreadId())
                .golemId(card.getAssigneeGolemId())
                .summary("Card updated")
                .details(card.getTitle()));
        return card;
    }

    public Card moveCard(String cardId,
                         String targetColumnId,
                         Integer targetIndex,
                         CardTransitionOrigin origin,
                         String actorId,
                         String actorName,
                         String summary) {
        Card card = getCard(cardId);
        Board board = boardService.getBoard(card.getBoardId());
        boolean targetExists = board.getFlow().getColumns().stream().anyMatch(column -> column.getId().equals(targetColumnId));
        if (!targetExists) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }
        boolean transitionAllowed = origin == CardTransitionOrigin.BOARD_AUTOMATION
                ? boardService.isTransitionReachable(board, card.getColumnId(), targetColumnId)
                : boardService.isTransitionAllowed(board, card.getColumnId(), targetColumnId);
        if (!transitionAllowed) {
            throw new IllegalArgumentException("Transition is not allowed by board flow");
        }

        List<Card> boardCards = listCards(card.getBoardId(), false);
        List<Card> targetColumnCards = boardCards.stream()
                .filter(existing -> !existing.getId().equals(card.getId()) && targetColumnId.equals(existing.getColumnId()))
                .sorted(Comparator.comparing(Card::getPosition, Comparator.nullsLast(Integer::compareTo)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int insertIndex = targetIndex != null ? Math.max(0, Math.min(targetIndex, targetColumnCards.size())) : targetColumnCards.size();
        card.getTransitionEvents().add(CardTransitionEvent.builder()
                .fromColumnId(card.getColumnId())
                .toColumnId(targetColumnId)
                .origin(origin != null ? origin : CardTransitionOrigin.MANUAL)
                .summary(summary != null ? summary : "Card moved")
                .occurredAt(Instant.now())
                .actorId(actorId)
                .actorName(actorName)
                .build());
        card.setColumnId(targetColumnId);
        card.setUpdatedAt(Instant.now());
        card.setLastTransitionAt(card.getUpdatedAt());

        targetColumnCards.add(insertIndex, card);
        renumberAndSave(targetColumnCards);

        String previousColumnId = card.getTransitionEvents().get(card.getTransitionEvents().size() - 1).getFromColumnId();
        if (previousColumnId != null && !previousColumnId.equals(targetColumnId)) {
            List<Card> previousColumnCards = boardCards.stream()
                    .filter(existing -> !existing.getId().equals(card.getId()) && previousColumnId.equals(existing.getColumnId()))
                    .sorted(Comparator.comparing(Card::getPosition, Comparator.nullsLast(Integer::compareTo)))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            renumberAndSave(previousColumnCards);
        }
        auditService.record(AuditEvent.builder()
                .eventType("card.moved")
                .severity("INFO")
                .actorType(origin == CardTransitionOrigin.GOLEM_SIGNAL ? "GOLEM" : "OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("CARD")
                .targetId(card.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(card.getThreadId())
                .golemId(card.getAssigneeGolemId())
                .summary(firstNonBlank(summary, "Card moved"))
                .details(previousColumnId + " -> " + targetColumnId));
        return card;
    }

    public Card assignCard(String cardId, String assigneeGolemId) {
        Card card = getCard(cardId);
        if (assigneeGolemId != null && !assigneeGolemId.isBlank()) {
            golemRegistryService.findGolem(assigneeGolemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + assigneeGolemId));
        }
        card.setAssigneeGolemId(assigneeGolemId != null && !assigneeGolemId.isBlank() ? assigneeGolemId : null);
        card.setUpdatedAt(Instant.now());
        saveCard(card);
        syncThread(card);
        auditService.record(AuditEvent.builder()
                .eventType("card.assignee_updated")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("CARD")
                .targetId(card.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(card.getThreadId())
                .golemId(card.getAssigneeGolemId())
                .summary("Card assignee updated")
                .details(card.getAssigneeGolemId()));
        return card;
    }

    public Card archiveCard(String cardId) {
        Card card = getCard(cardId);
        card.setArchived(true);
        card.setArchivedAt(Instant.now());
        card.setUpdatedAt(card.getArchivedAt());
        saveCard(card);
        auditService.record(AuditEvent.builder()
                .eventType("card.archived")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("CARD")
                .targetId(card.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(card.getThreadId())
                .golemId(card.getAssigneeGolemId())
                .summary("Card archived")
                .details(card.getTitle()));
        return card;
    }

    private Optional<Card> loadCardByPath(String path) {
        String content = storagePort.getText(CARDS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Card.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize card " + path, exception);
        }
    }

    private void saveCard(Card card) {
        try {
            storagePort.putTextAtomic(CARDS_DIR, card.getId() + ".json", objectMapper.writeValueAsString(card));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize card " + card.getId(), exception);
        }
    }

    private void saveThread(ThreadRecord thread) {
        try {
            storagePort.putTextAtomic(THREADS_DIR, thread.getId() + ".json", objectMapper.writeValueAsString(thread));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread " + thread.getId(), exception);
        }
    }

    private void syncThread(Card card) {
        String content = storagePort.getText(THREADS_DIR, card.getThreadId() + ".json");
        if (content == null) {
            return;
        }
        try {
            ThreadRecord thread = objectMapper.readValue(content, ThreadRecord.class);
            thread.setTitle(card.getTitle());
            thread.setAssignedGolemId(card.getAssigneeGolemId());
            thread.setUpdatedAt(card.getUpdatedAt());
            saveThread(thread);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread " + card.getThreadId(), exception);
        }
    }

    private Integer nextPosition(String boardId, String columnId) {
        return listCards(boardId, false).stream()
                .filter(card -> columnId.equals(card.getColumnId()))
                .map(Card::getPosition)
                .filter(position -> position != null)
                .max(Integer::compareTo)
                .map(position -> position + 1)
                .orElse(0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void renumberAndSave(List<Card> cards) {
        for (int index = 0; index < cards.size(); index++) {
            Card card = cards.get(index);
            card.setPosition(index);
            saveCard(card);
        }
    }
}
