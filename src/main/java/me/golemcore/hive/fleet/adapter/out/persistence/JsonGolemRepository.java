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

package me.golemcore.hive.fleet.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.fleet.application.port.out.GolemRepository;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonGolemRepository implements GolemRepository {

    private static final String GOLEMS_DIR = "golems";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Golem> findById(String golemId) {
        String content = storagePort.getText(GOLEMS_DIR, golemId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readGolem(content, golemId));
    }

    @Override
    public List<Golem> list() {
        List<Golem> golems = new ArrayList<>();
        for (String path : storagePort.listObjects(GOLEMS_DIR, "")) {
            String content = storagePort.getText(GOLEMS_DIR, path);
            if (content == null) {
                continue;
            }
            golems.add(readGolem(content, path));
        }
        return golems;
    }

    @Override
    public void save(Golem golem) {
        try {
            storagePort.putTextAtomic(GOLEMS_DIR, golem.getId() + ".json", objectMapper.writeValueAsString(golem));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize golem " + golem.getId(), exception);
        }
    }

    private Golem readGolem(String content, String golemRef) {
        try {
            return objectMapper.readValue(content, Golem.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize golem " + golemRef, exception);
        }
    }
}
