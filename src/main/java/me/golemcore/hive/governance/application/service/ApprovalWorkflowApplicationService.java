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

package me.golemcore.hive.governance.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.domain.model.ApprovalStatus;
import me.golemcore.hive.domain.model.ApprovalSubjectType;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.domain.model.OperatorUpdate;
import me.golemcore.hive.domain.model.SelfEvolvingPromotionApprovalContext;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.governance.application.ApprovalCommandRequest;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.PromotionApprovalRequest;
import me.golemcore.hive.governance.application.port.in.ApprovalWorkflowUseCase;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import me.golemcore.hive.governance.application.port.out.ApprovalCommandStatePort;
import me.golemcore.hive.governance.application.port.out.ApprovalGolemProfilePort;
import me.golemcore.hive.governance.application.port.out.ApprovalRepositoryPort;
import me.golemcore.hive.governance.application.port.out.ApprovalThreadPort;
import me.golemcore.hive.shared.approval.ApprovedCommandDispatchPort;

public class ApprovalWorkflowApplicationService implements ApprovalWorkflowUseCase {

    private final GovernanceSettings governanceSettings;
    private final AuditLogUseCase auditLogUseCase;
    private final NotificationUseCase notificationUseCase;
    private final OperatorUpdatePublisherPort operatorUpdatePublisherPort;
    private final ApprovalRepositoryPort approvalRepositoryPort;
    private final ApprovalCommandStatePort approvalCommandStatePort;
    private final ApprovedCommandDispatchPort approvedCommandDispatchPort;
    private final ApprovalThreadPort approvalThreadPort;
    private final ApprovalGolemProfilePort approvalGolemProfilePort;

    public ApprovalWorkflowApplicationService(
            GovernanceSettings governanceSettings,
            AuditLogUseCase auditLogUseCase,
            NotificationUseCase notificationUseCase,
            OperatorUpdatePublisherPort operatorUpdatePublisherPort,
            ApprovalRepositoryPort approvalRepositoryPort,
            ApprovalCommandStatePort approvalCommandStatePort,
            ApprovedCommandDispatchPort approvedCommandDispatchPort,
            ApprovalThreadPort approvalThreadPort,
            ApprovalGolemProfilePort approvalGolemProfilePort) {
        this.governanceSettings = governanceSettings;
        this.auditLogUseCase = auditLogUseCase;
        this.notificationUseCase = notificationUseCase;
        this.operatorUpdatePublisherPort = operatorUpdatePublisherPort;
        this.approvalRepositoryPort = approvalRepositoryPort;
        this.approvalCommandStatePort = approvalCommandStatePort;
        this.approvedCommandDispatchPort = approvedCommandDispatchPort;
        this.approvalThreadPort = approvalThreadPort;
        this.approvalGolemProfilePort = approvalGolemProfilePort;
    }

    @Override
    public ApprovalRiskLevel resolveApprovalRisk(ApprovalRiskLevel requestedRiskLevel, long estimatedCostMicros) {
        if (requestedRiskLevel != null) {
            return requestedRiskLevel;
        }
        if (estimatedCostMicros >= governanceSettings.highCostThresholdMicros()) {
            return ApprovalRiskLevel.HIGH_COST;
        }
        return null;
    }

