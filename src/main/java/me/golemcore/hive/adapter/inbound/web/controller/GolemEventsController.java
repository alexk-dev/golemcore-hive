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

package me.golemcore.hive.adapter.inbound.web.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventBatchRequest;
import me.golemcore.hive.adapter.inbound.web.dto.events.GolemEventBatchResponse;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.execution.application.EventIngestionResult;
import me.golemcore.hive.execution.application.GolemEventBatchCommand;
import me.golemcore.hive.execution.application.GolemEventCommand;
import me.golemcore.hive.execution.application.GolemEventEvidenceInput;
import me.golemcore.hive.execution.application.port.in.EventIngestionUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/golems")
@RequiredArgsConstructor
public class GolemEventsController {

    private final EventIngestionUseCase eventIngestionUseCase;

    @PostMapping("/{golemId}/events:batch")
    public Mono<ResponseEntity<GolemEventBatchResponse>> ingestEvents(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody GolemEventBatchRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireGolemScope(principal, golemId, GolemScope.EVENTS_WRITE.value());
            EventIngestionResult result = eventIngestionUseCase.ingestBatch(golemId, toBatchCommand(request));
            return ResponseEntity.ok(new GolemEventBatchResponse(
                    result.acceptedEvents(),
                    result.runtimeEvents(),
                    result.lifecycleSignals(),
                    result.autoAppliedTransitions(),
                    result.suggestedTransitions()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private GolemEventBatchCommand toBatchCommand(GolemEventBatchRequest request) {
        return new GolemEventBatchCommand(
                request.schemaVersion(),
                request.golemId(),
                request.events().stream().map(event -> new GolemEventCommand(
                        event.schemaVersion(),
                        event.eventType(),
                        event.golemId(),
                        event.runtimeEventType(),
                        event.signalId(),
                        event.cardId(),
                        event.commandId(),
                        event.requestId(),
                        event.runId(),
                        event.threadId(),
                        event.signalType(),
                        event.summary(),
                        event.details(),
                        event.blockerCode(),
                        event.evidenceRefs() == null
                                ? java.util.List.of()
                                : event.evidenceRefs().stream()
                                        .map(ref -> new GolemEventEvidenceInput(ref.kind(), ref.ref()))
                                        .toList(),
                        event.inputTokens(),
                        event.outputTokens(),
                        event.accumulatedCostMicros(),
                        event.operation(),
                        event.success(),
                        event.errorCode(),
                        event.errorMessage(),
                        event.payload(),
                        event.createdAt())).toList());
    }
}
