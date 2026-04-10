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

package me.golemcore.hive.selfevolving.application.service;

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
import me.golemcore.hive.selfevolving.application.port.in.SelfEvolvingReadUseCase;
import me.golemcore.hive.selfevolving.application.port.out.SelfEvolvingReadPort;

public class SelfEvolvingReadApplicationService implements SelfEvolvingReadUseCase {

    private final SelfEvolvingReadPort selfEvolvingReadPort;

    public SelfEvolvingReadApplicationService(SelfEvolvingReadPort selfEvolvingReadPort) {
        this.selfEvolvingReadPort = selfEvolvingReadPort;
    }

    @Override
    public List<SelfEvolvingRunProjection> listRuns(String golemId) {
        return selfEvolvingReadPort.listRuns(golemId);
    }

    @Override
    public Optional<SelfEvolvingRunProjection> findRun(String golemId, String runId) {
        return selfEvolvingReadPort.findRun(golemId, runId);
    }

    @Override
    public List<SelfEvolvingCandidateProjection> listCandidates(String golemId) {
        return selfEvolvingReadPort.listCandidates(golemId);
    }

    @Override
    public List<SelfEvolvingCampaignProjection> listCampaigns(String golemId) {
        return selfEvolvingReadPort.listCampaigns(golemId);
    }

    @Override
    public List<SelfEvolvingLineageNode> listLineage(String golemId) {
        return selfEvolvingReadPort.listLineage(golemId);
    }

    @Override
    public List<SelfEvolvingArtifactCatalogProjection> listArtifacts(String golemId) {
        return selfEvolvingReadPort.listArtifacts(golemId);
    }

    @Override
    public List<SelfEvolvingTacticProjection> listTactics(String golemId) {
        return selfEvolvingReadPort.listTactics(golemId);
    }

    @Override
    public List<SelfEvolvingTacticProjection> searchTactics(String golemId, String query) {
        return selfEvolvingReadPort.searchTactics(golemId, query);
    }

    @Override
    public Optional<SelfEvolvingTacticSearchStatusProjection> getTacticSearchStatus(String golemId, String query) {
        return selfEvolvingReadPort.getTacticSearchStatus(golemId, query);
    }

    @Override
    public Optional<SelfEvolvingTacticProjection> findTactic(String golemId, String tacticId) {
        return selfEvolvingReadPort.findTactic(golemId, tacticId);
    }

    @Override
    public Optional<SelfEvolvingArtifactCatalogProjection> findArtifactSummary(String golemId,
            String artifactStreamId) {
        return selfEvolvingReadPort.findArtifactSummary(golemId, artifactStreamId);
    }

    @Override
    public Optional<SelfEvolvingArtifactLineageProjection> findArtifactLineage(String golemId,
            String artifactStreamId) {
        return selfEvolvingReadPort.findArtifactLineage(golemId, artifactStreamId);
    }

    @Override
    public Optional<Map<String, Object>> findArtifactDiff(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId) {
        return selfEvolvingReadPort.findArtifactDiff(golemId, artifactStreamId, payloadKind, leftId, rightId);
    }

    @Override
    public Optional<Map<String, Object>> findArtifactEvidence(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId) {
        return selfEvolvingReadPort.findArtifactEvidence(golemId, artifactStreamId, payloadKind, leftId, rightId);
    }

    @Override
    public Optional<SelfEvolvingArtifactCompareProjection> compareArtifacts(
            String artifactStreamId,
            String leftGolemId,
            String rightGolemId,
            String leftRevisionId,
            String rightRevisionId) {
        return selfEvolvingReadPort.compareArtifacts(
                artifactStreamId,
                leftGolemId,
                rightGolemId,
                leftRevisionId,
                rightRevisionId);
    }

    @Override
    public List<SelfEvolvingArtifactCatalogProjection> searchArtifacts(
            String golemId,
            String artifactType,
            String artifactSubtype,
            Boolean hasRegression,
            Boolean hasPendingApproval,
            String rolloutStage,
            String query) {
        return selfEvolvingReadPort.searchArtifacts(
                golemId,
                artifactType,
                artifactSubtype,
                hasRegression,
                hasPendingApproval,
                rolloutStage,
                query);
    }
}
