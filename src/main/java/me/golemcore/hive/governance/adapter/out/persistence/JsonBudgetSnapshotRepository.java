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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.governance.application.port.out.BudgetSnapshotRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonBudgetSnapshotRepository implements BudgetSnapshotRepositoryPort {

    private static final String BUDGETS_DIR = "budgets";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public void save(BudgetSnapshot budgetSnapshot) {
        try {
            storagePort.putTextAtomic(BUDGETS_DIR, budgetSnapshot.getId() + ".json",
                    objectMapper.writeValueAsString(budgetSnapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize budget snapshot " + budgetSnapshot.getId(), exception);
        }
    }

    @Override
    public void replaceAll(List<BudgetSnapshot> budgetSnapshots) {
        List<SerializedBudgetSnapshot> serializedBudgetSnapshots = new ArrayList<>();
        Set<String> retainedPaths = new LinkedHashSet<>();
        for (BudgetSnapshot budgetSnapshot : budgetSnapshots) {
            String path = budgetSnapshot.getId() + ".json";
            retainedPaths.add(path);
            try {
                serializedBudgetSnapshots.add(new SerializedBudgetSnapshot(
                        path,
                        objectMapper.writeValueAsString(budgetSnapshot)));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Failed to serialize budget snapshot " + budgetSnapshot.getId(),
                        exception);
            }
        }
        for (SerializedBudgetSnapshot serializedBudgetSnapshot : serializedBudgetSnapshots) {
            storagePort.putTextAtomic(
                    BUDGETS_DIR,
                    serializedBudgetSnapshot.path(),
                    serializedBudgetSnapshot.content());
        }
        for (String path : storagePort.listObjects(BUDGETS_DIR, "")) {
            if (!retainedPaths.contains(path)) {
                storagePort.delete(BUDGETS_DIR, path);
            }
        }
    }

    @Override
    public List<BudgetSnapshot> findAll() {
        List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();
        for (String path : storagePort.listObjects(BUDGETS_DIR, "")) {
            String content = storagePort.getText(BUDGETS_DIR, path);
            if (content == null) {
                continue;
            }
            try {
                budgetSnapshots.add(objectMapper.readValue(content, BudgetSnapshot.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to deserialize budget snapshot " + path, exception);
            }
        }
        return budgetSnapshots;
    }

    private record SerializedBudgetSnapshot(String path, String content) {
    }
}
