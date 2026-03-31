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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingLineageNode;
import me.golemcore.hive.domain.model.SelfEvolvingRunProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfEvolvingProjectionService {

    private static final String SELF_EVOLVING_RUNS_DIR = "selfevolving-runs";
    private static final String SELF_EVOLVING_CANDIDATES_DIR = "selfevolving-candidates";
    private static final String SELF_EVOLVING_LINEAGE_DIR = "selfevolving-lineage";
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

    public void applyLineageEvent(String golemId, GolemEventPayload event) {
        SelfEvolvingLineageNode projection = objectMapper.convertValue(event.payload(), SelfEvolvingLineageNode.class);
        projection.setGolemId(firstNonBlank(projection.getGolemId(), golemId));
        projection.setUpdatedAt(event.createdAt() != null ? event.createdAt() : Instant.now());
        saveLineage(projection);
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

    private String lineagePath(String golemId, String nodeId) {
        return golemId + "/" + nodeId + ".json";
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
