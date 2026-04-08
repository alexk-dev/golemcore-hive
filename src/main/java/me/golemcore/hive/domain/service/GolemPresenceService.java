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

import java.time.Instant;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemState;

public class GolemPresenceService {

    public GolemState resolveState(Golem golem, Instant now) {
        if (golem.getState() == GolemState.REVOKED) {
            return GolemState.REVOKED;
        }
        if (golem.getState() == GolemState.PAUSED) {
            return GolemState.PAUSED;
        }
        return resolveOperationalState(golem, now);
    }

    public GolemState resolveOperationalState(Golem golem, Instant now) {
        if (golem.getLastHeartbeatAt() == null) {
            return GolemState.PENDING_ENROLLMENT;
        }
        return GolemState.ONLINE;
    }

    public int calculateMissedHeartbeats(Golem golem, Instant now) {
        return 0;
    }
}
