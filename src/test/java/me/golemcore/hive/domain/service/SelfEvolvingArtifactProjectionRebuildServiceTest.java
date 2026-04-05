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
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.port.outbound.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfEvolvingArtifactProjectionRebuildServiceTest {

    private final Map<String, String> storedObjects = new LinkedHashMap<>();
    private StoragePort storagePort;
    private SelfEvolvingProjectionService projectionService;
    private SelfEvolvingArtifactProjectionRebuildService rebuildService;

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

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        projectionService = new SelfEvolvingProjectionService(storagePort, objectMapper);
        rebuildService = new SelfEvolvingArtifactProjectionRebuildService(projectionService);
    }

    @Test
    void shouldScanMirroredArtifactsAndCountStaleEntries() {
        projectionService.applyArtifactCatalogEvent("golem-1", event(
                "selfevolving.artifact.upserted",
                Map.ofEntries(
                        Map.entry("artifactStreamId", "stream-1"),
                        Map.entry("originArtifactStreamId", "stream-1"),
                        Map.entry("artifactKey", "skill:planner"),
                        Map.entry("artifactAliases", List.of("skill:planner")),
                        Map.entry("artifactType", "skill"),
                        Map.entry("artifactSubtype", "skill"),
                        Map.entry("activeRevisionId", "rev-missing"),
                        Map.entry("latestCandidateRevisionId", "rev-missing"),
                        Map.entry("projectionSchemaVersion", 1),
                        Map.entry("sourceBotVersion", "bot-1"),
                        Map.entry("projectedAt", "2026-03-31T22:00:00Z"))));

        SelfEvolvingArtifactProjectionRebuildService.RebuildResult result = rebuildService.rebuildAll();

        assertEquals(1, result.scannedArtifacts());
        assertEquals(1, result.staleArtifacts());
        assertTrue(result.staleArtifactRefs().contains("golem-1:stream-1"));
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
                Instant.parse("2026-03-31T22:01:00Z"));
    }

    private String storageKey(String directory, String path) {
        return directory + "::" + path;
    }
}
