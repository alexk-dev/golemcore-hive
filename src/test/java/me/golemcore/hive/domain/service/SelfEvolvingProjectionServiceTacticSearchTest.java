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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.SelfEvolvingTacticProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticSearchStatusProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfEvolvingProjectionServiceTacticSearchTest {

    private final Map<String, String> storedObjects = new LinkedHashMap<>();
    private StoragePort storagePort;
    private SelfEvolvingProjectionService service;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        when(storagePort.listObjects(anyString(), anyString())).thenAnswer(invocation -> {
            String directory = invocation.getArgument(0, String.class);
            String prefix = invocation.getArgument(1, String.class);
            List<String> matches = new ArrayList<>();
            for (String key : storedObjects.keySet()) {
                String directoryPrefix = directory + "::";
                if (key.startsWith(directoryPrefix + prefix)) {
                    matches.add(key.substring(directoryPrefix.length()));
                }
            }
            return matches;
        });
        when(storagePort.getText(anyString(), anyString())).thenAnswer(invocation -> storedObjects.get(storageKey(
                invocation.getArgument(0, String.class), invocation.getArgument(1, String.class))));
        doAnswer(invocation -> {
            storedObjects.put(
                    storageKey(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class)),
                    invocation.getArgument(2, String.class));
            return null;
        }).when(storagePort).putTextAtomic(anyString(), anyString(), anyString());
        service = new SelfEvolvingProjectionService(storagePort,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void shouldPersistMirroredTacticSearchStatusAndResults() {
        service.applyTacticSearchStatusEvent("golem-1", event(
                "selfevolving.tactic.search-status.upserted",
                Map.of(
                        "query", "planner",
                        "mode", "hybrid",
                        "reason", "Embeddings healthy",
                        "degraded", false,
                        "updatedAt", "2026-04-01T20:00:00Z")));
        service.applyTacticEvent("golem-1", event(
                "selfevolving.tactic.upserted",
                Map.ofEntries(
                        Map.entry("tacticId", "planner"),
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "origin-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("artifactType", "skill"),
                        Map.entry("title", "Planner tactic"),
                        Map.entry("aliases", List.of("planner")),
                        Map.entry("contentRevisionId", "rev-3"),
                        Map.entry("intentSummary", "Plan complex work"),
                        Map.entry("behaviorSummary", "Produce ordered plans"),
                        Map.entry("toolSummary", "filesystem, shell"),
                        Map.entry("outcomeSummary", "High completion rate"),
                        Map.entry("benchmarkSummary", "Wins planning suite"),
                        Map.entry("approvalNotes", "Approved after canary"),
                        Map.entry("evidenceSnippets", List.of("trace:run-1")),
                        Map.entry("taskFamilies", List.of("planning")),
                        Map.entry("tags", List.of("core")),
                        Map.entry("promotionState", "active"),
                        Map.entry("rolloutStage", "active"),
                        Map.entry("successRate", 0.92d),
                        Map.entry("benchmarkWinRate", 0.81d),
                        Map.entry("regressionFlags", List.of("watch_tool_churn")),
                        Map.entry("recencyScore", 0.78d),
                        Map.entry("golemLocalUsageSuccess", 0.88d),
                        Map.entry("embeddingStatus", "indexed"),
                        Map.entry("updatedAt", "2026-04-01T20:00:00Z"),
                        Map.entry("score", 1.18d),
                        Map.entry("explanation", Map.ofEntries(
                                Map.entry("searchMode", "hybrid"),
                                Map.entry("bm25Score", 0.52d),
                                Map.entry("vectorScore", 0.43d),
                                Map.entry("rrfScore", 0.95d),
                                Map.entry("qualityPrior", 0.21d),
                                Map.entry("mmrDiversityAdjustment", -0.01d),
                                Map.entry("negativeMemoryPenalty", 0.0d),
                                Map.entry("personalizationBoost", 0.08d),
                                Map.entry("rerankerVerdict", "tier deep"),
                                Map.entry("matchedQueryViews", List.of("planner")),
                                Map.entry("matchedTerms", List.of("planner", "shell")),
                                Map.entry("eligible", true),
                                Map.entry("finalScore", 1.18d))))));

        List<SelfEvolvingTacticProjection> tactics = service.listTactics("golem-1");
        Optional<SelfEvolvingTacticSearchStatusProjection> searchStatus = service.getTacticSearchStatus("golem-1");

        assertEquals(1, tactics.size());
        assertEquals("planner", tactics.getFirst().getTacticId());
        assertEquals(0.81d, tactics.getFirst().getBenchmarkWinRate());
        assertEquals(0.08d, tactics.getFirst().getExplanation().getPersonalizationBoost());
        assertTrue(searchStatus.isPresent());
        assertEquals("hybrid", searchStatus.get().getMode());
    }

    private GolemEventPayload event(String eventType, Map<String, Object> payload) {
        return new GolemEventPayload(
                1,
                eventType,
                "golem-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                payload,
                Instant.parse("2026-04-01T20:00:01Z"));
    }

    private String storageKey(String directory, String path) {
        return directory + "::" + path;
    }
}
