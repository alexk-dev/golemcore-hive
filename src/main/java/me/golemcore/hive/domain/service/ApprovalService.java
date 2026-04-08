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
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.domain.model.ApprovalStatus;
import me.golemcore.hive.domain.model.ApprovalSubjectType;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.RunStatus;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingPromotionApprovalContext;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final String APPROVALS_DIR = "approvals";
    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final HiveProperties properties;
    private final ThreadService threadService;
    private final GolemRegistryService golemRegistryService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final OperatorUpdatesService operatorUpdatesService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ApprovalRiskLevel resolveApprovalRisk(ApprovalRiskLevel requestedRiskLevel, long estimatedCostMicros) {
        if (requestedRiskLevel != null) {
            return requestedRiskLevel;
        }
        if (estimatedCostMicros >= properties.getGovernance().getApprovals().getHighCostThresholdMicros()) {
            return ApprovalRiskLevel.HIGH_COST;
        }
        return null;
    }

    public ApprovalRequest createApproval(CommandRecord command,
            RunProjection run,
            Card card,
            String actorId,
            String actorName) {
        Instant now = Instant.now();
        String golemDisplayName = resolveGolemDisplayName(command.getGolemId());
        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr_" + UUID.randomUUID().toString().replace("-", ""))
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId(command.getId())
                .runId(run.getId())
                .threadId(command.getThreadId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .golemId(command.getGolemId())
                .requestedByActorType("OPERATOR")
                .requestedByActorId(actorId)
                .requestedByActorName(actorName)
                .riskLevel(command.getApprovalRiskLevel())
                .reason(command.getApprovalReason())
                .estimatedCostMicros(command.getEstimatedCostMicros())
                .commandBody(command.getBody())
                .status(ApprovalStatus.PENDING)
                .requestedAt(now)
                .updatedAt(now)
                .build();
        saveApproval(approval);

        auditService.record(AuditEvent.builder()
                .eventType("approval.requested")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .threadId(command.getThreadId())
                .golemId(command.getGolemId())
                .commandId(command.getId())
                .runId(run.getId())
                .approvalId(approval.getId())
                .summary("Approval requested for " + approval.getRiskLevel())
                .details(approval.getReason()));

        if (notificationService.isApprovalRequestedEnabled()) {
            notificationService.create(NotificationEvent.builder()
                    .type("APPROVAL_REQUESTED")
                    .severity(NotificationSeverity.WARN)
                    .title("Approval requested")
                    .message(approval.getRiskLevel() + " command for " + golemDisplayName
                            + " requires operator approval")
                    .boardId(card.getBoardId())
                    .cardId(card.getId())
                    .threadId(command.getThreadId())
                    .golemId(command.getGolemId())
                    .commandId(command.getId())
                    .approvalId(approval.getId()));
        }

        ThreadRecord thread = threadService.getThread(command.getThreadId());
        threadService.appendMessage(thread, command.getId(), run.getId(), null,
                ThreadMessageType.COMMAND_STATUS, ThreadParticipantType.SYSTEM, actorId, actorName,
                "Approval required before dispatch: " + approval.getRiskLevel(), now);
        operatorUpdatesService.publishApprovalUpdate("approval.requested", approval);

        return approval;
    }

    public ApprovalRequest createPromotionApproval(
            SelfEvolvingCandidateProjection candidate,
            String actorId,
            String actorName) {
        if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
            throw new IllegalArgumentException("Candidate is required");
        }

        Instant now = Instant.now();
        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr_" + UUID.randomUUID().toString().replace("-", ""))
                .subjectType(ApprovalSubjectType.SELF_EVOLVING_PROMOTION)
                .runId(candidate.getSourceRunIds() != null && !candidate.getSourceRunIds().isEmpty()
                        ? candidate.getSourceRunIds().getFirst()
                        : null)
                .golemId(candidate.getGolemId())
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
                        .candidateId(candidate.getId())
                        .goal(candidate.getGoal())
                        .artifactType(candidate.getArtifactType())
                        .riskLevel(candidate.getRiskLevel())
                        .expectedImpact(candidate.getExpectedImpact())
                        .sourceRunIds(candidate.getSourceRunIds() != null ? candidate.getSourceRunIds() : List.of())
                        .build())
                .build();
        saveApproval(approval);

        auditService.record(AuditEvent.builder()
                .eventType("approval.requested")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("APPROVAL")
                .targetId(approval.getId())
                .golemId(candidate.getGolemId())
                .runId(approval.getRunId())
                .approvalId(approval.getId())
                .summary("Promotion approval requested")
                .details(candidate.getExpectedImpact()));

        if (notificationService.isApprovalRequestedEnabled()) {
            notificationService.create(NotificationEvent.builder()
                    .type("APPROVAL_REQUESTED")
                    .severity(NotificationSeverity.WARN)
                    .title("Promotion approval requested")
                    .message(candidate.getArtifactType() + " candidate requires operator approval")
                    .golemId(candidate.getGolemId())
                    .approvalId(approval.getId()));
        }

        operatorUpdatesService.publishApprovalUpdate("approval.requested", approval);
        return approval;
    }

    public List<ApprovalRequest> listApprovals(String status, String boardId, String cardId, String golemId) {
        List<ApprovalRequest> approvals = new ArrayList<>();
        for (String path : storagePort.listObjects(APPROVALS_DIR, "")) {
            Optional<ApprovalRequest> approvalOptional = loadApproval(path);
            if (approvalOptional.isEmpty()) {
                continue;
            }
            ApprovalRequest approval = approvalOptional.get();
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

    public ApprovalRequest getApproval(String approvalId) {
        return loadApproval(approvalId + ".json")
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval: " + approvalId));
    }

    public ApprovalRequest approve(String approvalId, String actorId, String actorName, String comment) {
        ApprovalRequest approval = getApproval(approvalId);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already resolved");
        }
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setUpdatedAt(now);
        approval.setDecidedAt(now);
        approval.setDecidedByActorType("OPERATOR");
        approval.setDecidedByActorId(actorId);
        approval.setDecidedByActorName(actorName);
        approval.setDecisionComment(comment);
        saveApproval(approval);
        operatorUpdatesService.publishApprovalUpdate("approval.approved", approval);

        if (approval.getSubjectType() == ApprovalSubjectType.COMMAND) {
            CommandRecord command = loadCommand(approval.getCommandId());
            RunProjection run = loadRun(approval.getRunId());
            command.setStatus(CommandStatus.QUEUED);
            command.setQueueReason("Approved and awaiting dispatch");
            command.setUpdatedAt(now);
            run.setStatus(RunStatus.QUEUED);
            run.setUpdatedAt(now);
            saveCommand(command);
            saveRun(run);
            appendDecisionMessage(command, run, "Approval granted by " + actorName, now);
            applicationEventPublisher
                    .publishEvent(new ApprovalApprovedEvent(approval.getId(), approval.getCommandId()));
        }

        auditService.record(AuditEvent.builder()
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

    public ApprovalRequest reject(String approvalId, String actorId, String actorName, String comment) {
        ApprovalRequest approval = getApproval(approvalId);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already resolved");
        }
        Instant now = Instant.now();
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setUpdatedAt(now);
        approval.setDecidedAt(now);
        approval.setDecidedByActorType("OPERATOR");
        approval.setDecidedByActorId(actorId);
        approval.setDecidedByActorName(actorName);
        approval.setDecisionComment(comment);
        saveApproval(approval);
        operatorUpdatesService.publishApprovalUpdate("approval.rejected", approval);

        if (approval.getSubjectType() == ApprovalSubjectType.COMMAND) {
            CommandRecord command = loadCommand(approval.getCommandId());
            RunProjection run = loadRun(approval.getRunId());
            command.setStatus(CommandStatus.REJECTED);
            command.setQueueReason(comment != null && !comment.isBlank() ? comment : "Rejected by operator");
            command.setUpdatedAt(now);
            command.setCompletedAt(now);
            run.setStatus(RunStatus.REJECTED);
            run.setUpdatedAt(now);
            run.setCompletedAt(now);
            saveCommand(command);
            saveRun(run);
            appendDecisionMessage(command, run, "Approval rejected by " + actorName, now);
        }

        auditService.record(AuditEvent.builder()
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

    private void appendDecisionMessage(CommandRecord command, RunProjection run, String message, Instant now) {
        ThreadRecord thread = threadService.getThread(command.getThreadId());
        threadService.appendMessage(thread, command.getId(), run.getId(), null,
                ThreadMessageType.COMMAND_STATUS, ThreadParticipantType.SYSTEM,
                "system", "Hive", message, now);
    }

    private String resolveGolemDisplayName(String golemId) {
        if (golemId == null || golemId.isBlank()) {
            return "golem";
        }
        return golemRegistryService.findGolem(golemId)
                .map(Golem::getDisplayName)
                .filter(displayName -> displayName != null && !displayName.isBlank())
                .orElse(golemId);
    }

    private Optional<ApprovalRequest> loadApproval(String path) {
        String content = storagePort.getText(APPROVALS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, ApprovalRequest.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize approval " + path, exception);
        }
    }

    private CommandRecord loadCommand(String commandId) {
        String content = storagePort.getText(COMMANDS_DIR, commandId + ".json");
        if (content == null) {
            throw new IllegalArgumentException("Unknown command: " + commandId);
        }
        try {
            return objectMapper.readValue(content, CommandRecord.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize command " + commandId, exception);
        }
    }

    private RunProjection loadRun(String runId) {
        String content = storagePort.getText(RUNS_DIR, runId + ".json");
        if (content == null) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        try {
            return objectMapper.readValue(content, RunProjection.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize run " + runId, exception);
        }
    }

    private void saveApproval(ApprovalRequest approval) {
        try {
            storagePort.putTextAtomic(APPROVALS_DIR, approval.getId() + ".json",
                    objectMapper.writeValueAsString(approval));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize approval " + approval.getId(), exception);
        }
    }

    private void saveCommand(CommandRecord command) {
        try {
            storagePort.putTextAtomic(COMMANDS_DIR, command.getId() + ".json",
                    objectMapper.writeValueAsString(command));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize command " + command.getId(), exception);
        }
    }

    private void saveRun(RunProjection run) {
        try {
            storagePort.putTextAtomic(RUNS_DIR, run.getId() + ".json", objectMapper.writeValueAsString(run));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize run " + run.getId(), exception);
        }
    }
}
