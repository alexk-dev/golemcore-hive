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

package me.golemcore.hive.governance.application.port.in;

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

public interface GovernanceOperationsUseCase {

    AuditEvent recordAuditEvent(AuditEvent event);

    List<AuditEvent> listAuditEvents(
            String actorId,
            String golemId,
            String boardId,
            String cardId,
            Instant from,
            Instant to,
            String eventType);

    List<BudgetSnapshot> listBudgetSnapshots(String scopeType, String query);

    List<ApprovalRequest> listApprovals(String status, String boardId, String cardId, String golemId);

    ApprovalRequest getApproval(String approvalId);

    ApprovalRequest approve(String approvalId, String actorId, String actorName, String comment);

    ApprovalRequest reject(String approvalId, String actorId, String actorName, String comment);

    List<PolicyGroup> listPolicyGroups();

    PolicyGroup createPolicyGroup(String slug, String name, String description, String actorId, String actorName);

    PolicyGroup getPolicyGroup(String groupId);

    PolicyGroup updatePolicyGroupDraft(String groupId, PolicyGroupSpec draftSpec);

    PolicyGroupVersion publishPolicyGroup(String groupId, String changeSummary, String actorId, String actorName);

    List<PolicyGroupVersion> listPolicyGroupVersions(String groupId);

    PolicyGroup rollbackPolicyGroup(String groupId, int version, String changeSummary, String actorId,
            String actorName);

    int countPolicyGroupBindings(String groupId);

    GolemPolicyBinding getPolicyBinding(String golemId);

    PolicyGroupVersion getTargetPolicyVersionForGolem(String golemId);

    GolemPolicyBinding recordPolicyApplyResult(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String checksum,
            String errorDigest);

    Optional<GolemPolicyBinding> recordHeartbeatSyncState(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String errorDigest);

    GolemPolicyBinding bindPolicyGroup(String golemId, String policyGroupId, String actorId, String actorName);

    void unbindPolicyGroup(String golemId, String actorId, String actorName);
}
