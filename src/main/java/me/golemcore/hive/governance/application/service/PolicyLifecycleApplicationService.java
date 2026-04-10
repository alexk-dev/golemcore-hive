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

import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyLifecycleUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyRolloutUseCase;

public class PolicyLifecycleApplicationService implements PolicyLifecycleUseCase {

    private final PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase;
    private final PolicyRolloutUseCase policyRolloutUseCase;

    public PolicyLifecycleApplicationService(
            PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            PolicyRolloutUseCase policyRolloutUseCase) {
        this.policyGroupAdministrationUseCase = policyGroupAdministrationUseCase;
        this.policyRolloutUseCase = policyRolloutUseCase;
    }

    @Override
    public PolicyGroupVersion publish(String groupId, String changeSummary, String actorId, String actorName) {
        PolicyGroupVersion version = policyGroupAdministrationUseCase.publish(groupId, changeSummary, actorId,
                actorName);
        policyRolloutUseCase.requestSyncForGroup(groupId);
        return version;
    }

    @Override
    public PolicyGroup rollback(String groupId, int version, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = policyGroupAdministrationUseCase.rollback(groupId, version, changeSummary, actorId,
                actorName);
        policyRolloutUseCase.requestSyncForGroup(groupId);
        return policyGroup;
    }

    @Override
    public GolemPolicyBinding bindGolem(String golemId, String policyGroupId, String actorId, String actorName) {
        GolemPolicyBinding binding = policyGroupAdministrationUseCase.bindGolem(golemId, policyGroupId, actorId,
                actorName);
        policyRolloutUseCase.requestSyncIfSupported(binding);
        return binding;
    }

    @Override
    public void unbindGolem(String golemId, String actorId, String actorName) {
        policyGroupAdministrationUseCase.unbindGolem(golemId, actorId, actorName);
    }
}
