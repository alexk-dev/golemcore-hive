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

package me.golemcore.hive.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Builder.Default
    private int schemaVersion = 1;

    private String id;
    @Builder.Default
    private ApprovalSubjectType subjectType = ApprovalSubjectType.COMMAND;
    private String commandId;
    private String runId;
    private String threadId;
    private String boardId;
    private String cardId;
    private String golemId;
    private String requestedByActorType;
    private String requestedByActorId;
    private String requestedByActorName;
    private ApprovalRiskLevel riskLevel;
    private String reason;
    private long estimatedCostMicros;
    private String commandBody;
    private ApprovalStatus status;
    private Instant requestedAt;
    private Instant updatedAt;
    private Instant decidedAt;
    private String decidedByActorType;
    private String decidedByActorId;
    private String decidedByActorName;
    private String decisionComment;
    private SelfEvolvingPromotionApprovalContext promotionContext;
}
