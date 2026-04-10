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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.golemcore.hive.execution.application.EventIngestionResult;
import me.golemcore.hive.execution.application.GolemEventBatchCommand;
import me.golemcore.hive.execution.application.GolemEventCommand;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.in.GolemInspectionResponseUseCase;
import me.golemcore.hive.execution.application.port.in.LifecycleSignalResolutionUseCase;
import me.golemcore.hive.execution.application.port.out.CardLifecycleSignalRepository;
import me.golemcore.hive.execution.application.port.out.SelfEvolvingEventProjectionPort;
import org.junit.jupiter.api.Test;

class EventIngestionApplicationServiceSelfEvolvingTest {

    @Test
    void shouldIngestSelfEvolvingCandidateEventWithoutTouchingSessionInspection() {
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
                        "selfevolving.candidate.upserted",
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
                        Map.of("id", "candidate-1"),
                        Instant.parse("2026-03-31T16:00:00Z")))));

        assertEquals(1, result.acceptedEvents());
        verify(selfEvolvingEventProjectionPort).applyEvent(eq("golem-1"), any());
    }

    @Test
    void shouldIngestSelfEvolvingTacticEvents() {
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
                List.of(
                        new GolemEventCommand(
                                1,
                                "selfevolving.tactic.upserted",
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
                                Map.of("tacticId", "planner"),
                                Instant.parse("2026-04-01T20:00:00Z")),
                        new GolemEventCommand(
                                1,
                                "selfevolving.tactic.search-status.upserted",
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
                                Map.of("mode", "hybrid"),
                                Instant.parse("2026-04-01T20:00:01Z")))));

        assertEquals(2, result.acceptedEvents());
        verify(selfEvolvingEventProjectionPort).applyEvent(eq("golem-1"),
                argThat(event -> "selfevolving.tactic.upserted".equals(event.eventType())));
        verify(selfEvolvingEventProjectionPort).applyEvent(eq("golem-1"),
                argThat(event -> "selfevolving.tactic.search-status.upserted".equals(event.eventType())));
    }
}
