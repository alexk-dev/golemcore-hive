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

package me.golemcore.hive.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Golem {

    @Builder.Default
    private int schemaVersion = 1;

    private String id;
    private String displayName;
    private String hostLabel;
    private String runtimeVersion;
    private String buildVersion;
    private String controlChannelUrl;
    private GolemState state;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant registeredAt;
    private Instant lastHeartbeatAt;
    private Instant lastSeenAt;
    private Instant lastStateChangeAt;
    private Instant lastSuccessfulCommandAt;
    private String enrollmentTokenId;
    private String pauseReason;
    private String revokeReason;
    private int heartbeatIntervalSeconds;
    private int missedHeartbeatCount;
    private Set<String> supportedChannels;
    private GolemCapabilitySnapshot capabilitySnapshot;
    private HeartbeatPing lastHeartbeat;

    @Builder.Default
    private List<GolemRoleBinding> roleBindings = new ArrayList<>();
}
