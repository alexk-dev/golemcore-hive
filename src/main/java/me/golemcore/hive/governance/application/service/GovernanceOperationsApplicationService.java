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
import java.util.List;
import java.util.Optional;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.governance.application.port.in.ApprovalWorkflowUseCase;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.in.BudgetSnapshotUseCase;
import me.golemcore.hive.governance.application.port.in.GovernanceOperationsUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyLifecycleUseCase;

public class GovernanceOperationsApplicationService implements GovernanceOperationsUseCase {

    private final AuditLogUseCase auditLogUseCase;
    private final BudgetSnapshotUseCase budgetSnapshotUseCase;
    private final ApprovalWorkflowUseCase approvalWorkflowUseCase;
    private final PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase;
    private final PolicyLifecycleUseCase policyLifecycleUseCase;

    public GovernanceOperationsApplicationService(
            AuditLogUseCase auditLogUseCase,
            BudgetSnapshotUseCase budgetSnapshotUseCase,
            ApprovalWorkflowUseCase approvalWorkflowUseCase,
            PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            PolicyLifecycleUseCase policyLifecycleUseCase) {
        this.auditLogUseCase = auditLogUseCase;
        this.budgetSnapshotUseCase = budgetSnapshotUseCase;
        this.approvalWorkflowUseCase = approvalWorkflowUseCase;
        this.policyGroupAdministrationUseCase = policyGroupAdministrationUseCase;
        this.policyLifecycleUseCase = policyLifecycleUseCase;
    }

    @Override
    public AuditEvent recordAuditEvent(AuditEvent event) {
        return auditLogUseCase.record(event);
    }

    @Override
    public List<AuditEvent> listAuditEvents(
            String actorId,
            String golemId,
            String boardId,
            String cardId,
            Instant from,
            Instant to,
            String eventType) {
        return auditLogUseCase.listEvents(actorId, golemId, boardId, cardId, from, to, eventType);
    }

    @Override
    public List<BudgetSnapshot> listBudgetSnapshots(String scopeType, String query) {
        return budgetSnapshotUseCase.listSnapshots(scopeType, query);
    }

    @Override
    public List<ApprovalRequest> listApprovals(String status, String boardId, String cardId, String golemId) {
        return approvalWorkflowUseCase.listApprovals(status, boardId, cardId, golemId);
    }

    @Override
    public ApprovalRequest getApproval(String approvalId) {
        return approvalWorkflowUseCase.getApproval(approvalId);
    }

    @Override
    public ApprovalRequest approve(String approvalId, String actorId, String actorName, String comment) {
        return approvalWorkflowUseCase.approve(approvalId, actorId, actorName, comment);
    }

    @Override
    public ApprovalRequest reject(String approvalId, String actorId, String actorName, String comment) {
        return approvalWorkflowUseCase.reject(approvalId, actorId, actorName, comment);
    }

    @Override
    public List<PolicyGroup> listPolicyGroups() {
        return policyGroupAdministrationUseCase.listPolicyGroups();
    }

    @Override
    public PolicyGroup createPolicyGroup(String slug, String name, String description, String actorId,
            String actorName) {
        return policyGroupAdministrationUseCase.createPolicyGroup(slug, name, description, actorId, actorName);
    }

    @Override
    public PolicyGroup getPolicyGroup(String groupId) {
        return policyGroupAdministrationUseCase.getPolicyGroup(groupId);
    }

    @Override
    public PolicyGroup updatePolicyGroupDraft(String groupId, PolicyGroupSpec draftSpec) {
        return policyGroupAdministrationUseCase.updateDraft(groupId, draftSpec);
    }

    @Override
    public PolicyGroupVersion publishPolicyGroup(String groupId, String changeSummary, String actorId,
            String actorName) {
        return policyLifecycleUseCase.publish(groupId, changeSummary, actorId, actorName);
    }

    @Override
    public List<PolicyGroupVersion> listPolicyGroupVersions(String groupId) {
        return policyGroupAdministrationUseCase.listVersions(groupId);
    }

    @Override
    public PolicyGroup rollbackPolicyGroup(String groupId, int version, String changeSummary, String actorId,
            String actorName) {
        return policyLifecycleUseCase.rollback(groupId, version, changeSummary, actorId, actorName);
    }

    @Override
    public int countPolicyGroupBindings(String groupId) {
        return policyGroupAdministrationUseCase.countBindingsForPolicyGroup(groupId);
    }

    @Override
    public GolemPolicyBinding getPolicyBinding(String golemId) {
        return policyGroupAdministrationUseCase.getBinding(golemId);
    }

    @Override
    public PolicyGroupVersion getTargetPolicyVersionForGolem(String golemId) {
        return policyGroupAdministrationUseCase.getTargetVersionForGolem(golemId);
    }

    @Override
    public GolemPolicyBinding recordPolicyApplyResult(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String checksum,
            String errorDigest) {
        return policyGroupAdministrationUseCase.recordApplyResult(
                golemId,
                policyGroupId,
                targetVersion,
                appliedVersion,
                reportedStatus,
                checksum,
                errorDigest);
    }

    @Override
    public Optional<GolemPolicyBinding> recordHeartbeatSyncState(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String errorDigest) {
        return policyGroupAdministrationUseCase.recordHeartbeatSyncState(
                golemId,
                policyGroupId,
                targetVersion,
                appliedVersion,
                reportedStatus,
                errorDigest);
    }

    @Override
    public GolemPolicyBinding bindPolicyGroup(String golemId, String policyGroupId, String actorId, String actorName) {
        return policyLifecycleUseCase.bindGolem(golemId, policyGroupId, actorId, actorName);
    }

    @Override
    public void unbindPolicyGroup(String golemId, String actorId, String actorName) {
        policyLifecycleUseCase.unbindGolem(golemId, actorId, actorName);
    }
}
