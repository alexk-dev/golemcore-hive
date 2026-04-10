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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Organization;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.OrganizationRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonOrganizationRepository implements OrganizationRepository {

    private static final String ORGANIZATION_DIR = "organization";
    private static final String ORGANIZATION_PATH = "primary.json";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Organization> findPrimary() {
        String content = storagePort.getText(ORGANIZATION_DIR, ORGANIZATION_PATH);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Organization.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize organization", exception);
        }
    }

    @Override
    public void save(Organization organization) {
        try {
            storagePort.putTextAtomic(
                    ORGANIZATION_DIR,
                    ORGANIZATION_PATH,
                    objectMapper.writeValueAsString(organization));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize organization", exception);
        }
    }
}
