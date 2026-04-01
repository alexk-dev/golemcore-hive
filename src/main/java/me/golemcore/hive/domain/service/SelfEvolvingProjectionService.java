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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCatalogProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCompareProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactLineageProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactNormalizedRevisionProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCampaignProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingLineageNode;
import me.golemcore.hive.domain.model.SelfEvolvingRunProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticSearchStatusProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfEvolvingProjectionService {

    private static final String SELF_EVOLVING_RUNS_DIR = "selfevolving-runs";
    private static final String SELF_EVOLVING_CANDIDATES_DIR = "selfevolving-candidates";
    private static final String SELF_EVOLVING_CAMPAIGNS_DIR = "selfevolving-campaigns";
    private static final String SELF_EVOLVING_LINEAGE_DIR = "selfevolving-lineage";
    private static final String SELF_EVOLVING_ARTIFACTS_DIR = "selfevolving-artifacts";
    private static final String SELF_EVOLVING_ARTIFACT_NORMALIZED_REVISIONS_DIR = "selfevolving-artifact-normalized-revisions";
    private static final String SELF_EVOLVING_ARTIFACT_LINEAGE_DIR = "selfevolving-artifact-lineage";
    private static final String SELF_EVOLVING_ARTIFACT_DIFFS_DIR = "selfevolving-artifact-diffs";
    private static final String SELF_EVOLVING_ARTIFACT_EVIDENCE_DIR = "selfevolving-artifact-evidence";
    private static final String SELF_EVOLVING_TACTICS_DIR = "selfevolving-tactics";
    private static final String SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR = "selfevolving-tactic-search-results";
    private static final String SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR = "selfevolving-tactic-search-status";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    public void applyRunEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingRunProjection projection = objectMapper.convertValue(event.payload(),
                SelfEvolvingRunProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setId(firstNonBlank(projection.getId(), event.runId()));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveRun(projection);
        updateRunProjection(projection);
    }

    public void applyCandidateEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingCandidateProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingCandidateProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveCandidate(projection);
    }

    public void applyCampaignEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingCampaignProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingCampaignProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveCampaign(projection);
    }

    public void applyLineageEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingLineageNode projection = objectMapper.convertValue(event.payload(), SelfEvolvingLineageNode.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveLineage(projection);
    }

    public void applyArtifactCatalogEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingArtifactCatalogProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingArtifactCatalogProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveArtifactCatalog(golemId, projection);
    }

    public void applyArtifactNormalizedRevisionEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingArtifactNormalizedRevisionProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingArtifactNormalizedRevisionProjection.class);
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveArtifactNormalizedRevision(golemId, projection);
    }

    public void applyArtifactLineageEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingArtifactLineageProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingArtifactLineageProjection.class);
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveArtifactLineage(golemId, projection);
    }

    public void applyArtifactDiffEvent(String golemId, GolemEventPayload event) {
        Map<String, Object> payload = objectMapper.convertValue(event.payload(), Map.class);
        saveArtifactPayload(SELF_EVOLVING_ARTIFACT_DIFFS_DIR, golemId, payload, event.createdAt());
    }

    public void applyArtifactEvidenceEvent(String golemId, GolemEventPayload event) {
        Map<String, Object> payload = objectMapper.convertValue(event.payload(), Map.class);
        saveArtifactPayload(SELF_EVOLVING_ARTIFACT_EVIDENCE_DIR, golemId, payload, event.createdAt());
    }

    public void applyTacticEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingTacticProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingTacticProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveTactic(projection);
    }

    public void applyTacticSearchStatusEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingTacticSearchStatusProjection projection = objectMapper.convertValue(
                event.payload(),
                SelfEvolvingTacticSearchStatusProjection.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        clearMirroredTacticSearchResults(projection.getGolemId(), projection.getQuery());
        saveTacticSearchStatus(projection);
    }

    public List<SelfEvolvingRunProjection> listRuns(String golemId) {
        List<SelfEvolvingRunProjection> runs = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_RUNS_DIR, golemId + "/")) {
            Optional<SelfEvolvingRunProjection> run = load(path, SELF_EVOLVING_RUNS_DIR,
                    SelfEvolvingRunProjection.class);
            run.ifPresent(runs::add);
        }
        runs.sort(Comparator
                .comparing(SelfEvolvingRunProjection::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingRunProjection::getId, Comparator.nullsLast(String::compareTo)));
        return runs;
    }

    public Optional<SelfEvolvingRunProjection> findRun(String golemId, String runId) {
        return load(runPath(golemId, runId), SELF_EVOLVING_RUNS_DIR, SelfEvolvingRunProjection.class);
    }

    public List<SelfEvolvingCandidateProjection> listCandidates(String golemId) {
        List<SelfEvolvingCandidateProjection> candidates = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_CANDIDATES_DIR, golemId + "/")) {
            Optional<SelfEvolvingCandidateProjection> candidate = load(
                    path,
                    SELF_EVOLVING_CANDIDATES_DIR,
                    SelfEvolvingCandidateProjection.class);
            candidate.ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparing(
                SelfEvolvingCandidateProjection::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates;
    }

    public List<SelfEvolvingLineageNode> listLineage(String golemId) {
        List<SelfEvolvingLineageNode> nodes = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_LINEAGE_DIR, golemId + "/")) {
            Optional<SelfEvolvingLineageNode> node = load(path, SELF_EVOLVING_LINEAGE_DIR,
                    SelfEvolvingLineageNode.class);
            node.ifPresent(nodes::add);
        }
        nodes.sort(Comparator.comparing(SelfEvolvingLineageNode::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return nodes;
    }

    public List<SelfEvolvingCampaignProjection> listCampaigns(String golemId) {
        List<SelfEvolvingCampaignProjection> campaigns = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_CAMPAIGNS_DIR, golemId + "/")) {
            Optional<SelfEvolvingCampaignProjection> campaign = load(path, SELF_EVOLVING_CAMPAIGNS_DIR,
                    SelfEvolvingCampaignProjection.class);
            campaign.ifPresent(campaigns::add);
        }
        campaigns.sort(Comparator.comparing(SelfEvolvingCampaignProjection::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return campaigns;
    }

    public List<SelfEvolvingArtifactCatalogProjection> listArtifacts(String golemId) {
        List<SelfEvolvingArtifactCatalogProjection> artifacts = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_ARTIFACTS_DIR, golemId + "/")) {
            Optional<SelfEvolvingArtifactCatalogProjection> projection = load(
                    path,
                    SELF_EVOLVING_ARTIFACTS_DIR,
                    SelfEvolvingArtifactCatalogProjection.class);
            projection.map(value -> enrichArtifactCatalogProjection(golemId, value)).ifPresent(artifacts::add);
        }
        artifacts.sort(Comparator.comparing(
                SelfEvolvingArtifactCatalogProjection::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingArtifactCatalogProjection::getGolemId,
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(SelfEvolvingArtifactCatalogProjection::getArtifactStreamId,
                        Comparator.nullsLast(String::compareTo)));
        return artifacts;
    }

    public List<SelfEvolvingTacticProjection> listTactics(String golemId) {
        List<SelfEvolvingTacticProjection> tactics = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_TACTICS_DIR, golemId + "/")) {
            Optional<SelfEvolvingTacticProjection> projection = load(path, SELF_EVOLVING_TACTICS_DIR,
                    SelfEvolvingTacticProjection.class);
            projection.ifPresent(tactics::add);
        }
        tactics.sort(Comparator.comparing(
                SelfEvolvingTacticProjection::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingTacticProjection::getTacticId, Comparator.nullsLast(String::compareTo)));
        return tactics;
    }

    public List<SelfEvolvingTacticProjection> searchTactics(String golemId, String query) {
        if (query == null || query.isBlank()) {
            return listTactics(golemId);
        }
        return listMirroredTacticSearchResults(golemId, query);
    }

    public Optional<SelfEvolvingTacticProjection> findTactic(String golemId, String tacticId) {
        return load(tacticPath(golemId, tacticId), SELF_EVOLVING_TACTICS_DIR, SelfEvolvingTacticProjection.class);
    }

    public Optional<SelfEvolvingTacticSearchStatusProjection> getTacticSearchStatus(String golemId) {
        return getTacticSearchStatus(golemId, null);
    }

    public Optional<SelfEvolvingTacticSearchStatusProjection> getTacticSearchStatus(String golemId, String query) {
        if (query != null && !query.isBlank()) {
            Optional<SelfEvolvingTacticSearchStatusProjection> queryScoped = load(
                    tacticSearchStatusPath(golemId, query),
                    SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR,
                    SelfEvolvingTacticSearchStatusProjection.class);
            if (queryScoped.isPresent()) {
                return queryScoped;
            }
            return Optional.empty();
        }
        return load(tacticSearchStatusPath(golemId), SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR,
                SelfEvolvingTacticSearchStatusProjection.class);
    }

    public Optional<SelfEvolvingArtifactCatalogProjection> findArtifactSummary(String golemId,
            String artifactStreamId) {
        return load(
                artifactCatalogPath(golemId, artifactStreamId),
                SELF_EVOLVING_ARTIFACTS_DIR,
                SelfEvolvingArtifactCatalogProjection.class)
                .map(value -> enrichArtifactCatalogProjection(golemId, value));
    }

    public Optional<SelfEvolvingArtifactLineageProjection> findArtifactLineage(String golemId,
            String artifactStreamId) {
        return load(
                artifactLineagePath(golemId, artifactStreamId),
                SELF_EVOLVING_ARTIFACT_LINEAGE_DIR,
                SelfEvolvingArtifactLineageProjection.class);
    }

    public Optional<Map<String, Object>> findArtifactDiff(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId) {
        return loadMap(
                SELF_EVOLVING_ARTIFACT_DIFFS_DIR,
                artifactPayloadPath(golemId, artifactStreamId, payloadKind, leftId, rightId));
    }

    public Optional<Map<String, Object>> findArtifactEvidence(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId) {
        return loadMap(
                SELF_EVOLVING_ARTIFACT_EVIDENCE_DIR,
                artifactPayloadPath(golemId, artifactStreamId, payloadKind, leftId, rightId));
    }

    public Optional<SelfEvolvingArtifactCompareProjection> compareArtifacts(
            String artifactStreamId,
            String leftGolemId,
            String rightGolemId,
            String leftRevisionId,
            String rightRevisionId) {
        Optional<SelfEvolvingArtifactCatalogProjection> leftCatalog = findArtifactSummary(leftGolemId,
                artifactStreamId);
        Optional<SelfEvolvingArtifactCatalogProjection> rightCatalog = findArtifactSummary(rightGolemId,
                artifactStreamId);
        Optional<SelfEvolvingArtifactNormalizedRevisionProjection> leftProjection = findArtifactNormalizedRevision(
                leftGolemId,
                artifactStreamId,
                leftRevisionId);
        Optional<SelfEvolvingArtifactNormalizedRevisionProjection> rightProjection = findArtifactNormalizedRevision(
                rightGolemId,
                artifactStreamId,
                rightRevisionId);
        if (leftProjection.isEmpty() || rightProjection.isEmpty()) {
            return Optional.empty();
        }
        boolean sameContent = firstNonBlank(leftProjection.get().getNormalizedHash(), "")
                .equals(firstNonBlank(rightProjection.get().getNormalizedHash(), ""));
        List<String> warnings = new ArrayList<>();
        boolean leftStale = leftCatalog.map(SelfEvolvingArtifactCatalogProjection::getStale).orElse(Boolean.FALSE);
        boolean rightStale = rightCatalog.map(SelfEvolvingArtifactCatalogProjection::getStale).orElse(Boolean.FALSE);
        if (leftStale) {
            warnings.add("Left artifact projection is stale"
                    + formatReason(
                            leftCatalog.map(SelfEvolvingArtifactCatalogProjection::getStaleReason).orElse(null)));
        }
        if (rightStale) {
            warnings.add("Right artifact projection is stale"
                    + formatReason(
                            rightCatalog.map(SelfEvolvingArtifactCatalogProjection::getStaleReason).orElse(null)));
        }
        return Optional.of(SelfEvolvingArtifactCompareProjection.builder()
                .artifactStreamId(artifactStreamId)
                .leftGolemId(leftGolemId)
                .rightGolemId(rightGolemId)
                .leftRevisionId(leftRevisionId)
                .rightRevisionId(rightRevisionId)
                .leftNormalizedHash(leftProjection.get().getNormalizedHash())
                .rightNormalizedHash(rightProjection.get().getNormalizedHash())
                .sameContent(sameContent)
                .leftStale(leftStale)
                .rightStale(rightStale)
                .summary(sameContent ? "Artifacts are identical across golems" : "Artifacts diverged across golems")
                .normalizationSchemaVersion(leftProjection.get().getNormalizationSchemaVersion())
                .projectedAt(Instant.now())
                .warnings(warnings)
                .build());
    }

    public List<SelfEvolvingArtifactCatalogProjection> searchArtifacts(
            String golemId,
            String artifactType,
            String artifactSubtype,
            Boolean hasRegression,
            Boolean hasPendingApproval,
            String rolloutStage,
            String query) {
        List<SelfEvolvingArtifactCatalogProjection> matches = new ArrayList<>();
        for (String path : storagePort.listObjects(SELF_EVOLVING_ARTIFACTS_DIR, "")) {
            Optional<SelfEvolvingArtifactCatalogProjection> projection = load(
                    path,
                    SELF_EVOLVING_ARTIFACTS_DIR,
                    SelfEvolvingArtifactCatalogProjection.class);
            if (projection.isEmpty()) {
                continue;
            }
            String projectionGolemId = extractGolemId(path);
            SelfEvolvingArtifactCatalogProjection enriched = enrichArtifactCatalogProjection(projectionGolemId,
                    projection.get());
            if (!matchesArtifactFilters(enriched, golemId, artifactType, artifactSubtype, hasRegression,
                    hasPendingApproval, rolloutStage, query)) {
                continue;
            }
            matches.add(enriched);
        }
        matches.sort(Comparator.comparing(
                SelfEvolvingArtifactCatalogProjection::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingArtifactCatalogProjection::getGolemId,
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(SelfEvolvingArtifactCatalogProjection::getArtifactStreamId,
                        Comparator.nullsLast(String::compareTo)));
        return matches;
    }

    private void updateRunProjection(SelfEvolvingRunProjection projection) {
        if (!storagePort.exists(RUNS_DIR, projection.getId() + ".json")) {
            return;
        }
        Optional<RunProjection> runProjectionOptional = load(projection.getId() + ".json", RUNS_DIR,
                RunProjection.class);
        if (runProjectionOptional.isEmpty()) {
            return;
        }
        RunProjection runProjection = runProjectionOptional.get();
        runProjection.setSelfEvolvingRunId(projection.getId());
        runProjection.setSelfEvolvingOutcomeStatus(projection.getOutcomeStatus());
        runProjection.setSelfEvolvingPromotionRecommendation(projection.getPromotionRecommendation());
        save(RUNS_DIR, projection.getId() + ".json", runProjection);
    }

    private void saveRun(SelfEvolvingRunProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_RUNS_DIR);
        save(SELF_EVOLVING_RUNS_DIR, runPath(projection.getGolemId(), projection.getId()), projection);
    }

    private void saveCandidate(SelfEvolvingCandidateProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_CANDIDATES_DIR);
        save(SELF_EVOLVING_CANDIDATES_DIR, candidatePath(projection.getGolemId(), projection.getId()), projection);
    }

    private void saveCampaign(SelfEvolvingCampaignProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_CAMPAIGNS_DIR);
        save(SELF_EVOLVING_CAMPAIGNS_DIR, campaignPath(projection.getGolemId(), projection.getId()), projection);
    }

    private void saveArtifactCatalog(String golemId, SelfEvolvingArtifactCatalogProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_ARTIFACTS_DIR);
        save(SELF_EVOLVING_ARTIFACTS_DIR, artifactCatalogPath(golemId, projection.getArtifactStreamId()), projection);
    }

    private void saveArtifactNormalizedRevision(
            String golemId,
            SelfEvolvingArtifactNormalizedRevisionProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_ARTIFACT_NORMALIZED_REVISIONS_DIR);
        save(
                SELF_EVOLVING_ARTIFACT_NORMALIZED_REVISIONS_DIR,
                artifactNormalizedRevisionPath(golemId, projection.getArtifactStreamId(),
                        projection.getContentRevisionId()),
                projection);
    }

    private void saveArtifactLineage(String golemId, SelfEvolvingArtifactLineageProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_ARTIFACT_LINEAGE_DIR);
        save(SELF_EVOLVING_ARTIFACT_LINEAGE_DIR, artifactLineagePath(golemId, projection.getArtifactStreamId()),
                projection);
    }

    private void saveArtifactPayload(
            String directory,
            String golemId,
            Map<String, Object> payload,
            Instant updatedAt) {
        storagePort.ensureDirectory(directory);
        Map<String, Object> enrichedPayload = objectMapper.convertValue(payload, Map.class);
        enrichedPayload.put("updatedAt", updatedAt != null ? updatedAt : Instant.now());
        String artifactStreamId = firstNonBlank(stringValue(enrichedPayload.get("artifactStreamId")), "unknown");
        String payloadKind = firstNonBlank(stringValue(enrichedPayload.get("payloadKind")), "revision");
        String leftId = resolveArtifactPayloadLeftId(payloadKind, enrichedPayload);
        String rightId = resolveArtifactPayloadRightId(payloadKind, enrichedPayload);
        save(directory, artifactPayloadPath(golemId, artifactStreamId, payloadKind, leftId, rightId), enrichedPayload);
    }

    private void saveTactic(SelfEvolvingTacticProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_TACTICS_DIR);
        if (projection.getSearchQuery() != null && !projection.getSearchQuery().isBlank()) {
            storagePort.ensureDirectory(SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR);
            save(
                    SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR,
                    tacticSearchResultPath(projection.getGolemId(), projection.getSearchQuery(),
                            projection.getTacticId()),
                    projection);
            return;
        }
        save(SELF_EVOLVING_TACTICS_DIR, tacticPath(projection.getGolemId(), projection.getTacticId()), projection);
    }

    private void saveTacticSearchStatus(SelfEvolvingTacticSearchStatusProjection projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR);
        if (projection.getQuery() != null && !projection.getQuery().isBlank()) {
            save(
                    SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR,
                    tacticSearchStatusPath(projection.getGolemId(), projection.getQuery()),
                    projection);
        }
        save(SELF_EVOLVING_TACTIC_SEARCH_STATUS_DIR, tacticSearchStatusPath(projection.getGolemId()), projection);
    }

    private void clearMirroredTacticSearchResults(String golemId, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String prefix = golemId + "/" + encodePathToken(query) + "/";
        for (String path : storagePort.listObjects(SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR, prefix)) {
            storagePort.delete(SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR, path);
        }
    }

    private void saveLineage(SelfEvolvingLineageNode projection) {
        storagePort.ensureDirectory(SELF_EVOLVING_LINEAGE_DIR);
        save(SELF_EVOLVING_LINEAGE_DIR, lineagePath(projection.getGolemId(), projection.getId()), projection);
    }

    private <T> Optional<T> load(String path, String directory, Class<T> type) {
        String content = storagePort.getText(directory, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, type));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize projection " + path, exception);
        }
    }

    private Optional<Map<String, Object>> loadMap(String directory, String path) {
        String content = storagePort.getText(directory, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Map.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize projection " + path, exception);
        }
    }

    private void save(String directory, String path, Object value) {
        try {
            storagePort.putTextAtomic(directory, path, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize projection " + path, exception);
        }
    }

    private String runPath(String golemId, String runId) {
        return golemId + "/" + runId + ".json";
    }

    private String candidatePath(String golemId, String candidateId) {
        return golemId + "/" + candidateId + ".json";
    }

    private String campaignPath(String golemId, String campaignId) {
        return golemId + "/" + campaignId + ".json";
    }

    private String lineagePath(String golemId, String nodeId) {
        return golemId + "/" + nodeId + ".json";
    }

    private Optional<SelfEvolvingArtifactNormalizedRevisionProjection> findArtifactNormalizedRevision(
            String golemId,
            String artifactStreamId,
            String revisionId) {
        return load(
                artifactNormalizedRevisionPath(golemId, artifactStreamId, revisionId),
                SELF_EVOLVING_ARTIFACT_NORMALIZED_REVISIONS_DIR,
                SelfEvolvingArtifactNormalizedRevisionProjection.class);
    }

    private String artifactCatalogPath(String golemId, String artifactStreamId) {
        return golemId + "/" + encodePathToken(artifactStreamId) + ".json";
    }

    private String tacticPath(String golemId, String tacticId) {
        return golemId + "/" + encodePathToken(tacticId) + ".json";
    }

    private String tacticSearchStatusPath(String golemId) {
        return golemId + "/status.json";
    }

    private String tacticSearchStatusPath(String golemId, String query) {
        return golemId + "/queries/" + encodePathToken(query) + ".json";
    }

    private String tacticSearchResultPath(String golemId, String query, String tacticId) {
        return golemId + "/" + encodePathToken(query) + "/" + encodePathToken(tacticId) + ".json";
    }

    private String artifactNormalizedRevisionPath(String golemId, String artifactStreamId, String revisionId) {
        return golemId + "/" + encodePathToken(artifactStreamId) + "/" + encodePathToken(revisionId) + ".json";
    }

    private String artifactLineagePath(String golemId, String artifactStreamId) {
        return golemId + "/" + encodePathToken(artifactStreamId) + ".json";
    }

    private String artifactPayloadPath(
            String golemId,
            String artifactStreamId,
            String payloadKind,
            String leftId,
            String rightId) {
        return golemId + "/"
                + encodePathToken(artifactStreamId) + "/"
                + encodePathToken(payloadKind) + "__"
                + encodePathToken(firstNonBlank(leftId, "left")) + "__"
                + encodePathToken(firstNonBlank(rightId, "right")) + ".json";
    }

    private String resolveArtifactPayloadLeftId(String payloadKind, Map<String, Object> payload) {
        if ("transition".equals(payloadKind)) {
            return stringValue(payload.get("fromNodeId"));
        }
        return firstNonBlank(stringValue(payload.get("fromRevisionId")), stringValue(payload.get("revisionId")));
    }

    private String resolveArtifactPayloadRightId(String payloadKind, Map<String, Object> payload) {
        if ("transition".equals(payloadKind)) {
            return stringValue(payload.get("toNodeId"));
        }
        return firstNonBlank(stringValue(payload.get("toRevisionId")), stringValue(payload.get("revisionId")));
    }

    private String encodePathToken(String value) {
        return firstNonBlank(value, "unknown").replace("/", "~");
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean matchesTacticQuery(SelfEvolvingTacticProjection tactic, String loweredQuery) {
        return containsIgnoreCase(tactic.getTacticId(), loweredQuery)
                || containsIgnoreCase(tactic.getSearchQuery(), loweredQuery)
                || containsIgnoreCase(tactic.getArtifactKey(), loweredQuery)
                || containsIgnoreCase(tactic.getTitle(), loweredQuery)
                || containsIgnoreCase(tactic.getIntentSummary(), loweredQuery)
                || containsIgnoreCase(tactic.getBehaviorSummary(), loweredQuery)
                || containsIgnoreCase(tactic.getToolSummary(), loweredQuery)
                || containsIgnoreCase(tactic.getOutcomeSummary(), loweredQuery)
                || containsIgnoreCase(tactic.getBenchmarkSummary(), loweredQuery)
                || listContainsIgnoreCase(tactic.getAliases(), loweredQuery)
                || listContainsIgnoreCase(tactic.getTaskFamilies(), loweredQuery)
                || listContainsIgnoreCase(tactic.getTags(), loweredQuery)
                || listContainsIgnoreCase(tactic.getEvidenceSnippets(), loweredQuery)
                || (tactic.getExplanation() != null
                        && (listContainsIgnoreCase(tactic.getExplanation().getMatchedQueryViews(), loweredQuery)
                                || listContainsIgnoreCase(tactic.getExplanation().getMatchedTerms(), loweredQuery)));
    }

    private List<SelfEvolvingTacticProjection> listMirroredTacticSearchResults(String golemId, String query) {
        List<SelfEvolvingTacticProjection> results = new ArrayList<>();
        String prefix = golemId + "/" + encodePathToken(query) + "/";
        for (String path : storagePort.listObjects(SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR, prefix)) {
            Optional<SelfEvolvingTacticProjection> projection = load(
                    path,
                    SELF_EVOLVING_TACTIC_SEARCH_RESULTS_DIR,
                    SelfEvolvingTacticProjection.class);
            projection.ifPresent(results::add);
        }
        results.sort(Comparator.comparing(
                SelfEvolvingTacticProjection::getScore,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingTacticProjection::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SelfEvolvingTacticProjection::getTacticId, Comparator.nullsLast(String::compareTo)));
        return results;
    }

    private boolean listContainsIgnoreCase(List<String> values, String loweredQuery) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (containsIgnoreCase(value, loweredQuery)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private SelfEvolvingArtifactCatalogProjection enrichArtifactCatalogProjection(
            String golemId,
            SelfEvolvingArtifactCatalogProjection projection) {
        String resolvedGolemId = firstNonBlank(projection.getGolemId(), golemId);
        projection.setGolemId(resolvedGolemId);
        projection.setStale(isArtifactProjectionStale(resolvedGolemId, projection));
        projection.setStaleReason(resolveArtifactStaleReason(resolvedGolemId, projection));
        return projection;
    }

    private boolean matchesArtifactFilters(
            SelfEvolvingArtifactCatalogProjection projection,
            String golemId,
            String artifactType,
            String artifactSubtype,
            Boolean hasRegression,
            Boolean hasPendingApproval,
            String rolloutStage,
            String query) {
        if (!matchesEquals(projection.getGolemId(), golemId)) {
            return false;
        }
        if (!matchesEquals(projection.getArtifactType(), artifactType)) {
            return false;
        }
        if (!matchesEquals(projection.getArtifactSubtype(), artifactSubtype)) {
            return false;
        }
        if (!matchesBoolean(projection.getHasRegression(), hasRegression)) {
            return false;
        }
        if (!matchesBoolean(projection.getHasPendingApproval(), hasPendingApproval)) {
            return false;
        }
        if (!matchesEquals(projection.getCurrentRolloutStage(), rolloutStage)) {
            return false;
        }
        return matchesQuery(projection, query);
    }

    private boolean matchesEquals(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equalsIgnoreCase(firstNonBlank(actual, ""));
    }

    private boolean matchesBoolean(Boolean actual, Boolean expected) {
        if (expected == null) {
            return true;
        }
        return expected.equals(actual);
    }

    private boolean matchesQuery(SelfEvolvingArtifactCatalogProjection projection, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase();
        if (containsIgnoreCase(projection.getArtifactStreamId(), normalizedQuery)
                || containsIgnoreCase(projection.getArtifactKey(), normalizedQuery)
                || containsIgnoreCase(projection.getDisplayName(), normalizedQuery)
                || containsIgnoreCase(projection.getGolemId(), normalizedQuery)) {
            return true;
        }
        if (projection.getArtifactAliases() == null) {
            return false;
        }
        return projection.getArtifactAliases().stream()
                .anyMatch(alias -> containsIgnoreCase(alias, normalizedQuery));
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private boolean isArtifactProjectionStale(String golemId, SelfEvolvingArtifactCatalogProjection projection) {
        return resolveArtifactStaleReason(golemId, projection) != null;
    }

    private String resolveArtifactStaleReason(String golemId, SelfEvolvingArtifactCatalogProjection projection) {
        if (projection == null) {
            return "Missing catalog projection";
        }
        if (projection.getProjectionSchemaVersion() == null || projection.getProjectionSchemaVersion() < 1) {
            return "Unsupported projection schema version";
        }
        Optional<SelfEvolvingArtifactLineageProjection> lineageProjection = findArtifactLineage(
                golemId,
                projection.getArtifactStreamId());
        if (lineageProjection.isEmpty()) {
            return "Missing lineage projection";
        }
        String selectedRevisionId = firstNonBlank(lineageProjection.get().getDefaultSelectedRevisionId(),
                firstNonBlank(projection.getLatestCandidateRevisionId(), projection.getActiveRevisionId()));
        if (selectedRevisionId != null
                && !selectedRevisionId.isBlank()
                && findArtifactNormalizedRevision(golemId, projection.getArtifactStreamId(), selectedRevisionId)
                        .isEmpty()) {
            return "Missing normalized revision for " + selectedRevisionId;
        }
        return null;
    }

    private String extractGolemId(String path) {
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return path.substring(0, slashIndex);
    }

    private String formatReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        return ": " + reason;
    }
}
