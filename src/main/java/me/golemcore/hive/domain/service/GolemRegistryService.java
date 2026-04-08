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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonGolemRepository;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonGolemRoleRepository;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonHeartbeatRepository;
import me.golemcore.hive.fleet.adapter.out.support.AuditServiceFleetAuditAdapter;
import me.golemcore.hive.fleet.application.ActorContext;
import me.golemcore.hive.fleet.application.FleetSettings;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import me.golemcore.hive.fleet.application.service.GolemFleetApplicationService;
import me.golemcore.hive.port.outbound.StoragePort;

public class GolemRegistryService {

    private static final FleetNotificationPort NOOP_NOTIFICATION_PORT = new FleetNotificationPort() {
        @Override
        public boolean isGolemOfflineEnabled() {
            return false;
        }

        @Override
        public void create(me.golemcore.hive.domain.model.NotificationEvent notificationEvent) {
        }
    };

    private final GolemFleetApplicationService delegate;

    public GolemRegistryService(GolemFleetApplicationService delegate) {
        this.delegate = delegate;
    }

    public GolemRegistryService(
            StoragePort storagePort,
            ObjectMapper objectMapper,
            HiveProperties properties,
            GolemPresenceService golemPresenceService,
            AuditService auditService) {
        this.delegate = new GolemFleetApplicationService(
                new JsonGolemRepository(storagePort, objectMapper),
                new JsonHeartbeatRepository(storagePort, objectMapper),
                new JsonGolemRoleRepository(storagePort, objectMapper),
                new AuditServiceFleetAuditAdapter(auditService),
                NOOP_NOTIFICATION_PORT,
                new FleetSettings(
                        properties.getFleet().getControlChannelUrl(),
                        properties.getFleet().getHeartbeatIntervalSeconds(),
                        properties.getFleet().getDegradedAfterMisses(),
                        properties.getFleet().getOfflineAfterMisses(),
                        properties.getFleet().getEnrollmentTokenTtlMinutes(),
                        properties.getSecurity().getJwt().getGolemAccessExpirationMinutes(),
                        properties.getSecurity().getJwt().getGolemRefreshExpirationDays()));
    }

    public Golem registerGolem(String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot,
            String enrollmentTokenId) {
        return delegate.registerGolem(displayName, hostLabel, runtimeVersion, buildVersion,
                supportedChannels, capabilitySnapshot, enrollmentTokenId);
    }

    public Optional<Golem> findGolem(String golemId) {
        return delegate.findGolem(golemId);
    }

    public List<Golem> listGolems(String query, String state, String roleSlug) {
        return delegate.listGolems(query, state, roleSlug);
    }

    public Golem updateHeartbeat(String golemId, HeartbeatPing heartbeatPing) {
        return delegate.updateHeartbeat(golemId, heartbeatPing);
    }

    public Golem pauseGolem(String golemId, String reason) {
        return delegate.pauseGolem(golemId, reason);
    }

    public Golem resumeGolem(String golemId) {
        return delegate.resumeGolem(golemId);
    }

    public Golem revokeGolem(String golemId, String reason) {
        return delegate.revokeGolem(golemId, reason);
    }

    public List<GolemRole> listRoles() {
        return delegate.listRoles();
    }

    public Optional<GolemRole> findRole(String slug) {
        return delegate.findRole(slug);
    }

    public GolemRole createRole(String slug, String name, String description, Set<String> capabilityTags) {
        return delegate.createRole(slug, name, description, capabilityTags);
    }

    public GolemRole updateRole(String slug, String name, String description, Set<String> capabilityTags) {
        return delegate.updateRole(slug, name, description, capabilityTags);
    }

    public Golem assignRoles(String golemId, List<String> roleSlugs, ActorContext actor) {
        return delegate.assignRoles(golemId, roleSlugs, actor);
    }

    public Golem unassignRoles(String golemId, List<String> roleSlugs) {
        return delegate.unassignRoles(golemId, roleSlugs);
    }

    public Golem updatePolicyBinding(String golemId, GolemPolicyBinding policyBinding) {
        return delegate.updatePolicyBinding(golemId, policyBinding);
    }

    public Golem clearPolicyBinding(String golemId) {
        return delegate.clearPolicyBinding(golemId);
    }
}
