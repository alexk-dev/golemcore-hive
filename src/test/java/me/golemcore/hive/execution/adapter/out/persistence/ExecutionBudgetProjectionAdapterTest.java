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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import me.golemcore.hive.domain.model.CommandRecord;
import me.golemcore.hive.domain.model.CommandStatus;
import me.golemcore.hive.domain.model.RunProjection;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.shared.budget.BudgetCommandProjectionStatus;
import org.junit.jupiter.api.Test;

class ExecutionBudgetProjectionAdapterTest {

    @Test
    void shouldMapExecutionStorageToBudgetProjections() throws Exception {
        StoragePort storagePort = mock(StoragePort.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonExecutionBudgetProjectionAdapter adapter = new JsonExecutionBudgetProjectionAdapter(
                storagePort,
                objectMapper);
        CommandRecord commandRecord = CommandRecord.builder()
                .id("cmd-1")
                .cardId("card-1")
                .golemId("golem-1")
                .status(CommandStatus.QUEUED)
                .estimatedCostMicros(42L)
                .build();
        RunProjection runProjection = RunProjection.builder()
                .id("run-1")
                .cardId("card-1")
                .golemId("golem-1")
                .inputTokens(100L)
                .outputTokens(50L)
                .accumulatedCostMicros(21L)
                .build();
        when(storagePort.listObjects("commands", "")).thenReturn(List.of("cmd-1.json"));
        when(storagePort.listObjects("runs", "")).thenReturn(List.of("run-1.json"));
        when(storagePort.getText("commands", "cmd-1.json")).thenReturn(objectMapper.writeValueAsString(commandRecord));
        when(storagePort.getText("runs", "run-1.json")).thenReturn(objectMapper.writeValueAsString(runProjection));

        assertEquals(1, adapter.listCommands().size());
        assertEquals(BudgetCommandProjectionStatus.QUEUED, adapter.listCommands().getFirst().status());
        assertEquals(42L, adapter.listCommands().getFirst().estimatedCostMicros());
        assertEquals(1, adapter.listRuns().size());
        assertEquals(21L, adapter.listRuns().getFirst().accumulatedCostMicros());
    }
}
