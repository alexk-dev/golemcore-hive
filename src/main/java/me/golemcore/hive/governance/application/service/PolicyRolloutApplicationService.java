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

import java.time.Instant;
import java.util.Set;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyRolloutUseCase;
import me.golemcore.hive.governance.application.port.out.PolicyGolemPort;
import me.golemcore.hive.governance.application.port.out.PolicySyncDispatchPort;

public class PolicyRolloutApplicationService implements PolicyRolloutUseCase {

    static final String POLICY_SYNC_FEATURE = "policy-sync-v1";

    private final PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase;
    private final PolicyGolemPort policyGolemPort;
    private final PolicySyncDispatchPort policySyncDispatchPort;

    public PolicyRolloutApplicationService(
            PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            PolicyGolemPort policyGolemPort,
            PolicySyncDispatchPort policySyncDispatchPort) {
        this.policyGroupAdministrationUseCase = policyGroupAdministrationUseCase;
        this.policyGolemPort = policyGolemPort;
        this.policySyncDispatchPort = policySyncDispatchPort;
    }

    @Override
    public void requestSyncForGroup(String groupId) {
        for (GolemPolicyBinding binding : policyGroupAdministrationUseCase.listBindingsForPolicyGroup(groupId)) {
            requestSyncIfSupported(binding);
        }
    }

    @Override
    public boolean requestSyncIfSupported(GolemPolicyBinding binding) {
        Golem golem = policyGolemPort.findGolem(binding.getGolemId()).orElse(null);
        if (golem == null || !supportsPolicySync(golem) || !policySyncDispatchPort.isConnected(golem.getId())) {
            return false;
        }

        PolicyGroupVersion version = policyGroupAdministrationUseCase.getVersion(binding.getPolicyGroupId(),
                binding.getTargetVersion());
        return policySyncDispatchPort.requestSync(golem.getId(), binding.getPolicyGroupId(), binding.getTargetVersion(),
                version.getChecksum(), Instant.now());
    }

    private boolean supportsPolicySync(Golem golem) {
        if (golem.getCapabilitySnapshot() == null) {
            return false;
        }
        Set<String> enabledAutonomyFeatures = golem.getCapabilitySnapshot().getEnabledAutonomyFeatures();
        boolean advertisesFeature = enabledAutonomyFeatures != null
                && enabledAutonomyFeatures.contains(POLICY_SYNC_FEATURE);
        boolean supportsControlChannel = (golem.getSupportedChannels() != null && golem.getSupportedChannels()
                .contains("control"))
                || (golem.getCapabilitySnapshot().getSupportedChannels() != null && golem.getCapabilitySnapshot()
                        .getSupportedChannels().contains("control"));
        return advertisesFeature && supportsControlChannel;
    }
}
