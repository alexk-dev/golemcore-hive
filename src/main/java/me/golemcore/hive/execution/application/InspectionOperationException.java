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

package me.golemcore.hive.execution.application;

import lombok.Getter;
import me.golemcore.hive.domain.model.InspectionErrorCode;

@Getter
public class InspectionOperationException extends RuntimeException {

    private final InspectionErrorCode code;
    private final String requestId;
    private final boolean retryable;

    public InspectionOperationException(
            InspectionErrorCode code,
            String requestId,
            String message,
            boolean retryable) {
        super(message);
        this.code = code;
        this.requestId = requestId;
        this.retryable = retryable;
    }
}
