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
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

class GolemInspectionRpcServiceTest {

    @Test
    void shouldCorrelateInspectionResponseToPendingRequest() throws Exception {
        GolemControlChannelService controlChannelService = mock(GolemControlChannelService.class);
        when(controlChannelService.send(eq("golem-1"), anyString())).thenReturn(true);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        GolemInspectionRpcService service = new GolemInspectionRpcService(controlChannelService, objectMapper,
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

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(controlChannelService).send(eq("golem-1"), payloadCaptor.capture());
        JsonNode envelope = objectMapper.readTree(payloadCaptor.getValue());
        String requestId = envelope.get("requestId").asText();
        assertEquals("inspection.request", envelope.get("eventType").asText());
        assertEquals("sessions.list", envelope.get("inspection").get("operation").asText());

        service.handleInspectionResponse("golem-1", new GolemEventPayload(
                1,
                "inspection_response",
                "golem-1",
                null,
                null,
                null,
                null,
                requestId,
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
        GolemControlChannelService controlChannelService = mock(GolemControlChannelService.class);
        when(controlChannelService.send(eq("golem-1"), anyString())).thenReturn(true);
        GolemInspectionRpcService service = new GolemInspectionRpcService(
                controlChannelService,
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
