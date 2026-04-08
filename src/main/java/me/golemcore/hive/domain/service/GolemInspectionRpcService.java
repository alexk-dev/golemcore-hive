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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.domain.model.InspectionRpcResponse;
import me.golemcore.hive.port.outbound.GolemControlDispatchPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class GolemInspectionRpcService {

    private static final String EVENT_TYPE_INSPECTION_REQUEST = "inspection.request";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final GolemControlDispatchPort golemControlDispatchPort;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Map<String, Sinks.One<InspectionRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    @Autowired
    public GolemInspectionRpcService(
            GolemControlDispatchPort golemControlDispatchPort,
            ObjectMapper objectMapper) {
        this(golemControlDispatchPort, objectMapper, DEFAULT_TIMEOUT);
    }

    GolemInspectionRpcService(
            GolemControlDispatchPort golemControlDispatchPort,
            ObjectMapper objectMapper,
            Duration requestTimeout) {
        this.golemControlDispatchPort = golemControlDispatchPort;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
    }

    public Mono<InspectionRpcResponse> request(
            String golemId,
            String threadId,
            String cardId,
            String runId,
            InspectionRequestBody requestBody) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        Sinks.One<InspectionRpcResponse> sink = Sinks.one();
        pendingRequests.put(requestId, sink);

        ControlCommandEnvelope envelope = ControlCommandEnvelope.builder()
                .eventType(EVENT_TYPE_INSPECTION_REQUEST)
                .requestId(requestId)
                .threadId(threadId)
                .cardId(cardId)
                .golemId(golemId)
                .runId(runId)
                .inspection(requestBody)
                .createdAt(Instant.now())
                .build();
        if (!golemControlDispatchPort.send(golemId, envelope)) {
            pendingRequests.remove(requestId);
            return Mono.error(new IllegalStateException("Failed to deliver inspection request"));
        }

        return sink.asMono()
                .timeout(requestTimeout)
                .doFinally(signalType -> pendingRequests.remove(requestId));
    }

    public boolean handleInspectionResponse(InspectionResponseEvent event) {
        if (event == null || event.requestId() == null || event.requestId().isBlank()) {
            return false;
        }
        Sinks.One<InspectionRpcResponse> sink = pendingRequests.remove(event.requestId());
        if (sink == null) {
            return false;
        }
        InspectionRpcResponse response = InspectionRpcResponse.builder()
                .requestId(event.requestId())
                .operation(event.operation())
                .success(Boolean.TRUE.equals(event.success()))
                .errorCode(event.errorCode())
                .errorMessage(event.errorMessage())
                .payload(normalizePayload(event.payload()))
                .createdAt(event.createdAt())
                .build();
        sink.tryEmitValue(response);
        return true;
    }

    private Object normalizePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        return objectMapper.convertValue(payload, Object.class);
    }

}
