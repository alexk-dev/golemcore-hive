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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyRolloutService {

    static final String POLICY_SYNC_EVENT_TYPE = "policy.sync_requested";
    static final String POLICY_SYNC_FEATURE = "policy-sync-v1";

    private final GolemRegistryService golemRegistryService;
    private final PolicyGroupService policyGroupService;
    private final GolemControlChannelService golemControlChannelService;
    private final ObjectMapper objectMapper;

    public boolean requestSyncIfSupported(GolemPolicyBinding binding) {
        Golem golem = golemRegistryService.findGolem(binding.getGolemId()).orElse(null);
        if (golem == null || !supportsPolicySync(golem) || !golemControlChannelService.isConnected(golem.getId())) {
            return false;
        }

        PolicyGroupVersion version = policyGroupService.getVersion(binding.getPolicyGroupId(),
                binding.getTargetVersion());
        ControlCommandEnvelope envelope = ControlCommandEnvelope.builder()
                .eventType(POLICY_SYNC_EVENT_TYPE)
                .golemId(golem.getId())
                .policyGroupId(binding.getPolicyGroupId())
                .targetVersion(binding.getTargetVersion())
                .checksum(version.getChecksum())
                .createdAt(Instant.now())
                .build();
        return golemControlChannelService.send(golem.getId(), toJson(envelope));
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

    private String toJson(ControlCommandEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy rollout envelope", exception);
        }
    }
}
