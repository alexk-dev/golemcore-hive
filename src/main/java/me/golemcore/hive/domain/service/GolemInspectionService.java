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

import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.InspectionErrorCode;
import me.golemcore.hive.domain.model.InspectionRequestBody;
import me.golemcore.hive.domain.model.InspectionRpcResponse;
import me.golemcore.hive.port.outbound.GolemControlDispatchPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GolemInspectionService {

    private final GolemRegistryService golemRegistryService;
    private final GolemControlDispatchPort golemControlDispatchPort;
    private final GolemInspectionRpcService golemInspectionRpcService;
    private final AuditService auditService;

    public Mono<InspectionRpcResponse> execute(
            AuthenticatedActor actor,
            String golemId,
            InspectionRequestBody requestBody) {
        if (golemRegistryService.findGolem(golemId).isEmpty()) {
            return Mono.error(new InspectionException(
                    HttpStatus.NOT_FOUND,
                    InspectionErrorCode.NOT_FOUND,
                    null,
                    "Unknown golem",
                    false));
        }
        if (!golemControlDispatchPort.isConnected(golemId)) {
            return Mono.error(new InspectionException(
                    HttpStatus.CONFLICT,
                    InspectionErrorCode.GOLEM_OFFLINE,
                    null,
                    "Inspection is only available while the golem is online",
                    true));
        }

        return golemInspectionRpcService
                .request(golemId, inspectionThreadId(golemId), null, null, requestBody)
                .map(this::requireSuccessfulResponse)
                .onErrorMap(this::mapFailure)
                .doOnSuccess(response -> recordSuccess(actor, golemId, requestBody, response))
                .doOnError(error -> recordFailure(actor, golemId, requestBody, error));
    }

    private InspectionRpcResponse requireSuccessfulResponse(InspectionRpcResponse response) {
        if (response.success()) {
            return response;
        }
        InspectionErrorCode errorCode = mapRemoteErrorCode(response.errorCode());
        HttpStatus status = switch (errorCode) {
        case NOT_FOUND -> HttpStatus.NOT_FOUND;
        case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        default -> HttpStatus.BAD_GATEWAY;
        };
        boolean retryable = errorCode == InspectionErrorCode.REMOTE_ERROR;
        throw new InspectionException(
                status,
                errorCode,
                response.requestId(),
                firstNonBlank(response.errorMessage(), "Inspection request failed"),
                retryable);
    }

    private Throwable mapFailure(Throwable throwable) {
        if (throwable instanceof InspectionException) {
            return throwable;
        }
        if (throwable instanceof TimeoutException) {
            return new InspectionException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    InspectionErrorCode.REQUEST_TIMEOUT,
                    null,
                    "Inspection request timed out",
                    true);
        }
        if (throwable instanceof IllegalArgumentException) {
            return new InspectionException(
                    HttpStatus.BAD_REQUEST,
                    InspectionErrorCode.INVALID_REQUEST,
                    null,
                    firstNonBlank(throwable.getMessage(), "Invalid inspection request"),
                    false);
        }
        if (throwable instanceof IllegalStateException) {
            return new InspectionException(
                    HttpStatus.CONFLICT,
                    InspectionErrorCode.DELIVERY_FAILED,
                    null,
                    firstNonBlank(throwable.getMessage(), "Failed to deliver inspection request"),
                    true);
        }
        return new InspectionException(
                HttpStatus.BAD_GATEWAY,
                InspectionErrorCode.REMOTE_ERROR,
                null,
                firstNonBlank(throwable.getMessage(), "Inspection request failed"),
                true);
    }

    private void recordSuccess(
            AuthenticatedActor actor,
            String golemId,
            InspectionRequestBody requestBody,
            InspectionRpcResponse response) {
        auditService.record(AuditEvent.builder()
                .eventType("golem.inspection.requested")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actor.getSubjectId())
                .actorName(actor.getName())
                .targetType("GOLEM")
                .targetId(golemId)
                .golemId(golemId)
                .summary("Inspection operation completed")
                .details(requestBody.getOperation() + " requestId=" + response.requestId()));
    }

    private void recordFailure(
            AuthenticatedActor actor,
            String golemId,
            InspectionRequestBody requestBody,
            Throwable error) {
        InspectionException inspectionException = error instanceof InspectionException
                ? (InspectionException) error
                : new InspectionException(
                        HttpStatus.BAD_GATEWAY,
                        InspectionErrorCode.REMOTE_ERROR,
                        null,
                        firstNonBlank(error.getMessage(), "Inspection request failed"),
                        true);
        auditService.record(AuditEvent.builder()
                .eventType("golem.inspection.failed")
                .severity("WARN")
                .actorType("OPERATOR")
                .actorId(actor.getSubjectId())
                .actorName(actor.getName())
                .targetType("GOLEM")
                .targetId(golemId)
                .golemId(golemId)
                .summary("Inspection operation failed")
                .details(requestBody.getOperation() + " " + inspectionException.getCode() + " "
                        + inspectionException.getMessage()));
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

    @Getter
    public static class InspectionException extends RuntimeException {

        private final HttpStatus status;
        private final InspectionErrorCode code;
        private final String requestId;
        private final boolean retryable;

        public InspectionException(
                HttpStatus status,
                InspectionErrorCode code,
                String requestId,
                String message,
                boolean retryable) {
            super(message);
            this.status = status;
            this.code = code;
            this.requestId = requestId;
            this.retryable = retryable;
        }
    }
}
