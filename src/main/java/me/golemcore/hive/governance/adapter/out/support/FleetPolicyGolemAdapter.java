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
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.fleet.application.port.in.GolemPolicyBindingMaintenanceUseCase;
import me.golemcore.hive.governance.application.port.out.PolicyGolemPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FleetPolicyGolemAdapter implements PolicyGolemPort {

    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final GolemPolicyBindingMaintenanceUseCase golemPolicyBindingMaintenanceUseCase;

    @Override
    public Optional<Golem> findGolem(String golemId) {
        return golemDirectoryUseCase.findGolem(golemId);
    }

    @Override
    public void updatePolicyBinding(String golemId, GolemPolicyBinding policyBinding) {
        golemPolicyBindingMaintenanceUseCase.updatePolicyBinding(golemId, policyBinding);
    }

    @Override
    public void clearPolicyBinding(String golemId) {
        golemPolicyBindingMaintenanceUseCase.clearPolicyBinding(golemId);
    }
}
