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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.ObjectiveRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonObjectiveRepository implements ObjectiveRepository {

    private static final String OBJECTIVES_DIR = "objectives";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<Objective> list() {
        List<Objective> objectives = new ArrayList<>();
        for (String path : storagePort.listObjects(OBJECTIVES_DIR, "")) {
            String content = storagePort.getText(OBJECTIVES_DIR, path);
            if (content == null) {
                continue;
            }
            objectives.add(readObjective(content, path));
        }
        return objectives;
    }

    @Override
    public Optional<Objective> findById(String objectiveId) {
        String content = storagePort.getText(OBJECTIVES_DIR, objectiveId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readObjective(content, objectiveId));
    }

    @Override
    public void save(Objective objective) {
        try {
            storagePort.putTextAtomic(
                    OBJECTIVES_DIR,
                    objective.getId() + ".json",
                    objectMapper.writeValueAsString(objective));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize objective " + objective.getId(), exception);
        }
    }

    private Objective readObjective(String content, String objectiveRef) {
        try {
            return objectMapper.readValue(content, Objective.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize objective " + objectiveRef, exception);
        }
    }
}
