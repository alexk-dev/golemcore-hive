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
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.selfevolving.application.SelfEvolvingProjectionEvent;
import me.golemcore.hive.selfevolving.adapter.out.persistence.JsonSelfEvolvingProjectionAdapter;
import me.golemcore.hive.selfevolving.application.port.in.SelfEvolvingReadUseCase;
import me.golemcore.hive.selfevolving.application.port.in.SelfEvolvingWriteUseCase;
import me.golemcore.hive.selfevolving.application.service.SelfEvolvingReadApplicationService;
import me.golemcore.hive.selfevolving.application.service.SelfEvolvingWriteApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfEvolvingProjectionServiceTest {

    private final Map<String, String> storedObjects = new LinkedHashMap<>();
    private StoragePort storagePort;
    private SelfEvolvingReadUseCase readUseCase;
    private SelfEvolvingWriteUseCase writeUseCase;

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
        JsonSelfEvolvingProjectionAdapter projectionAdapter = new JsonSelfEvolvingProjectionAdapter(
                storagePort,
                objectMapper);
        readUseCase = new SelfEvolvingReadApplicationService(projectionAdapter);
        writeUseCase = new SelfEvolvingWriteApplicationService(projectionAdapter);
    }

    @Test
    void shouldPersistReadonlyRunAndCandidateProjections() {
        writeUseCase.applyEvent(event(
                "selfevolving.run.upserted",
                "golem-1",
                "run-1",
                Map.ofEntries(
                        Map.entry("id", "run-1"),
                        Map.entry("golemId", "golem-1"),
                        Map.entry("sessionId", "session-1"),
                        Map.entry("traceId", "trace-1"),
                        Map.entry("artifactBundleId", "bundle-1"),
                        Map.entry("status", "COMPLETED"),
                        Map.entry("outcomeStatus", "COMPLETED"),
                        Map.entry("processStatus", "HEALTHY"),
                        Map.entry("promotionRecommendation", "approve_gated"),
                        Map.entry("startedAt", "2026-03-31T15:00:00Z"),
                        Map.entry("completedAt", "2026-03-31T15:00:30Z")),
                Instant.parse("2026-03-31T15:00:31Z")));
        writeUseCase.applyEvent(event(
                "selfevolving.candidate.upserted",
                "golem-1",
                null,
                Map.of(
                        "id", "candidate-1",
                        "goal", "fix",
                        "artifactType", "skill",
                        "status", "approved_pending",
                        "riskLevel", "medium",
                        "expectedImpact", "Reduce routing failures",
                        "sourceRunIds", List.of("run-1")),
                Instant.parse("2026-03-31T15:00:32Z")));

        assertTrue(readUseCase.findRun("golem-1", "run-1").isPresent());
        assertEquals(1, readUseCase.listCandidates("golem-1").size());
    }

    @Test
    void shouldPersistReadonlyBenchmarkCampaignProjections() {
        writeUseCase.applyEvent(event(
                "selfevolving.campaign.upserted",
                "golem-1",
                "run-1",
                Map.ofEntries(
                        Map.entry("id", "campaign-1"),
                        Map.entry("golemId", "golem-1"),
                        Map.entry("suiteId", "suite-1"),
                        Map.entry("baselineBundleId", "bundle-a"),
                        Map.entry("candidateBundleId", "bundle-b"),
                        Map.entry("status", "created"),
                        Map.entry("runIds", List.of("run-1")),
                        Map.entry("startedAt", "2026-03-31T16:00:00Z")),
                Instant.parse("2026-03-31T16:00:01Z")));

        assertEquals(1, readUseCase.listCampaigns("golem-1").size());
        assertEquals("campaign-1", readUseCase.listCampaigns("golem-1").getFirst().getId());
    }

    private SelfEvolvingProjectionEvent event(
            String eventType,
            String golemId,
            String runId,
            Map<String, Object> payload,
            Instant createdAt) {
        return new SelfEvolvingProjectionEvent(eventType, golemId, runId, payload, createdAt);
    }

    private String storageKey(String directory, String path) {
        return directory + "::" + path;
    }
}
