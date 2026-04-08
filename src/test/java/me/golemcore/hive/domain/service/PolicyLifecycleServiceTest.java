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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import org.junit.jupiter.api.Test;

class PolicyLifecycleServiceTest {

    @Test
    void shouldRequestGroupSyncAfterPublish() {
        PolicyGroupService policyGroupService = mock(PolicyGroupService.class);
        PolicyRolloutService policyRolloutService = mock(PolicyRolloutService.class);
        PolicyLifecycleService service = new PolicyLifecycleService(policyGroupService, policyRolloutService);
        PolicyGroupVersion version = PolicyGroupVersion.builder()
                .policyGroupId("pg_1")
                .version(2)
                .build();
        when(policyGroupService.publish("pg_1", "Switch provider", "op_1", "Hive Admin")).thenReturn(version);

        PolicyGroupVersion result = service.publish("pg_1", "Switch provider", "op_1", "Hive Admin");

        assertSame(version, result);
        verify(policyRolloutService).requestSyncForGroup("pg_1");
    }

    @Test
    void shouldRequestGroupSyncAfterRollback() {
        PolicyGroupService policyGroupService = mock(PolicyGroupService.class);
        PolicyRolloutService policyRolloutService = mock(PolicyRolloutService.class);
        PolicyLifecycleService service = new PolicyLifecycleService(policyGroupService, policyRolloutService);
        PolicyGroup policyGroup = PolicyGroup.builder()
                .id("pg_1")
                .currentVersion(1)
                .build();
        when(policyGroupService.rollback("pg_1", 1, "Rollback", "op_1", "Hive Admin")).thenReturn(policyGroup);

        PolicyGroup result = service.rollback("pg_1", 1, "Rollback", "op_1", "Hive Admin");

        assertSame(policyGroup, result);
        verify(policyRolloutService).requestSyncForGroup("pg_1");
    }

    @Test
    void shouldRequestBindingSyncAfterBind() {
        PolicyGroupService policyGroupService = mock(PolicyGroupService.class);
        PolicyRolloutService policyRolloutService = mock(PolicyRolloutService.class);
        PolicyLifecycleService service = new PolicyLifecycleService(policyGroupService, policyRolloutService);
        GolemPolicyBinding binding = GolemPolicyBinding.builder()
                .golemId("golem_1")
                .policyGroupId("pg_1")
                .build();
        when(policyGroupService.bindGolem("golem_1", "pg_1", "op_1", "Hive Admin")).thenReturn(binding);

        GolemPolicyBinding result = service.bindGolem("golem_1", "pg_1", "op_1", "Hive Admin");

        assertSame(binding, result);
        verify(policyRolloutService).requestSyncIfSupported(binding);
    }
}
