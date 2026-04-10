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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.governance.application.port.out.PolicyGroupRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonPolicyGroupRepository implements PolicyGroupRepositoryPort {

    private static final String POLICY_GROUPS_DIR = "policy-groups";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<PolicyGroup> list() {
        List<PolicyGroup> policyGroups = new ArrayList<>();
        for (String path : storagePort.listObjects(POLICY_GROUPS_DIR, "")) {
            String content = storagePort.getText(POLICY_GROUPS_DIR, path);
            if (content == null) {
                continue;
            }
            policyGroups.add(readPolicyGroup(content, path));
        }
        return policyGroups;
    }

    @Override
    public Optional<PolicyGroup> findById(String groupId) {
        String content = storagePort.getText(POLICY_GROUPS_DIR, groupId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readPolicyGroup(content, groupId));
    }

    @Override
    public void save(PolicyGroup policyGroup) {
        try {
            storagePort.ensureDirectory(POLICY_GROUPS_DIR);
            storagePort.putTextAtomic(POLICY_GROUPS_DIR, policyGroup.getId() + ".json",
                    objectMapper.writeValueAsString(policyGroup));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group " + policyGroup.getId(), exception);
        }
    }

    private PolicyGroup readPolicyGroup(String content, String groupRef) {
        try {
            return objectMapper.readValue(content, PolicyGroup.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy group " + groupRef, exception);
        }
    }
}
