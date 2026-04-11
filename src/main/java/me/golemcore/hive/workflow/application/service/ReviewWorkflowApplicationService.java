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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardReviewDecision;
import me.golemcore.hive.domain.model.CardReviewStatus;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ReviewWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;

public class ReviewWorkflowApplicationService implements ReviewWorkflowUseCase {

    private final CardRepository cardRepository;
    private final CardWorkflowUseCase cardWorkflowUseCase;
    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final TeamWorkflowUseCase teamWorkflowUseCase;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final ThreadWorkflowUseCase threadWorkflowUseCase;
    private final WorkflowAuditPort workflowAuditPort;

    public ReviewWorkflowApplicationService(
            CardRepository cardRepository,
            CardWorkflowUseCase cardWorkflowUseCase,
            BoardWorkflowUseCase boardWorkflowUseCase,
            TeamWorkflowUseCase teamWorkflowUseCase,
            GolemDirectoryUseCase golemDirectoryUseCase,
            ThreadWorkflowUseCase threadWorkflowUseCase,
            WorkflowAuditPort workflowAuditPort) {
        this.cardRepository = cardRepository;
        this.cardWorkflowUseCase = cardWorkflowUseCase;
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.teamWorkflowUseCase = teamWorkflowUseCase;
        this.golemDirectoryUseCase = golemDirectoryUseCase;
        this.threadWorkflowUseCase = threadWorkflowUseCase;
        this.workflowAuditPort = workflowAuditPort;
    }