    @Override
    public ApprovalRequest createApproval(ApprovalCommandRequest request, String actorId, String actorName) {
        Instant now = Instant.now();
        String golemDisplayName = approvalGolemProfilePort.resolveDisplayName(request.golemId());
        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr_" + UUID.randomUUID().toString().replace("-", ""))
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId(request.commandId())
                .runId(request.runId())
                .threadId(request.threadId())
                .boardId(request.boardId())
                .cardId(request.cardId())
                .golemId(request.golemId())
                .requestedByActorType("OPERATOR")
                .requestedByActorId(actorId)
                .requestedByActorName(actorName)
                .riskLevel(request.approvalRiskLevel())
                .reason(request.approvalReason())
                .estimatedCostMicros(request.estimatedCostMicros())
                .commandBody(request.commandBody())
                .status(ApprovalStatus.PENDING)
                .requestedAt(now)
                .updatedAt(now)
                .build();
        approvalRepositoryPort.save(approval);

        auditLogUseCase.record(AuditEvent.builder()
                .eventType("approval.requested")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .boardId(request.boardId())
                .cardId(request.cardId())
                .threadId(request.threadId())
                .golemId(request.golemId())
                .commandId(request.commandId())
                .runId(request.runId())
                .approvalId(approval.getId())
                .summary("Approval requested for " + approval.getRiskLevel())
                .details(approval.getReason()));

        if (notificationUseCase.isApprovalRequestedEnabled()) {
            notificationUseCase.create(NotificationEvent.builder()
                    .type("APPROVAL_REQUESTED")
                    .severity(NotificationSeverity.WARN)
                    .title("Approval requested")
                    .message(approval.getRiskLevel() + " command for " + golemDisplayName
                            + " requires operator approval")
                    .boardId(request.boardId())
                    .cardId(request.cardId())
                    .threadId(request.threadId())
                    .golemId(request.golemId())
                    .commandId(request.commandId())
                    .approvalId(approval.getId()));
        }

        approvalThreadPort.appendCommandStatusMessage(
                request.threadId(),
                request.commandId(),
                request.runId(),
                actorId,
                actorName,
                "Approval required before dispatch: " + approval.getRiskLevel(),
                now);
        publishApprovalUpdate("approval.requested", approval);

        return approval;
    }

    @Override
    public ApprovalRequest createPromotionApproval(PromotionApprovalRequest request, String actorId,
            String actorName) {
        if (request == null || request.candidateId() == null || request.candidateId().isBlank()) {
            throw new IllegalArgumentException("Candidate is required");
        }

        Instant now = Instant.now();
        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr_" + UUID.randomUUID().toString().replace("-", ""))
                .subjectType(ApprovalSubjectType.SELF_EVOLVING_PROMOTION)
                .runId(request.sourceRunIds() != null && !request.sourceRunIds().isEmpty()
                        ? request.sourceRunIds().getFirst()
                        : null)
                .golemId(request.golemId())
                .requestedByActorType("OPERATOR")
                .requestedByActorId(actorId)
                .requestedByActorName(actorName)
                .reason("Awaiting approval before rollout")
                .estimatedCostMicros(0)
                .commandBody(null)
                .status(ApprovalStatus.PENDING)
                .requestedAt(now)
                .updatedAt(now)
                .promotionContext(SelfEvolvingPromotionApprovalContext.builder()
                        .candidateId(request.candidateId())
                        .goal(request.goal())
                        .artifactType(request.artifactType())
                        .riskLevel(request.riskLevel())
                        .expectedImpact(request.expectedImpact())
                        .sourceRunIds(request.sourceRunIds() != null ? request.sourceRunIds() : List.of())
                        .build())
                .build();
        approvalRepositoryPort.save(approval);

        auditLogUseCase.record(AuditEvent.builder()
                .eventType("approval.requested")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .golemId(request.golemId())
                .runId(approval.getRunId())
                .approvalId(approval.getId())
                .summary("Promotion approval requested")
                .details(request.expectedImpact()));

        if (notificationUseCase.isApprovalRequestedEnabled()) {
            notificationUseCase.create(NotificationEvent.builder()
                    .type("APPROVAL_REQUESTED")
                    .severity(NotificationSeverity.WARN)
                    .title("Promotion approval requested")
                    .message(request.artifactType() + " candidate requires operator approval")
                    .golemId(request.golemId())
                    .approvalId(approval.getId()));
        }

        publishApprovalUpdate("approval.requested", approval);
        return approval;
    }

