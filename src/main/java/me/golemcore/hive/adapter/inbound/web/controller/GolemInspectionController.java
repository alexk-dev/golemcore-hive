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

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionRpcResponse;
import me.golemcore.hive.execution.application.InspectionActor;
import me.golemcore.hive.execution.application.port.in.GolemInspectionUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/golems/{golemId}/inspection")
@RequiredArgsConstructor
public class GolemInspectionController {

    private final GolemInspectionUseCase golemInspectionUseCase;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/sessions")
    public Mono<ResponseEntity<Object>> listSessions(
            Principal principal,
            @PathVariable String golemId,
            @RequestParam(required = false) String channel) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, InspectionRequestBody.builder()
                .operation("sessions.list")
                .channel(channel)
                .build()).map(response -> ResponseEntity.ok(response.payload()));
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Object>> getSession(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        return executeJson(principal, golemId, InspectionRequestBody.builder()
                .operation("session.detail")
                .sessionId(sessionId)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Mono<ResponseEntity<Object>> getSessionMessages(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String beforeMessageId) {
        return executeJson(principal, golemId, InspectionRequestBody.builder()
                .operation("session.messages")
                .sessionId(sessionId)
                .limit(limit)
                .beforeMessageId(beforeMessageId)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/trace/summary")
    public Mono<ResponseEntity<Object>> getSessionTraceSummary(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        return executeJson(principal, golemId, InspectionRequestBody.builder()
                .operation("session.trace.summary")
                .sessionId(sessionId)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/trace")
    public Mono<ResponseEntity<Object>> getSessionTrace(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        return executeJson(principal, golemId, InspectionRequestBody.builder()
                .operation("session.trace.detail")
                .sessionId(sessionId)
                .build());
    }

    @GetMapping("/sessions/{sessionId}/trace/export")
    public Mono<ResponseEntity<Object>> exportSessionTrace(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, InspectionRequestBody.builder()
                .operation("session.trace.export")
                .sessionId(sessionId)
                .build()).map(
                        response -> ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"session-trace-" + sanitizeExportName(sessionId)
                                                + ".json\"")
                                .body(response.payload()));
    }

    @GetMapping("/sessions/{sessionId}/trace/snapshots/{snapshotId}/payload")
    public Mono<ResponseEntity<String>> exportSessionTraceSnapshotPayload(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId,
            @PathVariable String snapshotId) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, InspectionRequestBody.builder()
                .operation("session.trace.snapshot.payload")
                .sessionId(sessionId)
                .snapshotId(snapshotId)
                .build()).map(response -> toSnapshotPayloadResponse(response, sessionId, snapshotId));
    }

    @PostMapping("/sessions/{sessionId}/compact")
    public Mono<ResponseEntity<Object>> compactSession(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "20") int keepLast) {
        return executeJson(principal, golemId, InspectionRequestBody.builder()
                .operation("session.compact")
                .sessionId(sessionId)
                .keepLast(keepLast)
                .build());
    }

    @PostMapping("/sessions/{sessionId}/clear")
    public Mono<ResponseEntity<Void>> clearSession(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, InspectionRequestBody.builder()
                .operation("session.clear")
                .sessionId(sessionId)
                .build()).map(ignored -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ResponseEntity<Void>> deleteSession(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String sessionId) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, InspectionRequestBody.builder()
                .operation("session.delete")
                .sessionId(sessionId)
                .build()).map(ignored -> ResponseEntity.noContent().build());
    }

    private Mono<ResponseEntity<Object>> executeJson(
            Principal principal,
            String golemId,
            InspectionRequestBody requestBody) {
        AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
        return executeInspection(toInspectionActor(actor), golemId, requestBody)
                .map(response -> ResponseEntity.ok(response.payload()));
    }

    private Mono<InspectionRpcResponse> executeInspection(
            InspectionActor actor,
            String golemId,
            InspectionRequestBody requestBody) {
        CompletableFuture<InspectionRpcResponse> responseFuture = golemInspectionUseCase.execute(actor, golemId,
                requestBody);
        return Mono.fromFuture(responseFuture);
    }

    private ResponseEntity<String> toSnapshotPayloadResponse(
            InspectionRpcResponse response,
            String sessionId,
            String snapshotId) {
        JsonNode payload = objectMapper.valueToTree(response.payload());
        String payloadText = payload.path("payloadText").asText();
        String fileExtension = payload.path("fileExtension").asText(".txt");
        String contentTypeValue = payload.path("contentType").asText(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(contentTypeValue);
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"session-trace-" + sanitizeExportName(sessionId)
                                + "-snapshot-" + sanitizeExportName(snapshotId) + fileExtension + "\"")
                .body(payloadText);
    }

    private String sanitizeExportName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private InspectionActor toInspectionActor(AuthenticatedActor actor) {
        return new InspectionActor(actor.getSubjectId(), actor.getName());
    }
}
