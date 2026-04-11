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

package me.golemcore.hive.governance.adapter.out.support;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.workflow.application.port.out.WorkflowPolicyPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GovernanceWorkflowPolicyAdapter implements WorkflowPolicyPort {

    private final PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase;

    @Override
    public Optional<PolicyGroupSpec.PolicySdlcConfig> findSdlcPolicyForGolem(String golemId) {
        if (golemId == null || golemId.isBlank()) {
            return Optional.empty();
        }
        return policyGroupAdministrationUseCase.findBinding(golemId)
                .map(binding -> policyGroupAdministrationUseCase.getVersion(
                        binding.getPolicyGroupId(),
                        binding.getTargetVersion()))
                .map(PolicyGroupVersion::getSpecSnapshot)
                .map(PolicyGroupSpec::getSdlc);
    }
}
