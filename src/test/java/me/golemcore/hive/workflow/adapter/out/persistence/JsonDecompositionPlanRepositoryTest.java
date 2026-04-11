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

package me.golemcore.hive.workflow.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import me.golemcore.hive.config.JacksonConfig;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanStatus;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.junit.jupiter.api.Test;

class JsonDecompositionPlanRepositoryTest {

    @Test
    void shouldRoundTripDecompositionPlanJson() {
        StoragePort storagePort = mock(StoragePort.class);
        JsonDecompositionPlanRepository repository = new JsonDecompositionPlanRepository(
                storagePort,
                new JacksonConfig().objectMapper());

        DecompositionPlan plan = DecompositionPlan.builder()
                .id("dplan-1")
                .sourceCardId("card-source")
                .serviceId("service-1")
                .status(DecompositionPlanStatus.DRAFT)
                .createdAt(Instant.parse("2026-04-10T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-10T00:00:00Z"))
                .items(List.of(DecompositionPlanItem.builder()
                        .clientItemId("task-1")
                        .title("Task 1")
                        .prompt("Prompt 1")
                        .acceptanceCriteria(List.of("done"))
                        .assignment(null)
                        .build()))
                .build();

        repository.save(plan);
        when(storagePort.getText("decomposition-plans", "dplan-1.json")).thenReturn("""
                {
                  "id":"dplan-1",
                  "sourceCardId":"card-source",
                  "serviceId":"service-1",
                  "status":"DRAFT",
                  "createdAt":"2026-04-10T00:00:00Z",
                  "updatedAt":"2026-04-10T00:00:00Z",
                  "items":[
                    {
                      "clientItemId":"task-1",
                      "title":"Task 1",
                      "prompt":"Prompt 1",
                      "acceptanceCriteria":["done"],
                      "legacyField":"ignored"
                    }
                  ]
                }
                """);

        DecompositionPlan restored = repository.findById("dplan-1").orElseThrow();

        assertEquals("card-source", restored.getSourceCardId());
        assertEquals(DecompositionPlanStatus.DRAFT, restored.getStatus());
        assertEquals(1, restored.getItems().size());
        assertEquals("task-1", restored.getItems().getFirst().getClientItemId());
        assertNull(restored.getItems().getFirst().getAssignment());
        assertTrue(repository.findById("missing").isEmpty());
    }
}
