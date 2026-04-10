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

package me.golemcore.hive.governance.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.governance.application.port.out.PolicyGolemPort;
import me.golemcore.hive.governance.application.port.out.PolicySyncDispatchPort;
import org.junit.jupiter.api.Test;

class PolicyRolloutApplicationServiceTest {

    @Test
    void shouldRequestSyncWhenGolemSupportsPolicySync() {
        PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase = mock(
                PolicyGroupAdministrationUseCase.class);
        PolicyGolemPort policyGolemPort = mock(PolicyGolemPort.class);
        PolicySyncDispatchPort policySyncDispatchPort = mock(PolicySyncDispatchPort.class);
        PolicyRolloutApplicationService service = new PolicyRolloutApplicationService(
                policyGroupAdministrationUseCase,
                policyGolemPort,
                policySyncDispatchPort);

        GolemPolicyBinding binding = GolemPolicyBinding.builder()
                .golemId("golem_1")
                .policyGroupId("pg_1")
                .targetVersion(3)
                .build();
        Golem golem = Golem.builder()
                .id("golem_1")
                .supportedChannels(Set.of("control"))
                .capabilitySnapshot(GolemCapabilitySnapshot.builder()
                        .enabledAutonomyFeatures(Set.of("policy-sync-v1"))
                        .build())
                .build();
        PolicyGroupVersion version = PolicyGroupVersion.builder()
                .policyGroupId("pg_1")
                .version(3)
                .checksum("sha256")
                .build();
        when(policyGolemPort.findGolem("golem_1")).thenReturn(Optional.of(golem));
        when(policySyncDispatchPort.isConnected("golem_1")).thenReturn(true);
        when(policyGroupAdministrationUseCase.getVersion("pg_1", 3)).thenReturn(version);
        when(policySyncDispatchPort.requestSync(eq("golem_1"), eq("pg_1"), eq(3), eq("sha256"), any()))
                .thenReturn(true);

        boolean delivered = service.requestSyncIfSupported(binding);

        assertTrue(delivered);
        verify(policySyncDispatchPort).requestSync(
                eq("golem_1"),
                eq("pg_1"),
                eq(3),
                eq("sha256"),
                any());
    }

    @Test
    void shouldSkipSyncWhenGolemDoesNotAdvertisePolicySync() {
        PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase = mock(
                PolicyGroupAdministrationUseCase.class);
        PolicyGolemPort policyGolemPort = mock(PolicyGolemPort.class);
        PolicySyncDispatchPort policySyncDispatchPort = mock(PolicySyncDispatchPort.class);
        PolicyRolloutApplicationService service = new PolicyRolloutApplicationService(
                policyGroupAdministrationUseCase,
                policyGolemPort,
                policySyncDispatchPort);

        GolemPolicyBinding binding = GolemPolicyBinding.builder()
                .golemId("golem_1")
                .policyGroupId("pg_1")
                .targetVersion(3)
                .build();
        Golem golem = Golem.builder()
                .id("golem_1")
                .supportedChannels(Set.of("control"))
                .capabilitySnapshot(GolemCapabilitySnapshot.builder().enabledAutonomyFeatures(Set.of()).build())
                .build();
        when(policyGolemPort.findGolem("golem_1")).thenReturn(Optional.of(golem));

        boolean delivered = service.requestSyncIfSupported(binding);

        assertFalse(delivered);
        verifyNoInteractions(policyGroupAdministrationUseCase, policySyncDispatchPort);
    }
}
