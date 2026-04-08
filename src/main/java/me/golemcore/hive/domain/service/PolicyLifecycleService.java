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

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyLifecycleService {

    private final PolicyGroupService policyGroupService;
    private final PolicyRolloutService policyRolloutService;

    public PolicyGroupVersion publish(String groupId, String changeSummary, String actorId, String actorName) {
        PolicyGroupVersion version = policyGroupService.publish(groupId, changeSummary, actorId, actorName);
        policyRolloutService.requestSyncForGroup(groupId);
        return version;
    }

    public PolicyGroup rollback(String groupId, int version, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = policyGroupService.rollback(groupId, version, changeSummary, actorId, actorName);
        policyRolloutService.requestSyncForGroup(groupId);
        return policyGroup;
    }

    public GolemPolicyBinding bindGolem(String golemId, String policyGroupId, String actorId, String actorName) {
        GolemPolicyBinding binding = policyGroupService.bindGolem(golemId, policyGroupId, actorId, actorName);
        policyRolloutService.requestSyncIfSupported(binding);
        return binding;
    }

    public void unbindGolem(String golemId, String actorId, String actorName) {
        policyGroupService.unbindGolem(golemId, actorId, actorName);
    }
}
