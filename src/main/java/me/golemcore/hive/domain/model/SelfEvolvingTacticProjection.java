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
public class SelfEvolvingTacticProjection {

    @Builder.Default
    private int schemaVersion = 1;

    private String golemId;
    private String tacticId;
    private String searchQuery;
    private String artifactStreamId;
    private String originArtifactStreamId;
    private String artifactKey;
    private String artifactType;
    private String title;

    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    private String contentRevisionId;
    private String intentSummary;
    private String behaviorSummary;
    private String toolSummary;
    private String outcomeSummary;
    private String benchmarkSummary;
    private String approvalNotes;

    @Builder.Default
    private List<String> evidenceSnippets = new ArrayList<>();

    @Builder.Default
    private List<String> taskFamilies = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private String promotionState;
    private String rolloutStage;
    private Double successRate;
    private Double benchmarkWinRate;

    @Builder.Default
    private List<String> regressionFlags = new ArrayList<>();

    private Double recencyScore;
    private Double golemLocalUsageSuccess;
    private String embeddingStatus;
    private Double score;
    private SelfEvolvingTacticSearchExplanationProjection explanation;
    private Instant tacticUpdatedAt;
    private Instant updatedAt;
}
