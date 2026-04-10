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
public class SelfEvolvingArtifactCatalogProjection {

    @Builder.Default
    private int schemaVersion = 1;

    private String golemId;
    private String artifactStreamId;
    private String originArtifactStreamId;
    private String artifactKey;

    @Builder.Default
    private List<String> artifactAliases = new ArrayList<>();

    private String artifactType;
    private String artifactSubtype;
    private String displayName;
    private String latestRevisionId;
    private String activeRevisionId;
    private String latestCandidateRevisionId;
    private String currentLifecycleState;
    private String currentRolloutStage;
    private Boolean hasRegression;
    private Boolean hasPendingApproval;
    private Integer campaignCount;
    private Integer projectionSchemaVersion;
    private String sourceBotVersion;
    private Instant projectedAt;
    private Instant updatedAt;
    private Boolean stale;
    private String staleReason;
}
