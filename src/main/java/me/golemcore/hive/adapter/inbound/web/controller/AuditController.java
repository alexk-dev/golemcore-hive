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

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.audit.AuditEventResponse;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.governance.application.port.in.GovernanceOperationsUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final GovernanceOperationsUseCase governanceOperationsUseCase;

    @GetMapping
    public Mono<ResponseEntity<List<AuditEventResponse>>> listAuditEvents(
            Principal principal,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String golemId,
            @RequestParam(required = false) String boardId,
            @RequestParam(required = false) String cardId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<AuditEventResponse> response = governanceOperationsUseCase
                    .listAuditEvents(actorId, golemId, boardId, cardId, from, to, eventType)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getSeverity(),
                event.getActorType(),
                event.getActorId(),
                event.getActorName(),
                event.getTargetType(),
                event.getTargetId(),
                event.getBoardId(),
                event.getCardId(),
                event.getThreadId(),
                event.getGolemId(),
                event.getCommandId(),
                event.getRunId(),
                event.getApprovalId(),
                event.getSummary(),
                event.getDetails(),
                event.getCreatedAt());
    }
}
