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
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.governance.application.port.out.PolicyGroupVersionRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonPolicyGroupVersionRepository implements PolicyGroupVersionRepositoryPort {

    private static final String POLICY_GROUP_VERSIONS_DIR = "policy-group-versions";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<PolicyGroupVersion> listByGroupId(String groupId) {
        List<PolicyGroupVersion> versions = new ArrayList<>();
        for (String path : storagePort.listObjects(POLICY_GROUP_VERSIONS_DIR, groupId + "/")) {
            String content = storagePort.getText(POLICY_GROUP_VERSIONS_DIR, path);
            if (content == null) {
                continue;
            }
            versions.add(readPolicyGroupVersion(content, path));
        }
        return versions;
    }

    @Override
    public Optional<PolicyGroupVersion> findByGroupIdAndVersion(String groupId, int version) {
        String path = groupId + "/v" + version + ".json";
        String content = storagePort.getText(POLICY_GROUP_VERSIONS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readPolicyGroupVersion(content, path));
    }

    @Override
    public void save(PolicyGroupVersion policyGroupVersion) {
        try {
            storagePort.ensureDirectory(POLICY_GROUP_VERSIONS_DIR);
            storagePort.putTextAtomic(POLICY_GROUP_VERSIONS_DIR,
                    policyGroupVersion.getPolicyGroupId() + "/v" + policyGroupVersion.getVersion() + ".json",
                    objectMapper.writeValueAsString(policyGroupVersion));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group version "
                    + policyGroupVersion.getPolicyGroupId() + " v" + policyGroupVersion.getVersion(), exception);
        }
    }

    private PolicyGroupVersion readPolicyGroupVersion(String content, String versionRef) {
        try {
            return objectMapper.readValue(content, PolicyGroupVersion.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy group version " + versionRef, exception);
        }
    }
}
