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

package me.golemcore.hive.governance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.RunStatus;
import me.golemcore.hive.governance.application.port.out.ApprovalCommandStatePort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonApprovalCommandStateAdapter implements ApprovalCommandStatePort {

    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public void markCommandQueued(String commandId, String queueReason, Instant updatedAt) {
        updateCommand(commandId, commandRecord -> {
            commandRecord.setStatus(CommandStatus.QUEUED);
            commandRecord.setQueueReason(queueReason);
            commandRecord.setUpdatedAt(updatedAt);
            commandRecord.setCompletedAt(null);
        });
    }

    @Override
    public void markRunQueued(String runId, Instant updatedAt) {
        updateRun(runId, runProjection -> {
            runProjection.setStatus(RunStatus.QUEUED);
            runProjection.setUpdatedAt(updatedAt);
            runProjection.setCompletedAt(null);
        });
    }

    @Override
    public void markCommandRejected(String commandId, String queueReason, Instant updatedAt, Instant completedAt) {
        updateCommand(commandId, commandRecord -> {
            commandRecord.setStatus(CommandStatus.REJECTED);
            commandRecord.setQueueReason(queueReason);
            commandRecord.setUpdatedAt(updatedAt);
            commandRecord.setCompletedAt(completedAt);
        });
    }

    @Override
    public void markRunRejected(String runId, Instant updatedAt, Instant completedAt) {
        updateRun(runId, runProjection -> {
            runProjection.setStatus(RunStatus.REJECTED);
            runProjection.setUpdatedAt(updatedAt);
            runProjection.setCompletedAt(completedAt);
        });
    }

    private void updateCommand(String commandId, CommandMutator commandMutator) {
        String content = storagePort.getText(COMMANDS_DIR, commandId + ".json");
        if (content == null) {
            throw new IllegalArgumentException("Unknown command: " + commandId);
        }
        try {
            CommandRecord commandRecord = objectMapper.readValue(content, CommandRecord.class);
            commandMutator.mutate(commandRecord);
            storagePort.putTextAtomic(COMMANDS_DIR, commandRecord.getId() + ".json",
                    objectMapper.writeValueAsString(commandRecord));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist command " + commandId, exception);
        }
    }

    private void updateRun(String runId, RunMutator runMutator) {
        String content = storagePort.getText(RUNS_DIR, runId + ".json");
        if (content == null) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        try {
            RunProjection runProjection = objectMapper.readValue(content, RunProjection.class);
            runMutator.mutate(runProjection);
            storagePort.putTextAtomic(RUNS_DIR, runProjection.getId() + ".json",
                    objectMapper.writeValueAsString(runProjection));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist run " + runId, exception);
        }
    }

    @FunctionalInterface
    private interface CommandMutator {
        void mutate(CommandRecord commandRecord);
    }

    @FunctionalInterface
    private interface RunMutator {
        void mutate(RunProjection runProjection);
    }
}
