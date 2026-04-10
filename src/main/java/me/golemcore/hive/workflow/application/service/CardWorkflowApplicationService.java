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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.golemcore.hive.domain.model.AssignmentSuggestion;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardTransitionEvent;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ObjectiveWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.ThreadRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAssignmentPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;

public class CardWorkflowApplicationService implements CardWorkflowUseCase {

    private final CardRepository cardRepository;
    private final ThreadRepository threadRepository;
    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final WorkflowAssignmentPort workflowAssignmentPort;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final TeamWorkflowUseCase teamWorkflowUseCase;
    private final ObjectiveWorkflowUseCase objectiveWorkflowUseCase;
    private final WorkflowAuditPort workflowAuditPort;

    public CardWorkflowApplicationService(
            CardRepository cardRepository,
            ThreadRepository threadRepository,
            BoardWorkflowUseCase boardWorkflowUseCase,
            WorkflowAssignmentPort workflowAssignmentPort,
            GolemDirectoryUseCase golemDirectoryUseCase,
            TeamWorkflowUseCase teamWorkflowUseCase,
            ObjectiveWorkflowUseCase objectiveWorkflowUseCase,
            WorkflowAuditPort workflowAuditPort) {
        this.cardRepository = cardRepository;
        this.threadRepository = threadRepository;
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.workflowAssignmentPort = workflowAssignmentPort;
        this.golemDirectoryUseCase = golemDirectoryUseCase;
        this.teamWorkflowUseCase = teamWorkflowUseCase;
        this.objectiveWorkflowUseCase = objectiveWorkflowUseCase;
        this.workflowAuditPort = workflowAuditPort;
    }

    @Override
    public List<Card> listCards(String serviceId, boolean includeArchived) {
        String targetServiceId = normalizeOptionalId(serviceId);
        List<Card> cards = new ArrayList<>();
        for (Card storedCard : cardRepository.list()) {
            Card card = normalizeCard(storedCard);
            if (targetServiceId != null && !targetServiceId.equals(card.getServiceId())) {
                continue;
            }
            if (!includeArchived && card.isArchived()) {
                continue;
            }
            cards.add(card);
        }
        cards.sort(Comparator.comparing(Card::getColumnId).thenComparing(
                Card::getPosition,
                Comparator.nullsLast(Integer::compareTo)));
        return cards;
    }

    @Override
    public Optional<Card> findCard(String cardId) {
        return cardRepository.findById(cardId).map(this::normalizeCard);
    }

    @Override
    public Card getCard(String cardId) {
        return findCard(cardId).orElseThrow(() -> new IllegalArgumentException("Unknown card: " + cardId));
    }

