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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventBatchRequest;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.port.outbound.StoragePort;
import org.junit.jupiter.api.Test;

class EventIngestionServiceTest {

    @Test
    void shouldRouteInspectionResponsesToRpcServiceWithoutPersistingRuntimeArtifacts() {
        StoragePort storagePort = mock(StoragePort.class);
        CommandDispatchService commandDispatchService = mock(CommandDispatchService.class);
        SignalResolutionService signalResolutionService = mock(SignalResolutionService.class);
        GolemInspectionRpcService golemInspectionRpcService = mock(GolemInspectionRpcService.class);
        SelfEvolvingProjectionService selfEvolvingProjectionService = mock(SelfEvolvingProjectionService.class);
        EventIngestionService service = new EventIngestionService(
                storagePort,
                new ObjectMapper(),
                commandDispatchService,
                signalResolutionService,
                golemInspectionRpcService,
                selfEvolvingProjectionService);

        EventIngestionService.BatchResult result = service.ingestBatch("golem-1", new GolemEventBatchRequest(
                1,
                "golem-1",
                List.of(new GolemEventPayload(
                        1,
                        "inspection_response",
                        "golem-1",
                        null,
                        null,
                        null,
                        null,
                        "req-1",
                        null,
                        "inspection:golem-1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "sessions.list",
                        true,
                        null,
                        null,
                        new ObjectMapper().valueToTree(List.of(Map.of("id", "web:conv-1"))),
                        Instant.parse("2026-03-30T21:00:00Z")))));

        assertEquals(1, result.getAcceptedEvents());
        assertEquals(0, result.getRuntimeEvents());
        assertEquals(0, result.getLifecycleSignals());
        verify(golemInspectionRpcService).handleInspectionResponse("golem-1", new GolemEventPayload(
                1,
                "inspection_response",
                "golem-1",
                null,
                null,
                null,
                null,
                "req-1",
                null,
                "inspection:golem-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "sessions.list",
                true,
                null,
                null,
                new ObjectMapper().valueToTree(List.of(Map.of("id", "web:conv-1"))),
                Instant.parse("2026-03-30T21:00:00Z")));
        verifyNoInteractions(commandDispatchService);
        verifyNoInteractions(signalResolutionService);
    }
}
