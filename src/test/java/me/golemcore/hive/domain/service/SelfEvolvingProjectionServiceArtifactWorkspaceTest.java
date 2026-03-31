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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCatalogProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCompareProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactLineageProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfEvolvingProjectionServiceArtifactWorkspaceTest {

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
    void shouldPersistMirroredArtifactWorkspaceAndDeriveFleetCompare() {
        service.applyArtifactCatalogEvent("golem-1", event(
                "selfevolving.artifact.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("artifactAliases", List.of("skill:planner")),
                        Map.entry("artifactType", "skill"),
                        Map.entry("artifactSubtype", "skill"),
                        Map.entry("activeRevisionId", "rev-1"),
                        Map.entry("latestCandidateRevisionId", "rev-2"),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "dev"),
                        Map.entry("projectedAt", "2026-03-31T20:00:00Z"))));
        service.applyArtifactNormalizedRevisionEvent("golem-1", event(
                "selfevolving.artifact.normalized-revision.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("contentRevisionId", "rev-2"),
                        Map.entry("normalizationSchemaVersion", 1),
                        Map.entry("normalizedContent", "planner v2"),
                        Map.entry("normalizedHash", "hash-2"),
                        Map.entry("semanticSections", List.of("planner")),
                        Map.entry("sourceBotVersion", "dev"),
                        Map.entry("projectedAt", "2026-03-31T20:01:00Z"))));
        service.applyArtifactNormalizedRevisionEvent("golem-2", event(
                "selfevolving.artifact.normalized-revision.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("contentRevisionId", "rev-3"),
                        Map.entry("normalizationSchemaVersion", 1),
                        Map.entry("normalizedContent", "planner v3"),
                        Map.entry("normalizedHash", "hash-3"),
                        Map.entry("semanticSections", List.of("planner")),
                        Map.entry("sourceBotVersion", "dev"),
                        Map.entry("projectedAt", "2026-03-31T20:02:00Z"))));
        service.applyArtifactLineageEvent("golem-1", event(
                "selfevolving.artifact.lineage.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("nodes", List.of(Map.of(
                                "nodeId", "candidate-1:proposed",
                                "contentRevisionId", "rev-2",
                                "rolloutStage", "proposed"))),
                        Map.entry("edges", List.of()),
                        Map.entry("railOrder", List.of("candidate-1:proposed")),
                        Map.entry("branches", List.of()),
                        Map.entry("defaultSelectedNodeId", "candidate-1:proposed"),
                        Map.entry("defaultSelectedRevisionId", "rev-2"),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "dev"),
                        Map.entry("projectedAt", "2026-03-31T20:03:00Z"))));

        assertEquals(1, service.listArtifacts("golem-1").size());
        assertEquals("stream-1",
                service.findArtifactSummary("golem-1", "stream-1").orElseThrow().getArtifactStreamId());

        SelfEvolvingArtifactLineageProjection lineageProjection = service.findArtifactLineage("golem-1", "stream-1")
                .orElseThrow();
        assertEquals("candidate-1:proposed", lineageProjection.getDefaultSelectedNodeId());

        SelfEvolvingArtifactCompareProjection compareProjection = service.compareArtifacts(
                "stream-1",
                "golem-1",
                "golem-2",
                "rev-2",
                "rev-3").orElseThrow();
        assertFalse(Boolean.TRUE.equals(compareProjection.getSameContent()));
        assertTrue(compareProjection.getSummary().contains("Artifacts diverged"));
    }

    @Test
    void shouldSearchArtifactsAcrossGolemsAndFlagStaleCompareInputs() {
        service.applyArtifactCatalogEvent("golem-1", event(
                "selfevolving.artifact.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("artifactAliases", List.of("skill:planner", "planner")),
                        Map.entry("artifactType", "skill"),
                        Map.entry("artifactSubtype", "skill"),
                        Map.entry("activeRevisionId", "rev-2"),
                        Map.entry("latestCandidateRevisionId", "rev-2"),
                        Map.entry("currentRolloutStage", "active"),
                        Map.entry("hasRegression", false),
                        Map.entry("hasPendingApproval", false),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "bot-1"),
                        Map.entry("projectedAt", "2026-03-31T21:00:00Z"))));
        service.applyArtifactCatalogEvent("golem-2", event(
                "selfevolving.artifact.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("artifactAliases", List.of("skill:planner", "planner-v2")),
                        Map.entry("artifactType", "skill"),
                        Map.entry("artifactSubtype", "skill"),
                        Map.entry("activeRevisionId", "rev-missing"),
                        Map.entry("latestCandidateRevisionId", "rev-3"),
                        Map.entry("currentRolloutStage", "active"),
                        Map.entry("hasRegression", true),
                        Map.entry("hasPendingApproval", true),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "bot-2"),
                        Map.entry("projectedAt", "2026-03-31T21:01:00Z"))));
        service.applyArtifactNormalizedRevisionEvent("golem-1", event(
                "selfevolving.artifact.normalized-revision.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("contentRevisionId", "rev-2"),
                        Map.entry("normalizationSchemaVersion", 1),
                        Map.entry("normalizedContent", "planner v2"),
                        Map.entry("normalizedHash", "hash-2"),
                        Map.entry("semanticSections", List.of("planner")),
                        Map.entry("sourceBotVersion", "bot-1"),
                        Map.entry("projectedAt", "2026-03-31T21:02:00Z"))));
        service.applyArtifactNormalizedRevisionEvent("golem-2", event(
                "selfevolving.artifact.normalized-revision.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("contentRevisionId", "rev-3"),
                        Map.entry("normalizationSchemaVersion", 1),
                        Map.entry("normalizedContent", "planner v3"),
                        Map.entry("normalizedHash", "hash-3"),
                        Map.entry("semanticSections", List.of("planner")),
                        Map.entry("sourceBotVersion", "bot-2"),
                        Map.entry("projectedAt", "2026-03-31T21:03:00Z"))));
        service.applyArtifactLineageEvent("golem-1", event(
                "selfevolving.artifact.lineage.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("nodes", List.of(Map.of(
                                "nodeId", "candidate-2:active",
                                "contentRevisionId", "rev-2",
                                "rolloutStage", "active"))),
                        Map.entry("edges", List.of()),
                        Map.entry("railOrder", List.of("candidate-2:active")),
                        Map.entry("branches", List.of()),
                        Map.entry("defaultSelectedNodeId", "candidate-2:active"),
                        Map.entry("defaultSelectedRevisionId", "rev-2"),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "bot-1"),
                        Map.entry("projectedAt", "2026-03-31T21:04:00Z"))));

        List<SelfEvolvingArtifactCatalogProjection> searchResults = service.searchArtifacts(
                null,
                "skill",
                null,
                Boolean.TRUE,
                Boolean.TRUE,
                "active",
                "planner");

        assertEquals(1, searchResults.size());
        assertEquals("golem-2", searchResults.getFirst().getGolemId());
        assertTrue(Boolean.TRUE.equals(searchResults.getFirst().getStale()));
        assertNotNull(searchResults.getFirst().getStaleReason());

        SelfEvolvingArtifactCompareProjection compareProjection = service.compareArtifacts(
                "stream-1",
                "golem-1",
                "golem-2",
                "rev-2",
                "rev-3").orElseThrow();
        assertTrue(Boolean.TRUE.equals(compareProjection.getRightStale()));
        assertTrue(compareProjection.getWarnings().stream()
                .anyMatch(warning -> warning.contains("stale")));
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
                Instant.parse("2026-03-31T20:04:00Z"));
    }

    private String storageKey(String directory, String path) {
        return directory + "::" + path;
    }
}
