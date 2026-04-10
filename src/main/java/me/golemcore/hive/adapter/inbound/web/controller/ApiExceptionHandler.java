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

import me.golemcore.hive.adapter.inbound.web.dto.inspection.InspectionErrorResponse;
import me.golemcore.hive.domain.model.InspectionErrorCode;
import me.golemcore.hive.execution.application.InspectionOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InspectionOperationException.class)
    public ResponseEntity<InspectionErrorResponse> handleInspectionException(
            InspectionOperationException exception) {
        return ResponseEntity.status(toHttpStatus(exception.getCode())).body(new InspectionErrorResponse(
                exception.getCode().name(),
                exception.getMessage(),
                exception.getRequestId(),
                exception.isRetryable()));
    }

    private HttpStatus toHttpStatus(InspectionErrorCode errorCode) {
        return switch (errorCode) {
        case NOT_FOUND -> HttpStatus.NOT_FOUND;
        case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
        case REQUEST_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        case GOLEM_OFFLINE, DELIVERY_FAILED -> HttpStatus.CONFLICT;
        default -> HttpStatus.BAD_GATEWAY;
        };
    }

    public record ErrorResponse(String error) {
    }
}