    @Override
    public Card requestReview(
            String cardId,
            List<String> reviewerGolemIds,
            String reviewerTeamId,
            Integer requiredReviewCount,
            String actorId,
            String actorName) {
        Card implementationCard = cardWorkflowUseCase.getCard(cardId);
        List<String> normalizedReviewerIds = normalizeIds(reviewerGolemIds);
        String normalizedReviewerTeamId = normalizeOptionalId(reviewerTeamId);
        int effectiveReviewCount = requiredReviewCount != null && requiredReviewCount > 0 ? requiredReviewCount : 1;

        if (implementationCard.getKind() != CardKind.TASK) {
            throw new IllegalArgumentException("Review requirements can only target implementation task cards");
        }
        if (normalizedReviewerIds.isEmpty() && normalizedReviewerTeamId == null) {
            throw new IllegalArgumentException("At least one reviewer must be provided");
        }
        if (implementationCard.getAssigneeGolemId() != null
                && normalizedReviewerIds.contains(implementationCard.getAssigneeGolemId())) {
            throw new IllegalArgumentException("Reviewer cannot be the implementation assignee");
        }
        Set<String> eligibleReviewerIds = validateReviewers(
                normalizedReviewerIds,
                normalizedReviewerTeamId,
                implementationCard.getAssigneeGolemId());
        if (eligibleReviewerIds.size() < effectiveReviewCount) {
            throw new IllegalArgumentException("Not enough eligible reviewers for requiredReviewCount");
        }

        implementationCard.setReviewerGolemIds(new ArrayList<>(normalizedReviewerIds));
        implementationCard.setReviewerTeamId(normalizedReviewerTeamId);
        implementationCard.setRequiredReviewCount(effectiveReviewCount);
        implementationCard.setReviewStatus(CardReviewStatus.REQUIRED);
        implementationCard.setUpdatedAt(Instant.now());
        cardRepository.save(implementationCard);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("card.review_requested")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("CARD")
                .targetId(implementationCard.getId())
                .boardId(implementationCard.getBoardId())
                .cardId(implementationCard.getId())
                .threadId(implementationCard.getThreadId())
                .golemId(implementationCard.getAssigneeGolemId())
                .summary("Review requested")
                .details(buildReviewRequestDetails(implementationCard)));
        return implementationCard;
    }

    @Override
    public Card activateReviewForCompletedWork(String implementationCardId, String actorId, String actorName) {
        Card implementationCard = cardWorkflowUseCase.getCard(implementationCardId);
        if (implementationCard.getKind() != CardKind.TASK) {
            return null;
        }
        if (implementationCard.getReviewStatus() == null
                || implementationCard.getReviewStatus() == CardReviewStatus.NOT_REQUIRED
                || implementationCard.getReviewStatus() == CardReviewStatus.APPROVED
                || implementationCard.getReviewStatus() == CardReviewStatus.CHANGES_REQUESTED) {
            return null;
        }
        Optional<Card> existingReviewCard = findActiveReviewCard(implementationCard.getId());
        if (existingReviewCard.isPresent()) {
            return existingReviewCard.get();
        }

        String reviewerGolemId = resolveReviewerGolemId(implementationCard);
        Board board = boardWorkflowUseCase.getBoard(implementationCard.getBoardId());
        String reviewColumnId = resolveColumnId(board, "review", implementationCard.getColumnId());
        String reviewPrompt = buildReviewPrompt(implementationCard);
        Card reviewCard = cardWorkflowUseCase.createCard(new CardCreateCommand(
                implementationCard.getServiceId(),
                "Review: " + implementationCard.getTitle(),
                "Review task for " + implementationCard.getTitle(),
                reviewPrompt,
                reviewColumnId,
                implementationCard.getTeamId(),
                implementationCard.getObjectiveId(),
                reviewerGolemId,
                CardAssignmentPolicy.MANUAL,
                false,
                CardKind.REVIEW,
                null,
                null,
                implementationCard.getId(),
                null), actorId, actorName);
        reviewCard.setReviewStatus(CardReviewStatus.IN_REVIEW);
        reviewCard.setReviewerTeamId(implementationCard.getReviewerTeamId());
        reviewCard.setReviewerGolemIds(new ArrayList<>(implementationCard.getReviewerGolemIds()));
        reviewCard.setRequiredReviewCount(implementationCard.getRequiredReviewCount());
        reviewCard.setUpdatedAt(Instant.now());
        cardRepository.save(reviewCard);

        implementationCard.setReviewStatus(CardReviewStatus.IN_REVIEW);
        implementationCard.setUpdatedAt(reviewCard.getUpdatedAt());
        cardRepository.save(implementationCard);

        workflowAuditPort.record(AuditEvent.builder()
                .eventType("card.review_activated")
                .severity("INFO")
                .actorType("SYSTEM")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("CARD")
                .targetId(reviewCard.getId())
                .boardId(reviewCard.getBoardId())
                .cardId(reviewCard.getId())
                .threadId(reviewCard.getThreadId())
                .golemId(reviewCard.getAssigneeGolemId())
                .summary("Review task activated")
                .details(implementationCard.getId()));
        return reviewCard;
    }

    @Override
    public Card applyDecision(
            String reviewCardId,
            CardReviewDecision decision,
            String summary,
            String details,
            String actorId,
            String actorName) {
        if (decision == null) {
            throw new IllegalArgumentException("Review decision is required");
        }
        Card reviewCard = cardWorkflowUseCase.getCard(reviewCardId);
        if (reviewCard.getKind() != CardKind.REVIEW) {
            throw new IllegalArgumentException("Review decision must target a review card");
        }
        if (reviewCard.getReviewOfCardId() == null || reviewCard.getReviewOfCardId().isBlank()) {
            throw new IllegalArgumentException("Review card is not linked to an implementation card");
        }

        Card implementationCard = cardWorkflowUseCase.getCard(reviewCard.getReviewOfCardId());
        if (implementationCard.getKind() != CardKind.TASK) {
            throw new IllegalArgumentException("Review card must point to an implementation task card");
        }
        CardReviewStatus reviewCardStatus = decision == CardReviewDecision.APPROVED
                ? CardReviewStatus.APPROVED
                : CardReviewStatus.CHANGES_REQUESTED;
        reviewCard.setReviewStatus(reviewCardStatus);
        reviewCard.setUpdatedAt(Instant.now());
        cardRepository.save(reviewCard);

        boolean approvalRequirementsMet = decision == CardReviewDecision.APPROVED
                && approvedReviewCount(implementationCard.getId()) >= requiredReviewCount(implementationCard,
                        reviewCard);
        CardReviewStatus implementationStatus = approvalRequirementsMet ? CardReviewStatus.APPROVED
                : (decision == CardReviewDecision.APPROVED ? CardReviewStatus.IN_REVIEW
                        : CardReviewStatus.CHANGES_REQUESTED);
        implementationCard.setReviewStatus(implementationStatus);
        implementationCard.setUpdatedAt(reviewCard.getUpdatedAt());
        cardRepository.save(implementationCard);

        ThreadRecord implementationThread = threadWorkflowUseCase.getThreadByCardId(implementationCard.getId());
        threadWorkflowUseCase.appendMessage(
                implementationThread,
                null,
                null,
                null,
                ThreadMessageType.NOTE,
                ThreadParticipantType.GOLEM,
                actorId,
                actorName,
                buildDecisionMessage(decision, summary, details),
                reviewCard.getUpdatedAt());

        Board board = boardWorkflowUseCase.getBoard(implementationCard.getBoardId());
        if (decision == CardReviewDecision.APPROVED && implementationStatus == CardReviewStatus.APPROVED) {
            moveIfNeeded(implementationCard, resolveColumnId(board, "done", implementationCard.getColumnId()), actorId,
                    actorName, summary);
            moveIfNeeded(reviewCard, resolveColumnId(board, "done", reviewCard.getColumnId()), actorId, actorName,
                    summary);
        } else if (decision == CardReviewDecision.APPROVED) {
            moveIfNeeded(reviewCard, resolveColumnId(board, "done", reviewCard.getColumnId()), actorId, actorName,
                    summary);
        } else {
            moveIfNeeded(implementationCard, resolveColumnId(board, "in_progress", implementationCard.getColumnId()),
                    actorId, actorName, summary);
            moveIfNeeded(reviewCard, resolveColumnId(board, "done", reviewCard.getColumnId()), actorId, actorName,
                    summary);
        }

        workflowAuditPort.record(AuditEvent.builder()
                .eventType("card.review_decision")
                .severity("INFO")
                .actorType("GOLEM")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("CARD")
                .targetId(reviewCard.getId())
                .boardId(reviewCard.getBoardId())
                .cardId(reviewCard.getId())
                .threadId(reviewCard.getThreadId())
                .golemId(reviewCard.getAssigneeGolemId())
                .summary(decision.name())
                .details(buildDecisionMessage(decision, summary, details)));
        return reviewCard;
    }

    private Optional<Card> findActiveReviewCard(String implementationCardId) {
        return cardRepository.list().stream()
                .map(this::normalize)
                .filter(card -> card.getKind() == CardKind.REVIEW)
                .filter(card -> implementationCardId.equals(card.getReviewOfCardId()))
                .filter(card -> card.getReviewStatus() == CardReviewStatus.IN_REVIEW
                        || card.getReviewStatus() == CardReviewStatus.REQUIRED)
                .findFirst();
    }

    private List<String> normalizeIds(List<String> reviewerGolemIds) {
        if (reviewerGolemIds == null || reviewerGolemIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String reviewerGolemId : reviewerGolemIds) {
            String normalizedReviewerId = normalizeOptionalId(reviewerGolemId);
            if (normalizedReviewerId != null) {
                normalized.add(normalizedReviewerId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String resolveReviewerGolemId(Card implementationCard) {
        Set<String> approvedReviewerIds = approvedReviewerIds(implementationCard.getId());
        for (String reviewerGolemId : implementationCard.getReviewerGolemIds()) {
            if (reviewerGolemId == null || reviewerGolemId.isBlank()) {
                continue;
            }
            if (reviewerGolemId.equals(implementationCard.getAssigneeGolemId())) {
                continue;
            }
            if (approvedReviewerIds.contains(reviewerGolemId)) {
                continue;
            }
            if (golemDirectoryUseCase.findGolem(reviewerGolemId).isPresent()) {
                return reviewerGolemId;
            }
        }

        String reviewerTeamId = normalizeOptionalId(implementationCard.getReviewerTeamId());
        if (reviewerTeamId != null) {
            Team reviewerTeam = teamWorkflowUseCase.getTeam(reviewerTeamId);
            if (reviewerTeam.getGolemIds() != null) {
                for (String teamGolemId : reviewerTeam.getGolemIds()) {
                    if (teamGolemId == null || teamGolemId.isBlank()) {
                        continue;
                    }
                    if (teamGolemId.equals(implementationCard.getAssigneeGolemId())) {
                        continue;
                    }
                    if (approvedReviewerIds.contains(teamGolemId)) {
                        continue;
                    }
                    if (golemDirectoryUseCase.findGolem(teamGolemId).isPresent()) {
                        return teamGolemId;
                    }
                }
            }
        }

        throw new IllegalArgumentException("No eligible reviewer available for card: " + implementationCard.getId());
    }

    private Set<String> validateReviewers(List<String> reviewerGolemIds, String reviewerTeamId,
            String assigneeGolemId) {
        Set<String> eligibleReviewerIds = new LinkedHashSet<>();
        for (String reviewerGolemId : reviewerGolemIds) {
            if (reviewerGolemId == null || reviewerGolemId.isBlank()) {
                continue;
            }
            golemDirectoryUseCase.findGolem(reviewerGolemId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown reviewer golem: " + reviewerGolemId));
            if (assigneeGolemId != null && assigneeGolemId.equals(reviewerGolemId)) {
                throw new IllegalArgumentException("Reviewer cannot be the implementation assignee");
            }
            eligibleReviewerIds.add(reviewerGolemId);
        }
        String normalizedReviewerTeamId = normalizeOptionalId(reviewerTeamId);
        if (normalizedReviewerTeamId != null) {
            Team team = teamWorkflowUseCase.getTeam(normalizedReviewerTeamId);
            boolean hasEligibleReviewer = false;
            if (team.getGolemIds() != null) {
                for (String teamGolemId : team.getGolemIds()) {
                    if (teamGolemId == null || teamGolemId.isBlank()) {
                        continue;
                    }
                    if (assigneeGolemId != null && assigneeGolemId.equals(teamGolemId)) {
                        continue;
                    }
                    if (golemDirectoryUseCase.findGolem(teamGolemId).isPresent()) {
                        hasEligibleReviewer = true;
                        eligibleReviewerIds.add(teamGolemId);
                    }
                }
            }
            if (!hasEligibleReviewer) {
                throw new IllegalArgumentException("Reviewer team has no eligible golems");
            }
        }
        return eligibleReviewerIds;
    }

    private int requiredReviewCount(Card implementationCard, Card reviewCard) {
        if (implementationCard.getRequiredReviewCount() > 0) {
            return implementationCard.getRequiredReviewCount();
        }
        if (reviewCard.getRequiredReviewCount() > 0) {
            return reviewCard.getRequiredReviewCount();
        }
        return 1;
    }

    private int approvedReviewCount(String implementationCardId) {
        return approvedReviewerIds(implementationCardId).size();
    }

    private Set<String> approvedReviewerIds(String implementationCardId) {
        Set<String> reviewerIds = new LinkedHashSet<>();
        for (Card storedCard : cardRepository.list()) {
            Card card = normalize(storedCard);
            if (card.getKind() != CardKind.REVIEW) {
                continue;
            }
            if (!implementationCardId.equals(card.getReviewOfCardId())) {
                continue;
            }
            if (card.getReviewStatus() != CardReviewStatus.APPROVED) {
                continue;
            }
            String reviewerId = normalizeOptionalId(card.getAssigneeGolemId());
            if (reviewerId != null) {
                reviewerIds.add(reviewerId);
            }
        }
        return reviewerIds;
    }

    private String buildReviewPrompt(Card implementationCard) {
        StringBuilder builder = new StringBuilder();
        builder.append("Review implementation: ").append(implementationCard.getTitle());
        if (implementationCard.getDescription() != null && !implementationCard.getDescription().isBlank()) {
            builder.append("\n\nDescription:\n").append(implementationCard.getDescription());
        }
        if (implementationCard.getPrompt() != null && !implementationCard.getPrompt().isBlank()) {
            builder.append("\n\nOriginal prompt:\n").append(implementationCard.getPrompt());
        }
        return builder.toString();
    }

    private String buildReviewRequestDetails(Card implementationCard) {
        return "reviewers=" + implementationCard.getReviewerGolemIds()
                + ", reviewerTeamId=" + implementationCard.getReviewerTeamId()
                + ", requiredReviewCount=" + implementationCard.getRequiredReviewCount();
    }

    private String buildDecisionMessage(CardReviewDecision decision, String summary, String details) {
        StringBuilder builder = new StringBuilder();
        builder.append(decision.name());
        if (summary != null && !summary.isBlank()) {
            builder.append(": ").append(summary);
        }
        if (details != null && !details.isBlank()) {
            builder.append("\n\n").append(details);
        }
        return builder.toString();
    }

    private String resolveColumnId(Board board, String preferredColumnId, String fallbackColumnId) {
        if (board.getFlow() != null && board.getFlow().getColumns() != null) {
            for (BoardColumn column : board.getFlow().getColumns()) {
                if (preferredColumnId.equals(column.getId())) {
                    return preferredColumnId;
                }
            }
        }
        return fallbackColumnId;
    }

    private void moveIfNeeded(Card card, String targetColumnId, String actorId, String actorName, String summary) {
        if (targetColumnId == null || targetColumnId.isBlank() || targetColumnId.equals(card.getColumnId())) {
            return;
        }
        cardWorkflowUseCase.moveCard(
                card.getId(),
                targetColumnId,
                null,
                CardTransitionOrigin.BOARD_AUTOMATION,
                actorId,
                actorName,
                summary != null ? summary : card.getTitle());
    }

    private Card normalize(Card card) {
        if (card == null) {
            return null;
        }
        if (card.getKind() == null) {
            card.setKind(CardKind.TASK);
        }
        if (card.getReviewerGolemIds() == null) {
            card.setReviewerGolemIds(new ArrayList<>());
        }
        if (card.getReviewStatus() == null) {
            card.setReviewStatus(CardReviewStatus.NOT_REQUIRED);
        }
        return card;
    }

    private String normalizeOptionalId(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
