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

package me.golemcore.hive.adapter.inbound.web.dto.golems;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record GolemDetailsResponse(String id,String displayName,String hostLabel,String runtimeVersion,String buildVersion,String state,Instant registeredAt,Instant createdAt,Instant updatedAt,Instant lastHeartbeatAt,Instant lastSeenAt,Instant lastStateChangeAt,int heartbeatIntervalSeconds,int missedHeartbeatCount,String pauseReason,String revokeReason,String controlChannelUrl,Set<String>supportedChannels,GolemCapabilitySnapshotRequest capabilities,HeartbeatRequest lastHeartbeat,List<String>roleSlugs,GolemPolicyBindingResponse policyBinding){}