    @Override
    public Card createCard(
            String serviceId,
            String title,
            String description,
            String prompt,
            String columnId,
            String teamId,
            String objectiveId,
            String assigneeGolemId,
            CardAssignmentPolicy assignmentPolicy,
            boolean autoAssign,
            String actorId,
            String actorName) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Card title is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Card prompt is required");
        }
        String effectiveServiceId = requireServiceId(serviceId);
        String effectiveTeamId = normalizeOptionalId(teamId);
        String effectiveObjectiveId = normalizeOptionalId(objectiveId);
        validateCardScope(effectiveServiceId, effectiveTeamId, effectiveObjectiveId);

        Board board = boardWorkflowUseCase.getBoard(effectiveServiceId);
        String targetColumnId = columnId != null && !columnId.isBlank() ? columnId
                : board.getFlow().getDefaultColumnId();
        boolean columnExists = board.getFlow().getColumns().stream()
                .anyMatch(column -> column.getId().equals(targetColumnId));
        if (!columnExists) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }

        Instant now = Instant.now();
        String cardId = "card_" + UUID.randomUUID().toString().replace("-", "");
        String threadId = "thread_" + UUID.randomUUID().toString().replace("-", "");
        CardAssignmentPolicy effectivePolicy = assignmentPolicy != null
                ? assignmentPolicy
                : board.getDefaultAssignmentPolicy();
        String effectiveAssigneeId = normalizeOptionalId(assigneeGolemId);

        if (effectiveAssigneeId != null) {
            String requestedAssigneeId = effectiveAssigneeId;
            golemDirectoryUseCase.findGolem(requestedAssigneeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + requestedAssigneeId));
        } else if (autoAssign && effectivePolicy == CardAssignmentPolicy.AUTOMATIC) {
            AssignmentSuggestion suggestion = workflowAssignmentPort.suggestDefaultAssignee(board);
            if (suggestion != null) {
                effectiveAssigneeId = suggestion.getGolemId();
            }
        }

        Card card = Card.builder()
                .id(cardId)
                .serviceId(effectiveServiceId)
                .boardId(effectiveServiceId)
                .teamId(effectiveTeamId)
                .objectiveId(effectiveObjectiveId)
                .threadId(threadId)
                .title(title)
                .description(description)
                .prompt(prompt)
                .columnId(targetColumnId)
                .assigneeGolemId(effectiveAssigneeId)
                .assignmentPolicy(effectivePolicy)
                .position(nextPosition(effectiveServiceId, targetColumnId))
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
        cardRepository.save(card);
        threadRepository.saveThread(ThreadRecord.builder()
                .id(threadId)
                .serviceId(effectiveServiceId)
                .boardId(effectiveServiceId)
                .teamId(effectiveTeamId)
                .objectiveId(effectiveObjectiveId)
                .cardId(cardId)
                .title(title)
                .assignedGolemId(effectiveAssigneeId)
                .createdAt(now)
                .updatedAt(now)
                .build());
        workflowAuditPort.record(AuditEvent.builder()
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

    @Override
    public Card updateCard(
            String cardId,
            String title,
            String description,
            String prompt,
            String teamId,
            String objectiveId,
            CardAssignmentPolicy assignmentPolicy) {
        Card card = normalizeCard(getCard(cardId));
        if (title != null && !title.isBlank()) {
            card.setTitle(title);
        }
        if (description != null) {
            card.setDescription(description);
        }
        if (prompt != null) {
            if (prompt.isBlank()) {
                throw new IllegalArgumentException("Card prompt is required");
            }
            card.setPrompt(prompt);
        }
        String effectiveTeamId = card.getTeamId();
        if (teamId != null) {
            effectiveTeamId = normalizeOptionalId(teamId);
        }
        String effectiveObjectiveId = card.getObjectiveId();
        if (objectiveId != null) {
            effectiveObjectiveId = normalizeOptionalId(objectiveId);
        }
        validateCardScope(card.getServiceId(), effectiveTeamId, effectiveObjectiveId);

        if (teamId != null) {
            card.setTeamId(effectiveTeamId);
        }
        if (objectiveId != null) {
            card.setObjectiveId(effectiveObjectiveId);
        }
        if (assignmentPolicy != null) {
            card.setAssignmentPolicy(assignmentPolicy);
        }
        card.setUpdatedAt(Instant.now());
        cardRepository.save(card);
        syncThread(card);
        workflowAuditPort.record(AuditEvent.builder()
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

    @Override
    public Card moveCard(
            String cardId,
            String targetColumnId,
            Integer targetIndex,
            CardTransitionOrigin origin,
            String actorId,
            String actorName,
            String summary) {
        Card card = normalizeCard(getCard(cardId));
        Board board = boardWorkflowUseCase.getBoard(card.getServiceId());
        boolean targetExists = board.getFlow().getColumns().stream()
                .anyMatch(column -> column.getId().equals(targetColumnId));
        if (!targetExists) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }
        boolean transitionAllowed = origin == CardTransitionOrigin.BOARD_AUTOMATION
                ? boardWorkflowUseCase.isTransitionReachable(board, card.getColumnId(), targetColumnId)
                : boardWorkflowUseCase.isTransitionAllowed(board, card.getColumnId(), targetColumnId);
        if (!transitionAllowed) {
            throw new IllegalArgumentException("Transition is not allowed by board flow");
        }

        List<Card> boardCards = listCards(card.getServiceId(), false);
        List<Card> targetColumnCards = boardCards.stream()
                .filter(existing -> !existing.getId().equals(card.getId())
                        && targetColumnId.equals(existing.getColumnId()))
                .sorted(Comparator.comparing(Card::getPosition, Comparator.nullsLast(Integer::compareTo)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int insertIndex = targetIndex != null
                ? Math.max(0, Math.min(targetIndex, targetColumnCards.size()))
                : targetColumnCards.size();
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

        String previousColumnId = card.getTransitionEvents().get(card.getTransitionEvents().size() - 1)
                .getFromColumnId();
        if (previousColumnId != null && !previousColumnId.equals(targetColumnId)) {
            List<Card> previousColumnCards = boardCards.stream()
                    .filter(existing -> !existing.getId().equals(card.getId())
                            && previousColumnId.equals(existing.getColumnId()))
                    .sorted(Comparator.comparing(Card::getPosition, Comparator.nullsLast(Integer::compareTo)))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            renumberAndSave(previousColumnCards);
        }
        workflowAuditPort.record(AuditEvent.builder()
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

    @Override
    public Card assignCard(String cardId, String assigneeGolemId) {
        Card card = normalizeCard(getCard(cardId));
        if (assigneeGolemId != null && !assigneeGolemId.isBlank()) {
            golemDirectoryUseCase.findGolem(assigneeGolemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + assigneeGolemId));
        }
        card.setAssigneeGolemId(assigneeGolemId != null && !assigneeGolemId.isBlank() ? assigneeGolemId : null);
        card.setUpdatedAt(Instant.now());
        cardRepository.save(card);
        syncThread(card);
        workflowAuditPort.record(AuditEvent.builder()
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

    @Override
    public Card archiveCard(String cardId) {
        Card card = normalizeCard(getCard(cardId));
        card.setArchived(true);
        card.setArchivedAt(Instant.now());
        card.setUpdatedAt(card.getArchivedAt());
        cardRepository.save(card);
        workflowAuditPort.record(AuditEvent.builder()
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

    private void syncThread(Card card) {
        Optional<ThreadRecord> existingThread = threadRepository.findThread(card.getThreadId());
        if (existingThread.isEmpty()) {
            return;
        }
        ThreadRecord thread = existingThread.get();
        thread.setServiceId(card.getServiceId());
        thread.setBoardId(card.getBoardId());
        thread.setTeamId(card.getTeamId());
        thread.setObjectiveId(card.getObjectiveId());
        thread.setTitle(card.getTitle());
        thread.setAssignedGolemId(card.getAssigneeGolemId());
        thread.setUpdatedAt(card.getUpdatedAt());
        threadRepository.saveThread(thread);
    }

    private Integer nextPosition(String serviceId, String columnId) {
        return listCards(serviceId, false).stream()
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
            cardRepository.save(normalizeCard(card));
        }
    }

    private void validateCardScope(String serviceId, String teamId, String objectiveId) {
        String effectiveServiceId = requireServiceId(serviceId);
        String effectiveTeamId = normalizeOptionalId(teamId);
        String effectiveObjectiveId = normalizeOptionalId(objectiveId);

        if (effectiveTeamId != null) {
            Team team = teamWorkflowUseCase.getTeam(effectiveTeamId);
            if (team.getOwnedServiceIds() == null || !team.getOwnedServiceIds().contains(effectiveServiceId)) {
                throw new IllegalArgumentException("Team does not own service: " + effectiveServiceId);
            }
        }
        if (effectiveObjectiveId == null) {
            return;
        }

        Objective objective = objectiveWorkflowUseCase.getObjective(effectiveObjectiveId);
        if (!objective.getServiceIds().isEmpty() && !objective.getServiceIds().contains(effectiveServiceId)) {
            throw new IllegalArgumentException("Objective does not include service: " + effectiveServiceId);
        }
        if (effectiveTeamId != null
                && !effectiveTeamId.equals(objective.getOwnerTeamId())
                && !objective.getParticipatingTeamIds().contains(effectiveTeamId)) {
            throw new IllegalArgumentException("Team is not linked to objective: " + effectiveTeamId);
        }
    }

    private Card normalizeCard(Card card) {
        if (card == null) {
            return null;
        }
        if (card.getServiceId() == null || card.getServiceId().isBlank()) {
            card.setServiceId(card.getBoardId());
        }
        if (card.getBoardId() == null || card.getBoardId().isBlank()) {
            card.setBoardId(card.getServiceId());
        }
        return card;
    }

    private String requireServiceId(String serviceId) {
        String normalized = normalizeOptionalId(serviceId);
        if (normalized == null) {
            throw new IllegalArgumentException("serviceId is required");
        }
        return normalized;
    }

    private String normalizeOptionalId(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
