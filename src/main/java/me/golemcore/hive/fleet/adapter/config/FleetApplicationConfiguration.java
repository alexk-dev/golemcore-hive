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

package me.golemcore.hive.fleet.adapter.config;

import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.service.GolemRegistryService;
import me.golemcore.hive.fleet.application.FleetSettings;
import me.golemcore.hive.fleet.application.port.in.GolemFleetUseCase;
import me.golemcore.hive.fleet.application.port.out.EnrollmentTokenRepository;
import me.golemcore.hive.fleet.application.port.out.FleetAuditPort;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import me.golemcore.hive.fleet.application.port.out.GolemAuthSessionRepository;
import me.golemcore.hive.fleet.application.port.out.GolemRepository;
import me.golemcore.hive.fleet.application.port.out.GolemRoleRepository;
import me.golemcore.hive.fleet.application.port.out.GolemTokenPort;
import me.golemcore.hive.fleet.application.port.out.HeartbeatRepository;
import me.golemcore.hive.fleet.application.service.GolemEnrollmentApplicationService;
import me.golemcore.hive.fleet.application.service.GolemFleetApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FleetApplicationConfiguration {

    @Bean
    public FleetSettings fleetSettings(HiveProperties properties) {
        return new FleetSettings(
                properties.getFleet().getControlChannelUrl(),
                properties.getFleet().getHeartbeatIntervalSeconds(),
                properties.getFleet().getDegradedAfterMisses(),
                properties.getFleet().getOfflineAfterMisses(),
                properties.getFleet().getEnrollmentTokenTtlMinutes(),
                properties.getSecurity().getJwt().getGolemAccessExpirationMinutes(),
                properties.getSecurity().getJwt().getGolemRefreshExpirationDays());
    }

    @Bean
    public GolemFleetApplicationService golemFleetApplicationService(
            GolemRepository golemRepository,
            HeartbeatRepository heartbeatRepository,
            GolemRoleRepository golemRoleRepository,
            FleetAuditPort auditPort,
            FleetNotificationPort notificationPort,
            FleetSettings fleetSettings) {
        return new GolemFleetApplicationService(
                golemRepository,
                heartbeatRepository,
                golemRoleRepository,
                auditPort,
                notificationPort,
                fleetSettings);
    }

    @Bean
    public GolemRegistryService golemRegistryService(GolemFleetApplicationService golemFleetApplicationService) {
        return new GolemRegistryService(golemFleetApplicationService);
    }

    @Bean
    public GolemEnrollmentApplicationService golemEnrollmentApplicationService(
            EnrollmentTokenRepository enrollmentTokenRepository,
            GolemAuthSessionRepository golemAuthSessionRepository,
            GolemTokenPort golemTokenPort,
            GolemFleetUseCase golemFleetUseCase,
            FleetSettings fleetSettings) {
        return new GolemEnrollmentApplicationService(
                enrollmentTokenRepository,
                golemAuthSessionRepository,
                golemTokenPort,
                golemFleetUseCase,
                fleetSettings);
    }
}
