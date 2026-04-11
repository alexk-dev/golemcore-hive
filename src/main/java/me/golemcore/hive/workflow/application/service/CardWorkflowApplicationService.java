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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.golemcore.hive.domain.model.AssignmentSuggestion;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardReviewStatus;
import me.golemcore.hive.domain.model.CardTransitionEvent;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.CardQuery;
import me.golemcore.hive.workflow.application.CardUpdateCommand;
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
        return listCards(new CardQuery(serviceId, includeArchived, null, null, null, null, null));
    }

    @Override
    public List<Card> listCards(CardQuery query) {
        String targetServiceId = query != null ? normalizeOptionalId(query.serviceId()) : null;
        boolean includeArchived = query != null && query.includeArchived();
        CardKind targetKind = query != null ? query.kind() : null;
        String targetParentCardId = query != null ? normalizeOptionalId(query.parentCardId()) : null;
        String targetEpicCardId = query != null ? normalizeOptionalId(query.epicCardId()) : null;
        String targetReviewOfCardId = query != null ? normalizeOptionalId(query.reviewOfCardId()) : null;
        String targetObjectiveId = query != null ? normalizeOptionalId(query.objectiveId()) : null;
        List<Card> cards = new ArrayList<>();
        for (Card storedCard : cardRepository.list()) {
            Card card = normalizeCard(storedCard);
            if (targetServiceId != null && !targetServiceId.equals(card.getServiceId())) {
                continue;
            }
            if (!includeArchived && card.isArchived()) {
                continue;
            }
            if (targetKind != null && card.getKind() != targetKind) {
                continue;
            }
            if (targetParentCardId != null && !targetParentCardId.equals(card.getParentCardId())) {
                continue;
            }
            if (targetEpicCardId != null && !targetEpicCardId.equals(card.getEpicCardId())) {
                continue;
            }
            if (targetReviewOfCardId != null && !targetReviewOfCardId.equals(card.getReviewOfCardId())) {
                continue;
            }
            if (targetObjectiveId != null && !targetObjectiveId.equals(card.getObjectiveId())) {
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
        return createCard(new CardCreateCommand(
                serviceId,
                title,
                description,
                prompt,
                columnId,
                teamId,
                objectiveId,
                assigneeGolemId,
                assignmentPolicy,
                autoAssign,
                CardKind.TASK,
                null,
                null,
                null,
                null), actorId, actorName);
    }

    @Override
    public Card createCard(CardCreateCommand command, String actorId, String actorName) {
        if (command == null) {
            throw new IllegalArgumentException("Card create command is required");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("Card title is required");
        }
        if (command.prompt() == null || command.prompt().isBlank()) {
            throw new IllegalArgumentException("Card prompt is required");
        }
        String effectiveServiceId = requireServiceId(command.serviceId());
        String effectiveTeamId = normalizeOptionalId(command.teamId());
        String effectiveObjectiveId = normalizeOptionalId(command.objectiveId());
        validateCardScope(effectiveServiceId, effectiveTeamId, effectiveObjectiveId);

        String cardId = "card_" + UUID.randomUUID().toString().replace("-", "");
        CardKind effectiveKind = command.kind() != null ? command.kind() : CardKind.TASK;
        String effectiveParentCardId = normalizeOptionalId(command.parentCardId());
        String effectiveEpicCardId = resolveEpicCardId(
                effectiveServiceId,
                effectiveParentCardId,
                normalizeOptionalId(command.epicCardId()),
                cardId,
                effectiveKind);
        String effectiveReviewOfCardId = resolveReviewOfCardId(
                effectiveServiceId,
                effectiveKind,
                normalizeOptionalId(command.reviewOfCardId()),
                cardId);
        List<String> effectiveDependsOnCardIds = normalizeCardReferences(command.dependsOnCardIds());
        validateCardGraph(
                effectiveServiceId,
                cardId,
                effectiveKind,
                effectiveParentCardId,
                effectiveEpicCardId,
                effectiveDependsOnCardIds);

        Board board = boardWorkflowUseCase.getBoard(effectiveServiceId);
        String targetColumnId = command.columnId() != null && !command.columnId().isBlank()
                ? command.columnId()
                : board.getFlow().getDefaultColumnId();
        boolean columnExists = board.getFlow().getColumns().stream()
                .anyMatch(column -> column.getId().equals(targetColumnId));
        if (!columnExists) {
            throw new IllegalArgumentException("Target column does not exist in board flow");
        }

        Instant now = Instant.now();
        String threadId = "thread_" + UUID.randomUUID().toString().replace("-", "");
        CardAssignmentPolicy effectivePolicy = command.assignmentPolicy() != null
                ? command.assignmentPolicy()
                : board.getDefaultAssignmentPolicy();
        String effectiveAssigneeId = normalizeOptionalId(command.assigneeGolemId());

        if (effectiveAssigneeId != null) {
            String requestedAssigneeId = effectiveAssigneeId;
            golemDirectoryUseCase.findGolem(requestedAssigneeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown assignee golem: " + requestedAssigneeId));
        } else if (command.autoAssign() && effectivePolicy == CardAssignmentPolicy.AUTOMATIC) {
            AssignmentSuggestion suggestion = workflowAssignmentPort.suggestDefaultAssignee(board);
            if (suggestion != null) {
                effectiveAssigneeId = suggestion.getGolemId();
            }
        }

        Card card = Card.builder()
                .id(cardId)
                .serviceId(effectiveServiceId)
                .boardId(effectiveServiceId)
                .kind(effectiveKind)
                .parentCardId(effectiveParentCardId)
                .epicCardId(effectiveEpicCardId)
                .reviewOfCardId(effectiveReviewOfCardId)
                .dependsOnCardIds(new ArrayList<>(effectiveDependsOnCardIds))
                .teamId(effectiveTeamId)
                .objectiveId(effectiveObjectiveId)
                .threadId(threadId)
                .title(command.title())
                .description(command.description())
                .prompt(command.prompt())
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
        cardRepository.save(normalizeCard(card));
        threadRepository.saveThread(ThreadRecord.builder()
                .id(threadId)
                .serviceId(effectiveServiceId)
                .boardId(effectiveServiceId)
                .teamId(effectiveTeamId)
                .objectiveId(effectiveObjectiveId)
                .cardId(cardId)
                .title(command.title())
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
        return updateCard(cardId, new CardUpdateCommand(
                title,
                description,
                prompt,
                teamId,
                objectiveId,
                assignmentPolicy,
                null,
                null,
                null,
                null,
                null));
    }

    @Override
    public Card updateCard(String cardId, CardUpdateCommand command) {
        Card card = normalizeCard(getCard(cardId));
        if (command == null) {
            throw new IllegalArgumentException("Card update command is required");
        }
        if (command.title() != null && !command.title().isBlank()) {
            card.setTitle(command.title());
        }
        if (command.description() != null) {
            card.setDescription(command.description());
        }

        String effectivePrompt = card.getPrompt();
        if (command.prompt() != null) {
            if (command.prompt().isBlank()) {
                throw new IllegalArgumentException("Card prompt is required");
            }
            effectivePrompt = command.prompt();
        }

        String effectiveTeamId = command.teamId() != null ? normalizeOptionalId(command.teamId()) : card.getTeamId();
        String effectiveObjectiveId = command.objectiveId() != null ? normalizeOptionalId(command.objectiveId())
                : card.getObjectiveId();
        CardKind effectiveKind = command.kind() != null ? command.kind() : card.getKind();
        String effectiveParentCardId = command.parentCardId() != null ? normalizeOptionalId(command.parentCardId())
                : card.getParentCardId();
        String explicitEpicCardId = command.epicCardId() != null ? normalizeOptionalId(command.epicCardId())
                : card.getEpicCardId();
        String explicitReviewOfCardId = command.reviewOfCardId() != null ? normalizeOptionalId(command.reviewOfCardId())
                : card.getReviewOfCardId();
        List<String> effectiveDependsOnCardIds = command.dependsOnCardIds() != null
                ? normalizeCardReferences(command.dependsOnCardIds())
                : normalizeCardReferences(card.getDependsOnCardIds());

        validateCardScope(card.getServiceId(), effectiveTeamId, effectiveObjectiveId);
        String effectiveEpicCardId = resolveEpicCardId(
                card.getServiceId(),
                effectiveParentCardId,
                explicitEpicCardId,
                card.getId(),
                effectiveKind);
        String effectiveReviewOfCardId = resolveReviewOfCardId(
                card.getServiceId(),
                effectiveKind,
                explicitReviewOfCardId,
                card.getId());
        validateCardGraph(
                card.getServiceId(),
                card.getId(),
                effectiveKind,
                effectiveParentCardId,
                effectiveEpicCardId,
                effectiveDependsOnCardIds);
        if (effectiveKind == CardKind.TASK && (effectivePrompt == null || effectivePrompt.isBlank())) {
            throw new IllegalArgumentException("Card prompt is required");
        }

        card.setKind(effectiveKind);
        card.setPrompt(effectivePrompt);
        card.setTeamId(effectiveTeamId);
        card.setObjectiveId(effectiveObjectiveId);
        card.setParentCardId(effectiveParentCardId);
        card.setEpicCardId(effectiveEpicCardId);
        card.setReviewOfCardId(effectiveReviewOfCardId);
        card.setDependsOnCardIds(new ArrayList<>(effectiveDependsOnCardIds));
        if (command.assignmentPolicy() != null) {
            card.setAssignmentPolicy(command.assignmentPolicy());
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

    private void validateCardGraph(
            String serviceId,
            String currentCardId,
            CardKind kind,
            String parentCardId,
            String epicCardId,
            List<String> dependsOnCardIds) {
        if (kind == CardKind.EPIC) {
            if (parentCardId != null) {
                throw new IllegalArgumentException("Epic cards cannot have a parent card");
            }
            if (epicCardId != null) {
                throw new IllegalArgumentException("Epic cards cannot belong to another epic");
            }
        }

        validateGraphReference(serviceId, currentCardId, parentCardId, "parent");
        validateGraphReference(serviceId, currentCardId, epicCardId, "epic");
        if (epicCardId != null) {
            Card epic = getCard(epicCardId);
            if (epic.getKind() != CardKind.EPIC) {
                throw new IllegalArgumentException("Epic reference must point to an EPIC card");
            }
        }
        if (dependsOnCardIds != null) {
            for (String dependencyCardId : dependsOnCardIds) {
                validateGraphReference(serviceId, currentCardId, dependencyCardId, "dependency");
            }
        }
    }

    private void validateGraphReference(String serviceId, String currentCardId, String referenceCardId, String label) {
        if (referenceCardId == null) {
            return;
        }
        if (referenceCardId.equals(currentCardId)) {
            throw new IllegalArgumentException("Card cannot reference itself as " + label);
        }
        Card referencedCard = getCard(referenceCardId);
        if (!serviceId.equals(referencedCard.getServiceId())) {
            throw new IllegalArgumentException("Card graph reference must stay within service: " + referenceCardId);
        }
    }

    private String resolveEpicCardId(
            String serviceId,
            String parentCardId,
            String epicCardId,
            String currentCardId,
            CardKind kind) {
        if (kind == CardKind.EPIC) {
            return null;
        }

        String effectiveParentCardId = normalizeOptionalId(parentCardId);
        String effectiveEpicCardId = normalizeOptionalId(epicCardId);
        String derivedEpicCardId = null;
        if (effectiveParentCardId != null) {
            Card parentCard = getCard(effectiveParentCardId);
            if (!serviceId.equals(parentCard.getServiceId())) {
                throw new IllegalArgumentException("Card graph reference must stay within service: "
                        + effectiveParentCardId);
            }
            if (effectiveParentCardId.equals(currentCardId)) {
                throw new IllegalArgumentException("Card cannot reference itself as parent");
            }
            if (parentCard.getKind() == CardKind.EPIC) {
                derivedEpicCardId = parentCard.getId();
            } else {
                derivedEpicCardId = normalizeOptionalId(parentCard.getEpicCardId());
            }
        }
        if (effectiveEpicCardId != null) {
            Card epicCard = getCard(effectiveEpicCardId);
            if (!serviceId.equals(epicCard.getServiceId())) {
                throw new IllegalArgumentException(
                        "Card graph reference must stay within service: " + effectiveEpicCardId);
            }
            if (effectiveEpicCardId.equals(currentCardId)) {
                throw new IllegalArgumentException("Card cannot reference itself as epic");
            }
            if (epicCard.getKind() != CardKind.EPIC) {
                throw new IllegalArgumentException("Epic reference must point to an EPIC card");
            }
            if (derivedEpicCardId != null && !derivedEpicCardId.equals(effectiveEpicCardId)) {
                throw new IllegalArgumentException("Parent card belongs to a different epic");
            }
            return effectiveEpicCardId;
        }
        return derivedEpicCardId;
    }

    private String resolveReviewOfCardId(String serviceId, CardKind kind, String reviewOfCardId, String currentCardId) {
        String effectiveReviewOfCardId = normalizeOptionalId(reviewOfCardId);
        if (kind != CardKind.REVIEW) {
            if (effectiveReviewOfCardId != null) {
                throw new IllegalArgumentException("Only review cards can reference reviewOfCardId");
            }
            return null;
        }
        if (effectiveReviewOfCardId == null) {
            throw new IllegalArgumentException("Review cards must reference an implementation card");
        }
        Card reviewedCard = getCard(effectiveReviewOfCardId);
        if (!serviceId.equals(reviewedCard.getServiceId())) {
            throw new IllegalArgumentException("Review reference must stay within service: " + effectiveReviewOfCardId);
        }
        if (effectiveReviewOfCardId.equals(currentCardId)) {
            throw new IllegalArgumentException("Card cannot review itself");
        }
        if (reviewedCard.getKind() != CardKind.TASK) {
            throw new IllegalArgumentException("Review cards must point to a TASK card");
        }
        return effectiveReviewOfCardId;
    }

    private List<String> normalizeCardReferences(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String cardId : cardIds) {
            String normalizedCardId = normalizeOptionalId(cardId);
            if (normalizedCardId != null) {
                normalized.add(normalizedCardId);
            }
        }
        return new ArrayList<>(normalized);
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
        if (card.getKind() == null) {
            card.setKind(CardKind.TASK);
        }
        if (card.getDependsOnCardIds() == null) {
            card.setDependsOnCardIds(new ArrayList<>());
        }
        if (card.getReviewerGolemIds() == null) {
            card.setReviewerGolemIds(new ArrayList<>());
        }
        if (card.getReviewStatus() == null) {
            card.setReviewStatus(CardReviewStatus.NOT_REQUIRED);
        }
        if (card.getAssignmentPolicy() == null) {
            card.setAssignmentPolicy(CardAssignmentPolicy.MANUAL);
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
