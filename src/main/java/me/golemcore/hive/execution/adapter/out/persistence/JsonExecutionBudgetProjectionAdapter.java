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

package me.golemcore.hive.execution.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.shared.budget.BudgetCommandProjection;
import me.golemcore.hive.shared.budget.BudgetCommandProjectionStatus;
import me.golemcore.hive.shared.budget.BudgetExecutionProjectionPort;
import me.golemcore.hive.shared.budget.BudgetRunProjection;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonExecutionBudgetProjectionAdapter implements BudgetExecutionProjectionPort {

    private static final String COMMANDS_DIR = "commands";
    private static final String RUNS_DIR = "runs";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<BudgetCommandProjection> listCommands() {
        List<BudgetCommandProjection> commands = new ArrayList<>();
        for (String path : storagePort.listObjects(COMMANDS_DIR, "")) {
            Optional<BudgetCommandProjection> command = loadCommand(path);
            command.ifPresent(commands::add);
        }
        return commands;
    }

    @Override
    public List<BudgetRunProjection> listRuns() {
        List<BudgetRunProjection> runs = new ArrayList<>();
        for (String path : storagePort.listObjects(RUNS_DIR, "")) {
            Optional<BudgetRunProjection> run = loadRun(path);
            run.ifPresent(runs::add);
        }
        return runs;
    }

    private Optional<BudgetCommandProjection> loadCommand(String path) {
        String content = storagePort.getText(COMMANDS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            CommandRecord command = objectMapper.readValue(content, CommandRecord.class);
            return Optional.of(new BudgetCommandProjection(
                    command.getId(),
                    command.getCardId(),
                    command.getGolemId(),
                    toBudgetCommandProjectionStatus(command.getStatus()),
                    command.getEstimatedCostMicros()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize command " + path, exception);
        }
    }

    private Optional<BudgetRunProjection> loadRun(String path) {
        String content = storagePort.getText(RUNS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            RunProjection run = objectMapper.readValue(content, RunProjection.class);
            return Optional.of(new BudgetRunProjection(
                    run.getId(),
                    run.getCardId(),
                    run.getGolemId(),
                    run.getInputTokens(),
                    run.getOutputTokens(),
                    run.getAccumulatedCostMicros()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize run " + path, exception);
        }
    }

    private BudgetCommandProjectionStatus toBudgetCommandProjectionStatus(CommandStatus status) {
        return switch (status) {
        case PENDING_APPROVAL -> BudgetCommandProjectionStatus.PENDING_APPROVAL;
        case QUEUED -> BudgetCommandProjectionStatus.QUEUED;
        case DELIVERED -> BudgetCommandProjectionStatus.DELIVERED;
        case RUNNING -> BudgetCommandProjectionStatus.RUNNING;
        default -> BudgetCommandProjectionStatus.OTHER;
        };
    }
}
