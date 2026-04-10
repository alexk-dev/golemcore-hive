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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.EvidenceRef;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.RuntimeEventType;
import me.golemcore.hive.execution.application.EventIngestionResult;
import me.golemcore.hive.execution.application.GolemEventBatchCommand;
import me.golemcore.hive.execution.application.GolemEventCommand;
import me.golemcore.hive.execution.application.GolemEventEvidenceInput;
import me.golemcore.hive.execution.application.port.in.EventIngestionUseCase;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.in.GolemInspectionResponseUseCase;
import me.golemcore.hive.execution.application.port.in.LifecycleSignalResolutionUseCase;
import me.golemcore.hive.execution.application.port.out.CardLifecycleSignalRepository;
import me.golemcore.hive.execution.application.port.out.SelfEvolvingEventProjectionPort;

public class EventIngestionApplicationService implements EventIngestionUseCase {

    private static final Set<String> SELF_EVOLVING_EVENT_TYPES = Set.of(
            "selfevolving.run.upserted",
            "selfevolving.candidate.upserted",
            "selfevolving.campaign.upserted",
            "selfevolving.lineage.upserted",
            "selfevolving.artifact.upserted",
            "selfevolving.artifact.normalized-revision.upserted",
            "selfevolving.artifact.lineage.upserted",
            "selfevolving.artifact.diff.upserted",
            "selfevolving.artifact.evidence.upserted",
            "selfevolving.tactic.upserted",
            "selfevolving.tactic.search-status.upserted");

    private final CardLifecycleSignalRepository cardLifecycleSignalRepository;
    private final ExecutionOperationsUseCase executionOperationsUseCase;
    private final LifecycleSignalResolutionUseCase lifecycleSignalResolutionUseCase;
    private final GolemInspectionResponseUseCase golemInspectionResponseUseCase;
    private final SelfEvolvingEventProjectionPort selfEvolvingEventProjectionPort;

    public EventIngestionApplicationService(
            CardLifecycleSignalRepository cardLifecycleSignalRepository,
            ExecutionOperationsUseCase executionOperationsUseCase,
            LifecycleSignalResolutionUseCase lifecycleSignalResolutionUseCase,
            GolemInspectionResponseUseCase golemInspectionResponseUseCase,
            SelfEvolvingEventProjectionPort selfEvolvingEventProjectionPort) {
        this.cardLifecycleSignalRepository = cardLifecycleSignalRepository;
        this.executionOperationsUseCase = executionOperationsUseCase;
        this.lifecycleSignalResolutionUseCase = lifecycleSignalResolutionUseCase;
        this.golemInspectionResponseUseCase = golemInspectionResponseUseCase;
        this.selfEvolvingEventProjectionPort = selfEvolvingEventProjectionPort;
    }

    @Override
    public EventIngestionResult ingestBatch(String golemId, GolemEventBatchCommand batch) {
        int acceptedEvents = 0;
        int runtimeEvents = 0;
        int lifecycleSignals = 0;
        int autoAppliedTransitions = 0;
        int suggestedTransitions = 0;

        for (GolemEventCommand event : batch.events()) {
            if (event.golemId() != null && !event.golemId().isBlank() && !golemId.equals(event.golemId())) {
                throw new IllegalArgumentException("Event golemId does not match request path");
            }
            if ("inspection_response".equals(event.eventType())) {
                golemInspectionResponseUseCase.handleInspectionResponse(toInspectionResponseEvent(event));
                acceptedEvents++;
            } else if (SELF_EVOLVING_EVENT_TYPES.contains(event.eventType())) {
                selfEvolvingEventProjectionPort.applyEvent(golemId, event);
                acceptedEvents++;
            } else if ("runtime_event".equals(event.eventType())) {
                RuntimeEventType runtimeEventType = RuntimeEventType.valueOf(event.runtimeEventType());
                executionOperationsUseCase.applyRuntimeEvent(golemId, runtimeEventType, event.threadId(),
                        event.cardId(),
                        event.commandId(), event.runId(), event.summary(), event.details(),
                        event.createdAt(), event.inputTokens(), event.outputTokens(), event.accumulatedCostMicros());
                acceptedEvents++;
                runtimeEvents++;
            } else if ("card_lifecycle_signal".equals(event.eventType())) {
                CardLifecycleSignal signal = toSignal(golemId, event);
                cardLifecycleSignalRepository.save(signal);
                executionOperationsUseCase.applyLifecycleSignal(signal);
                lifecycleSignalResolutionUseCase.resolve(signal);
                cardLifecycleSignalRepository.save(signal);
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

        return new EventIngestionResult(
                acceptedEvents,
                runtimeEvents,
                lifecycleSignals,
                autoAppliedTransitions,
                suggestedTransitions);
    }

    private CardLifecycleSignal toSignal(String golemId, GolemEventCommand event) {
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
                .evidenceRefs(event.evidenceRefs().stream().map(this::toEvidenceRef).toList())
                .createdAt(createdAt)
                .build();
    }

    private InspectionResponseEvent toInspectionResponseEvent(GolemEventCommand event) {
        return new InspectionResponseEvent(
                event.requestId(),
                event.operation(),
                Boolean.TRUE.equals(event.success()),
                event.errorCode(),
                event.errorMessage(),
                event.payload(),
                event.createdAt());
    }

    private EvidenceRef toEvidenceRef(GolemEventEvidenceInput input) {
        return EvidenceRef.builder()
                .kind(input.kind())
                .ref(input.ref())
                .build();
    }
}
