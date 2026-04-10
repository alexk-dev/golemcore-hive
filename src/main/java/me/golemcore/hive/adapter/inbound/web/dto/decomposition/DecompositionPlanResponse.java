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

package me.golemcore.hive.adapter.inbound.web.dto.decomposition;

import java.time.Instant;
import java.util.List;

public record DecompositionPlanResponse(String id,String sourceCardId,String epicCardId,String serviceId,String objectiveId,String teamId,String plannerGolemId,String plannerDisplayName,String status,String rationale,Instant createdAt,Instant updatedAt,Instant approvedAt,String approvedByActorId,String approvedByActorName,String approvalComment,Instant rejectedAt,String rejectedByActorId,String rejectedByActorName,String rejectionComment,Instant appliedAt,String appliedByActorId,String appliedByActorName,List<DecompositionPlanItemResponse>items,List<DecompositionPlanLinkResponse>links){}
