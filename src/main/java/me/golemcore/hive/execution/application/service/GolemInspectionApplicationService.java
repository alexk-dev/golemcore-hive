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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.InspectionErrorCode;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionResponseEvent;
import me.golemcore.hive.domain.model.InspectionRpcResponse;
import me.golemcore.hive.execution.application.InspectionActor;
import me.golemcore.hive.execution.application.InspectionOperationException;
import me.golemcore.hive.execution.application.port.in.GolemInspectionResponseUseCase;
import me.golemcore.hive.execution.application.port.in.GolemInspectionUseCase;
import me.golemcore.hive.execution.application.port.out.InspectionAuditPort;
import me.golemcore.hive.execution.application.port.out.InspectionDispatchPort;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;

public class GolemInspectionApplicationService implements GolemInspectionUseCase, GolemInspectionResponseUseCase {

    private static final String EVENT_TYPE_INSPECTION_REQUEST = "inspection.request";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final InspectionDispatchPort inspectionDispatchPort;
    private final InspectionAuditPort inspectionAuditPort;
    private final Duration requestTimeout;
    private final Map<String, CompletableFuture<InspectionRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    public GolemInspectionApplicationService(
            GolemDirectoryUseCase golemDirectoryUseCase,
            InspectionDispatchPort inspectionDispatchPort,
            InspectionAuditPort inspectionAuditPort) {
        this(golemDirectoryUseCase, inspectionDispatchPort, inspectionAuditPort, DEFAULT_TIMEOUT);
    }

