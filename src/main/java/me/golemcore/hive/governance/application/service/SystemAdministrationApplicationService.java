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

package me.golemcore.hive.governance.application.service;

import java.util.List;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.SystemHealthSnapshot;
import me.golemcore.hive.governance.application.SystemSettingsSnapshot;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import me.golemcore.hive.governance.application.port.in.SystemAdministrationUseCase;
import me.golemcore.hive.governance.application.port.out.SystemHealthPort;

public class SystemAdministrationApplicationService implements SystemAdministrationUseCase {

    private final GovernanceSettings governanceSettings;
    private final SystemHealthPort systemHealthPort;
    private final NotificationUseCase notificationUseCase;

    public SystemAdministrationApplicationService(
            GovernanceSettings governanceSettings,
            SystemHealthPort systemHealthPort,
            NotificationUseCase notificationUseCase) {
        this.governanceSettings = governanceSettings;
        this.systemHealthPort = systemHealthPort;
        this.notificationUseCase = notificationUseCase;
    }

    @Override
    public SystemHealthSnapshot health() {
        systemHealthPort.ensureStorageReady();
        return new SystemHealthSnapshot(
                "golemcore-hive",
                governanceSettings.storageBasePath(),
                true);
    }

    @Override
    public SystemSettingsSnapshot settings() {
        List<NotificationEvent> recentNotifications = notificationUseCase.listNotifications().stream()
                .limit(20)
                .toList();
        return new SystemSettingsSnapshot(
                governanceSettings.productionMode(),
                governanceSettings.storageBasePath(),
                governanceSettings.secureRefreshCookie(),
                governanceSettings.highCostThresholdMicros(),
                new SystemSettingsSnapshot.RetentionDefaults(
                        governanceSettings.approvalsRetentionDays(),
                        governanceSettings.auditRetentionDays(),
                        governanceSettings.notificationsRetentionDays()),
                new SystemSettingsSnapshot.NotificationDefaults(
                        governanceSettings.approvalRequestedNotificationsEnabled(),
                        governanceSettings.blockerRaisedNotificationsEnabled(),
                        governanceSettings.golemOfflineNotificationsEnabled(),
                        governanceSettings.commandFailedNotificationsEnabled()),
                recentNotifications);
    }

    @Override
    public NotificationEvent acknowledgeNotification(String notificationId) {
        return notificationUseCase.acknowledge(notificationId);
    }
}
