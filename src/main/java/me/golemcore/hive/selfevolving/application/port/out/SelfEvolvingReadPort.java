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

package me.golemcore.hive.selfevolving.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCatalogProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCompareProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactLineageProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCampaignProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingLineageNode;
import me.golemcore.hive.domain.model.SelfEvolvingRunProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticSearchStatusProjection;

public interface SelfEvolvingReadPort {

    List<SelfEvolvingRunProjection> listRuns(String golemId);

    Optional<SelfEvolvingRunProjection> findRun(String golemId, String runId);

    List<SelfEvolvingCandidateProjection> listCandidates(String golemId);

    List<SelfEvolvingCampaignProjection> listCampaigns(String golemId);

    List<SelfEvolvingLineageNode> listLineage(String golemId);

    List<SelfEvolvingArtifactCatalogProjection> listArtifacts(String golemId);

    List<SelfEvolvingTacticProjection> listTactics(String golemId);

    List<SelfEvolvingTacticProjection> searchTactics(String golemId, String query);

    Optional<SelfEvolvingTacticSearchStatusProjection> getTacticSearchStatus(String golemId, String query);

    Optional<SelfEvolvingTacticProjection> findTactic(String golemId, String tacticId);

    Optional<SelfEvolvingArtifactCatalogProjection> findArtifactSummary(String golemId, String artifactStreamId);

    Optional<SelfEvolvingArtifactLineageProjection> findArtifactLineage(String golemId, String artifactStreamId);

    Optional<Map<String, Object>> findArtifactDiff(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId);

    Optional<Map<String, Object>> findArtifactEvidence(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId);

    Optional<SelfEvolvingArtifactCompareProjection> compareArtifacts(
            String artifactStreamId,
            String leftGolemId,
            String rightGolemId,
            String leftRevisionId,
            String rightRevisionId);

    List<SelfEvolvingArtifactCatalogProjection> searchArtifacts(
            String golemId,
            String artifactType,
            String artifactSubtype,
            Boolean hasRegression,
            Boolean hasPendingApproval,
            String rolloutStage,
            String query);
}