    public GolemInspectionApplicationService(
            GolemDirectoryUseCase golemDirectoryUseCase,
            InspectionDispatchPort inspectionDispatchPort,
            InspectionAuditPort inspectionAuditPort,
            Duration requestTimeout) {
        this.golemDirectoryUseCase = golemDirectoryUseCase;
        this.inspectionDispatchPort = inspectionDispatchPort;
        this.inspectionAuditPort = inspectionAuditPort;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public CompletableFuture<InspectionRpcResponse> execute(
            InspectionActor actor,
            String golemId,
            InspectionRequestBody requestBody) {
        if (golemDirectoryUseCase.findGolem(golemId).isEmpty()) {
            return failedInspection(new InspectionOperationException(
                    InspectionErrorCode.NOT_FOUND,
                    null,
                    "Unknown golem",
                    false));
        }
        if (!inspectionDispatchPort.isConnected(golemId)) {
            return failedInspection(new InspectionOperationException(
                    InspectionErrorCode.GOLEM_OFFLINE,
                    null,
                    "Inspection is only available while the golem is online",
                    true));
        }

        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        CompletableFuture<InspectionRpcResponse> pendingResponse = new CompletableFuture<>();
        pendingRequests.put(requestId, pendingResponse);

        ControlCommandEnvelope envelope = ControlCommandEnvelope.builder()
                .eventType(EVENT_TYPE_INSPECTION_REQUEST)
                .requestId(requestId)
                .threadId(inspectionThreadId(golemId))
                .golemId(golemId)
                .inspection(requestBody)
                .createdAt(Instant.now())
                .build();
        if (!inspectionDispatchPort.send(golemId, envelope)) {
            pendingRequests.remove(requestId);
            InspectionOperationException exception = new InspectionOperationException(
                    InspectionErrorCode.DELIVERY_FAILED,
                    null,
                    "Failed to deliver inspection request",
                    true);
            inspectionAuditPort.recordFailure(actor, golemId, requestBody, exception);
            return failedInspection(exception);
        }

        CompletableFuture<InspectionRpcResponse> result = new CompletableFuture<>();
        pendingResponse.orTimeout(requestTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> completeInspectionResult(
                        actor,
                        golemId,
                        requestBody,
                        requestId,
                        response,
                        throwable,
                        result));
        return result;
    }

    @Override
    public boolean handleInspectionResponse(InspectionResponseEvent event) {
        if (event == null || event.requestId() == null || event.requestId().isBlank()) {
            return false;
        }
        CompletableFuture<InspectionRpcResponse> pendingResponse = pendingRequests.remove(event.requestId());
        if (pendingResponse == null) {
            return false;
        }
        InspectionRpcResponse response = InspectionRpcResponse.builder()
                .requestId(event.requestId())
                .operation(event.operation())
                .success(Boolean.TRUE.equals(event.success()))
                .errorCode(event.errorCode())
                .errorMessage(event.errorMessage())
                .payload(event.payload())
                .createdAt(event.createdAt())
                .build();
        pendingResponse.complete(response);
        return true;
    }

    private void completeInspectionResult(
            InspectionActor actor,
            String golemId,
            InspectionRequestBody requestBody,
            String requestId,
            InspectionRpcResponse response,
            Throwable throwable,
            CompletableFuture<InspectionRpcResponse> result) {
        pendingRequests.remove(requestId);
        if (throwable != null) {
            InspectionOperationException exception = mapFailure(unwrapCompletion(throwable));
            inspectionAuditPort.recordFailure(actor, golemId, requestBody, exception);
            result.completeExceptionally(exception);
            return;
        }
        try {
            InspectionRpcResponse successfulResponse = requireSuccessfulResponse(response);
            inspectionAuditPort.recordSuccess(actor, golemId, requestBody, successfulResponse);
            result.complete(successfulResponse);
        } catch (InspectionOperationException exception) {
            inspectionAuditPort.recordFailure(actor, golemId, requestBody, exception);
            result.completeExceptionally(exception);
        }
    }

    private InspectionRpcResponse requireSuccessfulResponse(InspectionRpcResponse response) {
        if (response.success()) {
            return response;
        }
        InspectionErrorCode errorCode = mapRemoteErrorCode(response.errorCode());
        boolean retryable = errorCode == InspectionErrorCode.REMOTE_ERROR;
        throw new InspectionOperationException(
                errorCode,
                response.requestId(),
                firstNonBlank(response.errorMessage(), "Inspection request failed"),
                retryable);
    }

    private InspectionOperationException mapFailure(Throwable throwable) {
        if (throwable instanceof InspectionOperationException inspectionOperationException) {
            return inspectionOperationException;
        }
        if (throwable instanceof TimeoutException) {
            return new InspectionOperationException(
                    InspectionErrorCode.REQUEST_TIMEOUT,
                    null,
                    "Inspection request timed out",
                    true);
        }
        if (throwable instanceof IllegalArgumentException) {
            return new InspectionOperationException(
                    InspectionErrorCode.INVALID_REQUEST,
                    null,
                    firstNonBlank(throwable.getMessage(), "Invalid inspection request"),
                    false);
        }
        if (throwable instanceof IllegalStateException) {
            return new InspectionOperationException(
                    InspectionErrorCode.DELIVERY_FAILED,
                    null,
                    firstNonBlank(throwable.getMessage(), "Failed to deliver inspection request"),
                    true);
        }
        return new InspectionOperationException(
                InspectionErrorCode.REMOTE_ERROR,
                null,
                firstNonBlank(throwable.getMessage(), "Inspection request failed"),
                true);
    }

    private InspectionErrorCode mapRemoteErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return InspectionErrorCode.REMOTE_ERROR;
        }
        try {
            return InspectionErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException exception) {
            if ("INVALID_REQUEST".equals(errorCode)) {
                return InspectionErrorCode.INVALID_REQUEST;
            }
            if ("NOT_FOUND".equals(errorCode)) {
                return InspectionErrorCode.NOT_FOUND;
            }
            return InspectionErrorCode.REMOTE_ERROR;
        }
    }

    private String inspectionThreadId(String golemId) {
        return "inspection:" + golemId;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private Throwable unwrapCompletion(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private CompletableFuture<InspectionRpcResponse> failedInspection(InspectionOperationException exception) {
        CompletableFuture<InspectionRpcResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(exception);
        return failedFuture;
    }
}
