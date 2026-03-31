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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.events.EvidenceRefPayload;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventBatchRequest;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventPayload;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.EvidenceRef;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.RuntimeEventType;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private static final String LIFECYCLE_SIGNALS_DIR = "lifecycle-signals";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final CommandDispatchService commandDispatchService;
    private final SignalResolutionService signalResolutionService;
    private final GolemInspectionRpcService golemInspectionRpcService;
    private final SelfEvolvingProjectionService selfEvolvingProjectionService;

    public BatchResult ingestBatch(String golemId, GolemEventBatchRequest request) {
        int acceptedEvents = 0;
        int runtimeEvents = 0;
        int lifecycleSignals = 0;
        int autoAppliedTransitions = 0;
        int suggestedTransitions = 0;

        for (GolemEventPayload event : request.events()) {
            if (event.golemId() != null && !event.golemId().isBlank() && !golemId.equals(event.golemId())) {
                throw new IllegalArgumentException("Event golemId does not match request path");
            }
            if ("inspection_response".equals(event.eventType())) {
                golemInspectionRpcService.handleInspectionResponse(golemId, event);
                acceptedEvents++;
            } else if ("selfevolving.run.upserted".equals(event.eventType())) {
                selfEvolvingProjectionService.applyRunEvent(golemId, event);
                acceptedEvents++;
            } else if ("selfevolving.candidate.upserted".equals(event.eventType())) {
                selfEvolvingProjectionService.applyCandidateEvent(golemId, event);
                acceptedEvents++;
            } else if ("selfevolving.lineage.upserted".equals(event.eventType())) {
                selfEvolvingProjectionService.applyLineageEvent(golemId, event);
                acceptedEvents++;
            } else if ("runtime_event".equals(event.eventType())) {
                RuntimeEventType runtimeEventType = RuntimeEventType.valueOf(event.runtimeEventType());
                commandDispatchService.applyRuntimeEvent(golemId, runtimeEventType, event.threadId(), event.cardId(),
                        event.commandId(), event.runId(), event.summary(), event.details(),
                        event.createdAt(), event.inputTokens(), event.outputTokens(), event.accumulatedCostMicros());
                acceptedEvents++;
                runtimeEvents++;
            } else if ("card_lifecycle_signal".equals(event.eventType())) {
                CardLifecycleSignal signal = toSignal(golemId, event);
                saveSignal(signal);
                commandDispatchService.applyLifecycleSignal(signal);
                signalResolutionService.resolve(signal);
                saveSignal(signal);
                acceptedEvents++;
                lifecycleSignals++;
                switch (signal.getResolutionOutcome()) {
                case AUTO_APPLIED:
                    autoAppliedTransitions = autoAppliedTransitions + 1;
                    break;
                case SUGGESTED:
                    suggestedTransitions = suggestedTransitions + 1;
                    break;
                default:
                    break;
                }
            } else {
                throw new IllegalArgumentException("Unsupported eventType: " + event.eventType());
            }
        }

        return new BatchResult(acceptedEvents, runtimeEvents, lifecycleSignals, autoAppliedTransitions,
                suggestedTransitions);
    }

    public List<CardLifecycleSignal> listSignals(String threadId) {
        List<CardLifecycleSignal> signals = new ArrayList<>();
        for (String path : storagePort.listObjects(LIFECYCLE_SIGNALS_DIR, "")) {
            Optional<CardLifecycleSignal> signalOptional = loadSignal(path);
            if (signalOptional.isEmpty()) {
                continue;
            }
            CardLifecycleSignal signal = signalOptional.get();
            if (threadId.equals(signal.getThreadId())) {
                signals.add(signal);
            }
        }
        signals.sort(Comparator.comparing(CardLifecycleSignal::getCreatedAt).thenComparing(CardLifecycleSignal::getId));
        return signals;
    }

    private CardLifecycleSignal toSignal(String golemId, GolemEventPayload event) {
        if (event.cardId() == null || event.cardId().isBlank()) {
            throw new IllegalArgumentException("cardId is required for lifecycle signals");
        }
        if (event.signalType() == null || event.signalType().isBlank()) {
            throw new IllegalArgumentException("signalType is required for lifecycle signals");
        }
        if (event.summary() == null || event.summary().isBlank()) {
            throw new IllegalArgumentException("summary is required for lifecycle signals");
        }
        Instant createdAt = event.createdAt() != null ? event.createdAt() : Instant.now();
        return CardLifecycleSignal.builder()
                .id(event.signalId() != null && !event.signalId().isBlank()
                        ? event.signalId()
                        : "sig_" + UUID.randomUUID().toString().replace("-", ""))
                .cardId(event.cardId())
                .golemId(golemId)
                .commandId(event.commandId())
                .runId(event.runId())
                .threadId(event.threadId())
                .signalType(LifecycleSignalType.valueOf(event.signalType()))
                .summary(event.summary())
                .details(event.details())
                .blockerCode(event.blockerCode())
                .evidenceRefs(event.evidenceRefs() != null
                        ? event.evidenceRefs().stream().map(this::toEvidenceRef).toList()
                        : List.of())
                .createdAt(createdAt)
                .build();
    }

    private EvidenceRef toEvidenceRef(EvidenceRefPayload payload) {
        return EvidenceRef.builder()
                .kind(payload.kind())
                .ref(payload.ref())
                .build();
    }

    private Optional<CardLifecycleSignal> loadSignal(String path) {
        String content = storagePort.getText(LIFECYCLE_SIGNALS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, CardLifecycleSignal.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize lifecycle signal " + path, exception);
        }
    }

    private void saveSignal(CardLifecycleSignal signal) {
        try {
            storagePort.putTextAtomic(LIFECYCLE_SIGNALS_DIR, signal.getId() + ".json",
                    objectMapper.writeValueAsString(signal));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize lifecycle signal " + signal.getId(), exception);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class BatchResult {
        private final int acceptedEvents;
        private final int runtimeEvents;
        private final int lifecycleSignals;
        private final int autoAppliedTransitions;
        private final int suggestedTransitions;
    }
}
