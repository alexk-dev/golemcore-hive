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

package me.golemcore.hive.execution.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.execution.application.EventIngestionResult;
import me.golemcore.hive.execution.application.GolemEventBatchCommand;
import me.golemcore.hive.execution.application.GolemEventCommand;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.in.GolemInspectionResponseUseCase;
import me.golemcore.hive.execution.application.port.in.LifecycleSignalResolutionUseCase;
import me.golemcore.hive.execution.application.port.out.CardLifecycleSignalRepository;
import me.golemcore.hive.execution.application.port.out.SelfEvolvingEventProjectionPort;
import org.junit.jupiter.api.Test;

class EventIngestionApplicationServiceTest {

    @Test
    void shouldRouteInspectionResponsesToRpcServiceWithoutPersistingRuntimeArtifacts() {
        CardLifecycleSignalRepository cardLifecycleSignalRepository = mock(CardLifecycleSignalRepository.class);
        ExecutionOperationsUseCase executionOperationsUseCase = mock(ExecutionOperationsUseCase.class);
        LifecycleSignalResolutionUseCase lifecycleSignalResolutionUseCase = mock(
                LifecycleSignalResolutionUseCase.class);
        GolemInspectionResponseUseCase golemInspectionResponseUseCase = mock(GolemInspectionResponseUseCase.class);
        SelfEvolvingEventProjectionPort selfEvolvingEventProjectionPort = mock(SelfEvolvingEventProjectionPort.class);
        EventIngestionApplicationService service = new EventIngestionApplicationService(
                cardLifecycleSignalRepository,
                executionOperationsUseCase,
                lifecycleSignalResolutionUseCase,
                golemInspectionResponseUseCase,
                selfEvolvingEventProjectionPort);

        EventIngestionResult result = service.ingestBatch("golem-1", new GolemEventBatchCommand(
                1,
                "golem-1",
                List.of(new GolemEventCommand(
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

        assertEquals(1, result.acceptedEvents());
        assertEquals(0, result.runtimeEvents());
        assertEquals(0, result.lifecycleSignals());
        verify(golemInspectionResponseUseCase).handleInspectionResponse(new InspectionResponseEvent(
                "req-1",
                "sessions.list",
                true,
                null,
                null,
                new ObjectMapper().valueToTree(List.of(Map.of("id", "web:conv-1"))),
                Instant.parse("2026-03-30T21:00:00Z")));
        verifyNoInteractions(executionOperationsUseCase);
        verifyNoInteractions(cardLifecycleSignalRepository);
        verifyNoInteractions(lifecycleSignalResolutionUseCase);
        verifyNoInteractions(selfEvolvingEventProjectionPort);
    }
}
