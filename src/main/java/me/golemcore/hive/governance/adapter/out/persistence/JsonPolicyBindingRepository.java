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
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.governance.application.port.out.PolicyBindingRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonPolicyBindingRepository implements PolicyBindingRepositoryPort {

    private static final String GOLEM_POLICY_BINDINGS_DIR = "golem-policy-bindings";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<GolemPolicyBinding> findByGolemId(String golemId) {
        String content = storagePort.getText(GOLEM_POLICY_BINDINGS_DIR, golemId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readBinding(content, golemId));
    }

    @Override
    public List<GolemPolicyBinding> list() {
        List<GolemPolicyBinding> bindings = new ArrayList<>();
        for (String path : storagePort.listObjects(GOLEM_POLICY_BINDINGS_DIR, "")) {
            String content = storagePort.getText(GOLEM_POLICY_BINDINGS_DIR, path);
            if (content == null) {
                continue;
            }
            bindings.add(readBinding(content, path));
        }
        return bindings;
    }

    @Override
    public void save(GolemPolicyBinding binding) {
        try {
            storagePort.ensureDirectory(GOLEM_POLICY_BINDINGS_DIR);
            storagePort.putTextAtomic(GOLEM_POLICY_BINDINGS_DIR, binding.getGolemId() + ".json",
                    objectMapper.writeValueAsString(binding));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy binding for golem " + binding.getGolemId(),
                    exception);
        }
    }

    @Override
    public void deleteByGolemId(String golemId) {
        storagePort.delete(GOLEM_POLICY_BINDINGS_DIR, golemId + ".json");
    }

    private GolemPolicyBinding readBinding(String content, String bindingRef) {
        try {
            return objectMapper.readValue(content, GolemPolicyBinding.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy binding " + bindingRef, exception);
        }
    }
}
