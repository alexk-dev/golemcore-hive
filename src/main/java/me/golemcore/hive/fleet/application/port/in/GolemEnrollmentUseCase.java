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
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.fleet.application.ActorContext;
import me.golemcore.hive.fleet.application.CreatedEnrollmentToken;
import me.golemcore.hive.fleet.application.MachineTokenPair;
import me.golemcore.hive.fleet.application.RegistrationResult;

public interface GolemEnrollmentUseCase {

    CreatedEnrollmentToken createEnrollmentToken(ActorContext actor, String note, Integer expiresInMinutes);

    List<EnrollmentToken> listEnrollmentTokens();

    EnrollmentToken revokeEnrollmentToken(String tokenId, String reason);

    RegistrationResult registerGolem(String enrollmentTokenValue,
            String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot);

    MachineTokenPair rotateMachineTokens(String golemId, String refreshToken);
}
