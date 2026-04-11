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
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecompositionPlan {

    @Builder.Default
    private int schemaVersion = 1;

    private String id;
    private String sourceCardId;
    private String epicCardId;
    private String serviceId;
    private String objectiveId;
    private String teamId;
    private String plannerGolemId;
    private String plannerDisplayName;
    private DecompositionPlanStatus status;

    @Builder.Default
    private List<DecompositionPlanItem> items = new ArrayList<>();

    @Builder.Default
    private List<DecompositionPlanLink> links = new ArrayList<>();

    private String rationale;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
    private String approvedByActorId;
    private String approvedByActorName;
    private String approvalComment;
    private Instant rejectedAt;
    private String rejectedByActorId;
    private String rejectedByActorName;
    private String rejectionComment;
    private Instant appliedAt;
    private String appliedByActorId;
    private String appliedByActorName;
}
