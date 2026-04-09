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
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.fleet.application.port.out.GolemRoleRepository;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonGolemRoleRepository implements GolemRoleRepository {

    private static final String GOLEM_ROLES_DIR = "golem-roles";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<GolemRole> findBySlug(String slug) {
        String content = storagePort.getText(GOLEM_ROLES_DIR, slug + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readRole(content, slug));
    }

    @Override
    public List<GolemRole> list() {
        List<GolemRole> roles = new ArrayList<>();
        for (String path : storagePort.listObjects(GOLEM_ROLES_DIR, "")) {
            String content = storagePort.getText(GOLEM_ROLES_DIR, path);
            if (content == null) {
                continue;
            }
            roles.add(readRole(content, path));
        }
        return roles;
    }

    @Override
    public void save(GolemRole golemRole) {
        try {
            storagePort.putTextAtomic(GOLEM_ROLES_DIR, golemRole.getSlug() + ".json",
                    objectMapper.writeValueAsString(golemRole));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize role " + golemRole.getSlug(), exception);
        }
    }

    private GolemRole readRole(String content, String roleRef) {
        try {
            return objectMapper.readValue(content, GolemRole.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize role " + roleRef, exception);
        }
    }
}
