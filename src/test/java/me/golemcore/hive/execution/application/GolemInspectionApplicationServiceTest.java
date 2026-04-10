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

package me.golemcore.hive.execution.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.InspectionErrorCode;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.execution.application.port.out.InspectionAuditPort;
import me.golemcore.hive.execution.application.port.out.InspectionDispatchPort;
import me.golemcore.hive.execution.application.service.GolemInspectionApplicationService;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GolemInspectionApplicationServiceTest {

    @Test
    void shouldCorrelateInspectionResponseToPendingRequest() throws Exception {
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder().id("golem-1").build()));
        InspectionDispatchPort inspectionDispatchPort = mock(InspectionDispatchPort.class);
        when(inspectionDispatchPort.isConnected("golem-1")).thenReturn(true);
        when(inspectionDispatchPort.send(eq("golem-1"), any(ControlCommandEnvelope.class))).thenReturn(true);
        InspectionAuditPort inspectionAuditPort = mock(InspectionAuditPort.class);
        GolemInspectionApplicationService service = new GolemInspectionApplicationService(
                golemDirectoryUseCase,
                inspectionDispatchPort,
                inspectionAuditPort,
                Duration.ofSeconds(1));

        java.util.concurrent.CompletableFuture<me.golemcore.hive.domain.model.InspectionRpcResponse> responseFuture = service
                .execute(
                        new InspectionActor("operator-1", "Hive Admin"),
                        "golem-1",
                        InspectionRequestBody.builder()
                                .operation("sessions.list")
                                .channel("web")
                                .build());

        ArgumentCaptor<ControlCommandEnvelope> payloadCaptor = ArgumentCaptor.forClass(ControlCommandEnvelope.class);
        verify(inspectionDispatchPort).send(eq("golem-1"), payloadCaptor.capture());
        ControlCommandEnvelope envelope = payloadCaptor.getValue();
        String requestId = envelope.getRequestId();
        assertEquals("inspection.request", envelope.getEventType());
        assertEquals("sessions.list", envelope.getInspection().getOperation());

        service.handleInspectionResponse(new InspectionResponseEvent(
                requestId,
                "sessions.list",
                true,
                null,
                null,
                List.of(Map.of("id", "web:conv-1")),
                Instant.parse("2026-03-30T21:00:00Z")));

        me.golemcore.hive.domain.model.InspectionRpcResponse response = responseFuture.get();
        assertEquals(requestId, response.requestId());
        assertEquals("sessions.list", response.operation());
        assertTrue(response.success());
        List<?> payload = (List<?>) response.payload();
        assertEquals("web:conv-1", ((Map<?, ?>) payload.get(0)).get("id"));
    }

    @Test
    void shouldFailWhenGolemIsOffline() {
        GolemDirectoryUseCase golemDirectoryUseCase = mock(GolemDirectoryUseCase.class);
        when(golemDirectoryUseCase.findGolem("golem-1")).thenReturn(Optional.of(Golem.builder().id("golem-1").build()));
        InspectionDispatchPort inspectionDispatchPort = mock(InspectionDispatchPort.class);
        when(inspectionDispatchPort.isConnected("golem-1")).thenReturn(false);
        InspectionAuditPort inspectionAuditPort = mock(InspectionAuditPort.class);
        GolemInspectionApplicationService service = new GolemInspectionApplicationService(
                golemDirectoryUseCase,
                inspectionDispatchPort,
                inspectionAuditPort,
                Duration.ofSeconds(1));

        java.util.concurrent.CompletableFuture<me.golemcore.hive.domain.model.InspectionRpcResponse> responseFuture = service
                .execute(
                        new InspectionActor("operator-1", "Hive Admin"),
                        "golem-1",
                        InspectionRequestBody.builder().operation("sessions.list").build());

        ExecutionException executionException = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class,
                responseFuture::get);
        InspectionOperationException exception = (InspectionOperationException) executionException.getCause();
        assertEquals(InspectionErrorCode.GOLEM_OFFLINE, exception.getCode());
    }
}