    @Override
    public List<ApprovalRequest> listApprovals(String status, String boardId, String cardId, String golemId) {
        List<ApprovalRequest> approvals = new ArrayList<>();
        for (ApprovalRequest approval : approvalRepositoryPort.findAll()) {
            if (status != null && !status.isBlank() && !approval.getStatus().name().equalsIgnoreCase(status)) {
                continue;
            }
            if (boardId != null && !boardId.isBlank() && !boardId.equals(approval.getBoardId())) {
                continue;
            }
            if (cardId != null && !cardId.isBlank() && !cardId.equals(approval.getCardId())) {
                continue;
            }
            if (golemId != null && !golemId.isBlank() && !golemId.equals(approval.getGolemId())) {
                continue;
            }
            approvals.add(approval);
        }
        approvals.sort(
                Comparator.comparing(ApprovalRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ApprovalRequest::getId, Comparator.reverseOrder()));
        return approvals;
    }

    @Override
    public ApprovalRequest getApproval(String approvalId) {
        return approvalRepositoryPort.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval: " + approvalId));
    }

    @Override
    public ApprovalRequest approve(String approvalId, String actorId, String actorName, String comment) {
        ApprovalRequest approval = getApproval(approvalId);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already resolved");
        }
        Instant now = Instant.now();
        finalizeApprovalDecision(approval, ApprovalStatus.APPROVED, actorId, actorName, comment, now);
        publishApprovalUpdate("approval.approved", approval);
        boolean synchronizedDecision = synchronizeDecisionState(approval, actorId, actorName, comment, now);
        if (synchronizedDecision) {
            triggerApprovedDispatch(approval, actorId, actorName, comment);
        }

