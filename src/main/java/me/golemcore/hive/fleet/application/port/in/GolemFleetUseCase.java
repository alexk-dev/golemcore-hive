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

package me.golemcore.hive.fleet.application.port.in;

import java.util.List;
import java.util.Set;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.fleet.application.ActorContext;

public interface GolemFleetUseCase extends GolemDirectoryUseCase {

    Golem registerGolem(String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot,
            String enrollmentTokenId);

    Golem updateHeartbeat(String golemId, HeartbeatPing heartbeatPing);

    Golem pauseGolem(String golemId, String reason);

    Golem resumeGolem(String golemId);

    Golem revokeGolem(String golemId, String reason);

    GolemRole createRole(String slug, String name, String description, Set<String> capabilityTags);

    GolemRole updateRole(String slug, String name, String description, Set<String> capabilityTags);

    Golem assignRoles(String golemId, List<String> roleSlugs, ActorContext actor);

    Golem unassignRoles(String golemId, List<String> roleSlugs);
}
