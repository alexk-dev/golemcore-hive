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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.domain.model.ApprovalStatus;
import me.golemcore.hive.domain.model.ApprovalSubjectType;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.PromotionApprovalRequest;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import me.golemcore.hive.governance.application.port.out.ApprovalCommandStatePort;
import me.golemcore.hive.governance.application.port.out.ApprovalGolemProfilePort;
import me.golemcore.hive.governance.application.port.out.ApprovalRepositoryPort;
import me.golemcore.hive.governance.application.port.out.ApprovalThreadPort;
import me.golemcore.hive.shared.approval.ApprovedCommandDispatchPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ApprovalWorkflowApplicationServiceTest {

    private static final GovernanceSettings GOVERNANCE_SETTINGS = new GovernanceSettings(
            false,
            "/tmp/hive",
            false,
            5_000_000L,
            30,
            30,
            30,
            true,
            true,
            true,
            true);

    @Test
    void shouldCreatePromotionApprovalWithoutCommandRecord() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        when(notificationUseCase.isApprovalRequestedEnabled()).thenReturn(false);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        PromotionApprovalRequest request = new PromotionApprovalRequest(
                "candidate-1",
                "golem-1",
                "fix",
                "skill",
                "medium",
                "Reduce routing failures",
                java.util.List.of("run-1"));

        ApprovalRequest approval = approvalWorkflowApplicationService.createPromotionApproval(
                request,
                "operator-1",
                "Hive Admin");

        assertNotNull(approval.getId());
        assertEquals(ApprovalSubjectType.SELF_EVOLVING_PROMOTION, approval.getSubjectType());
        assertEquals(ApprovalStatus.PENDING, approval.getStatus());
        assertNull(approval.getCommandId());
        assertEquals("candidate-1", approval.getPromotionContext().getCandidateId());
        assertEquals("skill", approval.getPromotionContext().getArtifactType());
        verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        verify(auditLogUseCase).record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
        verify(operatorUpdatePublisherPort).publish(any());
    }

    @Test
    void shouldApproveCommandAfterPersistingApprovalDecision() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr-1")
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId("cmd-1")
                .runId("run-1")
                .threadId("thread-1")
                .boardId("board-1")
                .cardId("card-1")
                .golemId("golem-1")
                .riskLevel(ApprovalRiskLevel.DESTRUCTIVE)
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
        when(approvalRepositoryPort.findById("apr-1")).thenReturn(java.util.Optional.of(approval));

        ApprovalRequest approved = approvalWorkflowApplicationService.approve(
                "apr-1",
                "operator-1",
                "Hive Admin",
                "Looks safe");

        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        InOrder inOrder = inOrder(approvalRepositoryPort, approvalCommandStatePort, approvalThreadPort,
                approvedCommandDispatchPort);
        inOrder.verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        inOrder.verify(approvalCommandStatePort).markCommandQueued(
                "cmd-1",
                "Approved and awaiting dispatch",
                approved.getUpdatedAt());
        inOrder.verify(approvalCommandStatePort).markRunQueued("run-1", approved.getUpdatedAt());
        inOrder.verify(approvalThreadPort).appendCommandStatusMessage(
                "thread-1",
                "cmd-1",
                "run-1",
                "system",
                "Hive",
                "Approval granted by Hive Admin",
                approved.getUpdatedAt());
        inOrder.verify(approvedCommandDispatchPort).dispatchApprovedCommand("cmd-1");
        verify(auditLogUseCase).record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
    }

    @Test
    void shouldPersistApprovalWhenCommandStateUpdateFails() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr-1")
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId("cmd-1")
                .runId("run-1")
                .threadId("thread-1")
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
        when(approvalRepositoryPort.findById("apr-1")).thenReturn(java.util.Optional.of(approval));
        doThrow(new IllegalStateException("missing command")).when(approvalCommandStatePort).markCommandQueued(
                eq("cmd-1"),
                eq("Approved and awaiting dispatch"),
                any(Instant.class));

        ApprovalRequest approved = approvalWorkflowApplicationService.approve(
                "apr-1",
                "operator-1",
                "Hive Admin",
                "Looks safe");

        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        verify(approvedCommandDispatchPort, never()).dispatchApprovedCommand(any());
        verify(auditLogUseCase, times(2))
                .record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
    }

    @Test
    void shouldPersistApprovalWhenDispatchTriggerFails() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr-1")
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId("cmd-1")
                .runId("run-1")
                .threadId("thread-1")
                .boardId("board-1")
                .cardId("card-1")
                .golemId("golem-1")
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
        when(approvalRepositoryPort.findById("apr-1")).thenReturn(java.util.Optional.of(approval));
        doThrow(new IllegalStateException("offline")).when(approvedCommandDispatchPort)
                .dispatchApprovedCommand("cmd-1");

        ApprovalRequest approved = approvalWorkflowApplicationService.approve(
                "apr-1",
                "operator-1",
                "Hive Admin",
                "Looks safe");

        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        verify(approvedCommandDispatchPort).dispatchApprovedCommand("cmd-1");
        verify(auditLogUseCase, times(2))
                .record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
    }

    @Test
    void shouldRejectCommandApprovalAndPersistRejectedState() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr-1")
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId("cmd-1")
                .runId("run-1")
                .threadId("thread-1")
                .boardId("board-1")
                .cardId("card-1")
                .golemId("golem-1")
                .riskLevel(ApprovalRiskLevel.DESTRUCTIVE)
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
        when(approvalRepositoryPort.findById("apr-1")).thenReturn(java.util.Optional.of(approval));

        ApprovalRequest rejected = approvalWorkflowApplicationService.reject(
                "apr-1",
                "operator-1",
                "Hive Admin",
                "Unsafe in production");

        assertEquals(ApprovalStatus.REJECTED, rejected.getStatus());
        verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        verify(approvalCommandStatePort).markCommandRejected(
                "cmd-1",
                "Unsafe in production",
                rejected.getUpdatedAt(),
                rejected.getUpdatedAt());
        verify(approvalCommandStatePort).markRunRejected("run-1", rejected.getUpdatedAt(), rejected.getUpdatedAt());
        verify(approvalThreadPort).appendCommandStatusMessage(
                "thread-1",
                "cmd-1",
                "run-1",
                "system",
                "Hive",
                "Approval rejected by Hive Admin",
                rejected.getUpdatedAt());
        verify(auditLogUseCase).record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
        verify(approvedCommandDispatchPort, never()).dispatchApprovedCommand(any());
    }

    @Test
    void shouldPersistRejectedApprovalWhenStateSyncFails() {
        AuditLogUseCase auditLogUseCase = mock(AuditLogUseCase.class);
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        OperatorUpdatePublisherPort operatorUpdatePublisherPort = mock(OperatorUpdatePublisherPort.class);
        ApprovalRepositoryPort approvalRepositoryPort = mock(ApprovalRepositoryPort.class);
        ApprovalCommandStatePort approvalCommandStatePort = mock(ApprovalCommandStatePort.class);
        ApprovedCommandDispatchPort approvedCommandDispatchPort = mock(ApprovedCommandDispatchPort.class);
        ApprovalThreadPort approvalThreadPort = mock(ApprovalThreadPort.class);
        ApprovalGolemProfilePort approvalGolemProfilePort = mock(ApprovalGolemProfilePort.class);
        ApprovalWorkflowApplicationService approvalWorkflowApplicationService = new ApprovalWorkflowApplicationService(
                GOVERNANCE_SETTINGS,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);

        ApprovalRequest approval = ApprovalRequest.builder()
                .id("apr-1")
                .subjectType(ApprovalSubjectType.COMMAND)
                .commandId("cmd-1")
                .runId("run-1")
                .threadId("thread-1")
                .boardId("board-1")
                .cardId("card-1")
                .golemId("golem-1")
                .status(ApprovalStatus.PENDING)
                .requestedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
        when(approvalRepositoryPort.findById("apr-1")).thenReturn(java.util.Optional.of(approval));
        doThrow(new IllegalStateException("missing run")).when(approvalCommandStatePort).markCommandRejected(
                eq("cmd-1"),
                eq("Unsafe in production"),
                any(Instant.class),
                any(Instant.class));

        ApprovalRequest rejected = approvalWorkflowApplicationService.reject(
                "apr-1",
                "operator-1",
                "Hive Admin",
                "Unsafe in production");

        assertEquals(ApprovalStatus.REJECTED, rejected.getStatus());
        verify(approvalRepositoryPort).save(any(ApprovalRequest.class));
        verify(approvedCommandDispatchPort, never()).dispatchApprovedCommand(any());
        verify(auditLogUseCase, times(2))
                .record(any(me.golemcore.hive.domain.model.AuditEvent.AuditEventBuilder.class));
    }
}
