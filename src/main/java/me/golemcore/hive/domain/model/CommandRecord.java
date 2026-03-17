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

package me.golemcore.hive.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandRecord {

    @Builder.Default
    private int schemaVersion = 1;

    private String id;
    private String threadId;
    private String cardId;
    private String golemId;
    private String runId;
    private String body;
    private String approvalRequestId;
    private ApprovalRiskLevel approvalRiskLevel;
    private String approvalReason;
    private long estimatedCostMicros;
    private CommandStatus status;
    private String queueReason;
    private int dispatchAttempts;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastDispatchAttemptAt;
    private Instant deliveredAt;
    private Instant startedAt;
    private Instant completedAt;
}
