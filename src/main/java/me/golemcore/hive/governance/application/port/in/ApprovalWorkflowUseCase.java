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

import java.util.List;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalRiskLevel;
import me.golemcore.hive.governance.application.ApprovalCommandRequest;
import me.golemcore.hive.governance.application.PromotionApprovalRequest;

public interface ApprovalWorkflowUseCase {

    ApprovalRiskLevel resolveApprovalRisk(ApprovalRiskLevel requestedRiskLevel, long estimatedCostMicros);

    ApprovalRequest createApproval(
            ApprovalCommandRequest request,
            String actorId,
            String actorName);

    ApprovalRequest createPromotionApproval(PromotionApprovalRequest request, String actorId,
            String actorName);

    List<ApprovalRequest> listApprovals(String status, String boardId, String cardId, String golemId);

    ApprovalRequest getApproval(String approvalId);

    ApprovalRequest approve(String approvalId, String actorId, String actorName, String comment);

    ApprovalRequest reject(String approvalId, String actorId, String actorName, String comment);
}
