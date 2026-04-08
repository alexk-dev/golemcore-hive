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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.model.ApprovalStatus;
import me.golemcore.hive.domain.model.ApprovalSubjectType;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.port.outbound.StoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ApprovalServiceSelfEvolvingTest {

    @Test
    void shouldCreatePromotionApprovalWithoutCommandRecord() {
        StoragePort storagePort = mock(StoragePort.class);
        ThreadService threadService = mock(ThreadService.class);
        GolemRegistryService golemRegistryService = mock(GolemRegistryService.class);
        AuditService auditService = mock(AuditService.class);
        NotificationService notificationService = mock(NotificationService.class);
        OperatorUpdatesService operatorUpdatesService = mock(OperatorUpdatesService.class);
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        when(notificationService.isApprovalRequestedEnabled()).thenReturn(false);
        ApprovalService approvalService = new ApprovalService(
                storagePort,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new HiveProperties(),
                threadService,
                golemRegistryService,
                auditService,
                notificationService,
                operatorUpdatesService,
                applicationEventPublisher);

        SelfEvolvingCandidateProjection candidate = SelfEvolvingCandidateProjection.builder()
                .id("candidate-1")
                .golemId("golem-1")
                .goal("fix")
                .artifactType("skill")
                .status("approved_pending")
                .riskLevel("medium")
                .expectedImpact("Reduce routing failures")
                .sourceRunIds(java.util.List.of("run-1"))
                .build();

        ApprovalRequest approval = approvalService.createPromotionApproval(candidate, "operator-1", "Hive Admin");

        assertNotNull(approval.getId());
        assertEquals(ApprovalSubjectType.SELF_EVOLVING_PROMOTION, approval.getSubjectType());
        assertEquals(ApprovalStatus.PENDING, approval.getStatus());
        assertNull(approval.getCommandId());
        assertEquals("candidate-1", approval.getPromotionContext().getCandidateId());
        assertEquals("skill", approval.getPromotionContext().getArtifactType());
        verify(storagePort).putTextAtomic(anyString(), anyString(), anyString());
    }
}