        auditLogUseCase.record(AuditEvent.builder()
                .eventType("approval.approved")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .boardId(approval.getBoardId())
                .cardId(approval.getCardId())
                .threadId(approval.getThreadId())
                .golemId(approval.getGolemId())
                .commandId(approval.getCommandId())
                .runId(approval.getRunId())
                .approvalId(approval.getId())
                .summary(approval.getSubjectType() == ApprovalSubjectType.SELF_EVOLVING_PROMOTION
                        ? "Promotion approval granted"
                        : "Approval granted")
                .details(comment));
        return approval;
    }

    @Override
    public ApprovalRequest reject(String approvalId, String actorId, String actorName, String comment) {
        ApprovalRequest approval = getApproval(approvalId);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already resolved");
        }
        Instant now = Instant.now();
        finalizeApprovalDecision(approval, ApprovalStatus.REJECTED, actorId, actorName, comment, now);
        publishApprovalUpdate("approval.rejected", approval);
        synchronizeDecisionState(approval, actorId, actorName, comment, now);

        auditLogUseCase.record(AuditEvent.builder()
                .eventType("approval.rejected")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .boardId(approval.getBoardId())
                .cardId(approval.getCardId())
                .threadId(approval.getThreadId())
                .golemId(approval.getGolemId())
                .commandId(approval.getCommandId())
                .runId(approval.getRunId())
                .approvalId(approval.getId())
                .summary(approval.getSubjectType() == ApprovalSubjectType.SELF_EVOLVING_PROMOTION
                        ? "Promotion approval rejected"
                        : "Approval rejected")
                .details(comment));
        return approval;
    }

    private void finalizeApprovalDecision(
            ApprovalRequest approval,
            ApprovalStatus status,
            String actorId,
            String actorName,
            String comment,
            Instant decidedAt) {
        approval.setStatus(status);
        approval.setUpdatedAt(decidedAt);
        approval.setDecidedAt(decidedAt);
        approval.setDecidedByActorType("OPERATOR");
        approval.setDecidedByActorId(actorId);
        approval.setDecidedByActorName(actorName);
        approval.setDecisionComment(comment);
        approvalRepositoryPort.save(approval);
    }

    private boolean synchronizeDecisionState(
            ApprovalRequest approval,
            String actorId,
            String actorName,
            String comment,
            Instant decidedAt) {
        if (approval.getSubjectType() != ApprovalSubjectType.COMMAND) {
            return true;
        }
        try {
            if (approval.getStatus() == ApprovalStatus.APPROVED) {
                approvalCommandStatePort.markCommandQueued(
                        approval.getCommandId(),
                        "Approved and awaiting dispatch",
                        decidedAt);
                approvalCommandStatePort.markRunQueued(approval.getRunId(), decidedAt);
                appendDecisionMessage(approval, "system", "Hive", "Approval granted by " + actorName, decidedAt);
            } else if (approval.getStatus() == ApprovalStatus.REJECTED) {
                approvalCommandStatePort.markCommandRejected(
                        approval.getCommandId(),
                        firstNonBlank(comment, "Rejected by operator"),
                        decidedAt,
                        decidedAt);
                approvalCommandStatePort.markRunRejected(approval.getRunId(), decidedAt, decidedAt);
                appendDecisionMessage(approval, "system", "Hive", "Approval rejected by " + actorName, decidedAt);
            }
            return true;
        } catch (RuntimeException exception) {
            auditLogUseCase.record(AuditEvent.builder()
                    .eventType("approval.state_sync_deferred")
                    .severity("WARN")
                    .actorType("OPERATOR")
                    .actorId(actorId)
                    .actorName(actorName)
                    .targetType("APPROVAL")
                    .targetId(approval.getId())
                    .boardId(approval.getBoardId())
                    .cardId(approval.getCardId())
                    .threadId(approval.getThreadId())
                    .golemId(approval.getGolemId())
                    .commandId(approval.getCommandId())
                    .runId(approval.getRunId())
                    .approvalId(approval.getId())
                    .summary("Approval decision persisted but execution state sync failed")
                    .details(firstNonBlank(comment, exception.getMessage())));
            return false;
        }
    }

    private void triggerApprovedDispatch(ApprovalRequest approval, String actorId, String actorName, String comment) {
        if (approval.getSubjectType() != ApprovalSubjectType.COMMAND) {
            return;
        }
        try {
            approvedCommandDispatchPort.dispatchApprovedCommand(approval.getCommandId());
        } catch (RuntimeException exception) {
            auditLogUseCase.record(AuditEvent.builder()
                    .eventType("approval.dispatch_deferred")
                    .severity("WARN")
                    .actorType("OPERATOR")
                    .actorId(actorId)
                    .actorName(actorName)
                    .targetType("APPROVAL")
                    .targetId(approval.getId())
                    .boardId(approval.getBoardId())
                    .cardId(approval.getCardId())
                    .threadId(approval.getThreadId())
                    .golemId(approval.getGolemId())
                    .commandId(approval.getCommandId())
                    .runId(approval.getRunId())
                    .approvalId(approval.getId())
                    .summary("Approval granted but dispatch trigger failed")
                    .details(firstNonBlank(comment, exception.getMessage())));
        }
    }

    private void publishApprovalUpdate(String eventType, ApprovalRequest approval) {
        if (approval == null) {
            return;
        }
        operatorUpdatePublisherPort.publish(OperatorUpdate.builder()
                .eventType(eventType)
                .commandId(approval.getCommandId())
                .runId(approval.getRunId())
                .cardId(approval.getCardId())
                .threadId(approval.getThreadId())
                .kinds(approval.getSubjectType() == ApprovalSubjectType.SELF_EVOLVING_PROMOTION
                        ? java.util.List.of("approval", "selfevolving", "promotion")
                        : java.util.List.of("approval", "command"))
                .createdAt(approval.getUpdatedAt() != null ? approval.getUpdatedAt() : approval.getRequestedAt())
                .build());
    }

    private void appendDecisionMessage(
            ApprovalRequest approval,
            String actorId,
            String actorName,
            String message,
            Instant createdAt) {
        approvalThreadPort.appendCommandStatusMessage(
                approval.getThreadId(),
                approval.getCommandId(),
                approval.getRunId(),
                actorId,
                actorName,
                message,
                createdAt);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
