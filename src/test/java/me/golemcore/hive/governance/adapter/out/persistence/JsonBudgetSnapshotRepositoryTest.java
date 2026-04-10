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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import me.golemcore.hive.domain.model.BudgetScopeType;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JsonBudgetSnapshotRepositoryTest {

    @Test
    void shouldWriteCurrentSnapshotsBeforeDeletingStaleOnes() throws Exception {
        StoragePort storagePort = mock(StoragePort.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonBudgetSnapshotRepository repository = new JsonBudgetSnapshotRepository(storagePort, objectMapper);
        BudgetSnapshot budgetSnapshot = budgetSnapshot("budget-1");
        when(objectMapper.writeValueAsString(budgetSnapshot)).thenReturn("{\"id\":\"budget-1\"}");
        when(storagePort.listObjects("budgets", "")).thenReturn(List.of("budget-1.json", "stale.json"));

        repository.replaceAll(List.of(budgetSnapshot));

        InOrder inOrder = inOrder(storagePort);
        inOrder.verify(storagePort).putTextAtomic("budgets", "budget-1.json", "{\"id\":\"budget-1\"}");
        inOrder.verify(storagePort).delete("budgets", "stale.json");
    }

    @Test
    void shouldNotDeleteStaleSnapshotsWhenSerializationFails() throws Exception {
        StoragePort storagePort = mock(StoragePort.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonBudgetSnapshotRepository repository = new JsonBudgetSnapshotRepository(storagePort, objectMapper);
        BudgetSnapshot budgetSnapshot = budgetSnapshot("budget-1");
        when(objectMapper.writeValueAsString(budgetSnapshot)).thenThrow(mock(JsonProcessingException.class));

        assertThrows(IllegalStateException.class, () -> repository.replaceAll(List.of(budgetSnapshot)));

        verify(storagePort, never()).putTextAtomic(anyString(), anyString(), anyString());
        verify(storagePort, never()).delete(anyString(), anyString());
    }

    private BudgetSnapshot budgetSnapshot(String id) {
        return BudgetSnapshot.builder()
                .id(id)
                .scopeType(BudgetScopeType.SYSTEM)
                .scopeId("system")
                .scopeLabel("System")
                .updatedAt(Instant.parse("2026-04-09T00:00:00Z"))
                .build();
    }
}
