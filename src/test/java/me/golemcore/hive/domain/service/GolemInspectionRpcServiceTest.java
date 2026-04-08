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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.port.outbound.GolemControlDispatchPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

class GolemInspectionRpcServiceTest {

    @Test
    void shouldCorrelateInspectionResponseToPendingRequest() throws Exception {
        GolemControlDispatchPort controlDispatchPort = mock(GolemControlDispatchPort.class);
        when(controlDispatchPort.send(eq("golem-1"), any(ControlCommandEnvelope.class))).thenReturn(true);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        GolemInspectionRpcService service = new GolemInspectionRpcService(controlDispatchPort, objectMapper,
                Duration.ofSeconds(1));

        reactor.core.publisher.Mono<me.golemcore.hive.domain.model.InspectionRpcResponse> responseMono = service
                .request(
                        "golem-1",
                        "inspection:golem-1",
                        null,
                        null,
                        InspectionRequestBody.builder()
                                .operation("sessions.list")
                                .channel("web")
                                .build());

        ArgumentCaptor<ControlCommandEnvelope> payloadCaptor = ArgumentCaptor.forClass(ControlCommandEnvelope.class);
        verify(controlDispatchPort).send(eq("golem-1"), payloadCaptor.capture());
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
                objectMapper.valueToTree(List.of(Map.of("id", "web:conv-1"))),
                Instant.parse("2026-03-30T21:00:00Z")));

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(requestId, response.requestId());
                    assertEquals("sessions.list", response.operation());
                    assertTrue(response.success());
                    JsonNode responsePayload = objectMapper.valueToTree(response.payload());
                    assertEquals("web:conv-1", responsePayload.get(0).get("id").asText());
                })
                .verifyComplete();
    }

    @Test
    void shouldTimeoutPendingInspectionRequest() {
        GolemControlDispatchPort controlDispatchPort = mock(GolemControlDispatchPort.class);
        when(controlDispatchPort.send(eq("golem-1"), any(ControlCommandEnvelope.class))).thenReturn(true);
        GolemInspectionRpcService service = new GolemInspectionRpcService(
                controlDispatchPort,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Duration.ofMillis(50));

        StepVerifier.create(service.request(
                "golem-1",
                "inspection:golem-1",
                null,
                null,
                InspectionRequestBody.builder()
                        .operation("sessions.list")
                        .build()))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(1));
    }
}
