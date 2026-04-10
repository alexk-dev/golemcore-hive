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

package me.golemcore.hive.workflow.application.service;

import java.time.Instant;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Organization;
import me.golemcore.hive.workflow.application.port.in.OrganizationWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.OrganizationRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;

public class OrganizationService implements OrganizationWorkflowUseCase {

    private static final String ORGANIZATION_ID = "org_primary";

    private final OrganizationRepository organizationRepository;
    private final WorkflowAuditPort workflowAuditPort;

    public OrganizationService(OrganizationRepository organizationRepository, WorkflowAuditPort workflowAuditPort) {
        this.organizationRepository = organizationRepository;
        this.workflowAuditPort = workflowAuditPort;
    }

    @Override
    public Organization getOrganization() {
        return organizationRepository.findPrimary().orElseGet(() -> {
            Organization organization = createDefaultOrganization();
            organizationRepository.save(organization);
            return organization;
        });
    }

    @Override
    public Organization updateOrganization(String name, String description, String actorId, String actorName) {
        Organization organization = getOrganization();
        if (name != null && !name.isBlank()) {
            organization.setName(name);
        }
        if (description != null) {
            organization.setDescription(description);
        }
        organization.setUpdatedAt(Instant.now());
        organizationRepository.save(organization);
        workflowAuditPort.record(AuditEvent.builder()
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
}
