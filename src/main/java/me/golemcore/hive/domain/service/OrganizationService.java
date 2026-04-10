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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Organization;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final String ORGANIZATION_DIR = "organization";
    private static final String ORGANIZATION_PATH = "primary.json";
    private static final String ORGANIZATION_ID = "org_primary";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public Organization getOrganization() {
        String content = storagePort.getText(ORGANIZATION_DIR, ORGANIZATION_PATH);
        if (content == null) {
            Organization organization = createDefaultOrganization();
            saveOrganization(organization);
            return organization;
        }
        try {
            return objectMapper.readValue(content, Organization.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize organization", exception);
        }
    }

    public Organization updateOrganization(String name, String description, String actorId, String actorName) {
        Organization organization = getOrganization();
        if (name != null && !name.isBlank()) {
            organization.setName(name);
        }
        if (description != null) {
            organization.setDescription(description);
        }
        organization.setUpdatedAt(Instant.now());
        saveOrganization(organization);
        auditService.record(AuditEvent.builder()
                .eventType("organization.updated")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("ORGANIZATION")
                .targetId(organization.getId())
                .summary("Organization updated")
                .details(organization.getName()));
        return organization;
    }

    private Organization createDefaultOrganization() {
        Instant now = Instant.now();
        return Organization.builder()
                .id(ORGANIZATION_ID)
                .name("Hive Organization")
                .description("Single-tenant Hive control plane")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void saveOrganization(Organization organization) {
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
