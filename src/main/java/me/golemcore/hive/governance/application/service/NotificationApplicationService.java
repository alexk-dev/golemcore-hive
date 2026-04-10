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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import me.golemcore.hive.governance.application.port.out.NotificationDeliveryPort;
import me.golemcore.hive.governance.application.port.out.NotificationRepositoryPort;

public class NotificationApplicationService implements NotificationUseCase {

    private final GovernanceSettings governanceSettings;
    private final NotificationRepositoryPort notificationRepositoryPort;
    private final NotificationDeliveryPort notificationDeliveryPort;

    public NotificationApplicationService(
            GovernanceSettings governanceSettings,
            NotificationRepositoryPort notificationRepositoryPort,
            NotificationDeliveryPort notificationDeliveryPort) {
        this.governanceSettings = governanceSettings;
        this.notificationRepositoryPort = notificationRepositoryPort;
        this.notificationDeliveryPort = notificationDeliveryPort;
    }

    @Override
    public NotificationEvent create(NotificationEvent.NotificationEventBuilder builder) {
        Instant now = Instant.now();
        NotificationEvent template = builder.build();
        NotificationEvent notificationEvent = builder
                .id("ntf_" + UUID.randomUUID().toString().replace("-", ""))
                .createdAt(template.getCreatedAt() != null ? template.getCreatedAt() : now)
                .severity(template.getSeverity() != null ? template.getSeverity() : NotificationSeverity.INFO)
                .build();
        notificationRepositoryPort.save(notificationEvent);
        notificationDeliveryPort.deliver(notificationEvent);
        return notificationEvent;
    }

    @Override
    public List<NotificationEvent> listNotifications() {
        List<NotificationEvent> notificationEvents = notificationRepositoryPort.findAll().stream()
                .sorted(Comparator.comparing(
                        NotificationEvent::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NotificationEvent::getId, Comparator.reverseOrder()))
                .toList();
        return notificationEvents;
    }

    @Override
    public NotificationEvent acknowledge(String notificationId) {
        NotificationEvent notificationEvent = notificationRepositoryPort.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
        notificationEvent.setAcknowledged(true);
        notificationEvent.setAcknowledgedAt(Instant.now());
        notificationRepositoryPort.save(notificationEvent);
        return notificationEvent;
    }

    @Override
    public boolean isApprovalRequestedEnabled() {
        return governanceSettings.approvalRequestedNotificationsEnabled();
    }

    @Override
    public boolean isBlockerRaisedEnabled() {
        return governanceSettings.blockerRaisedNotificationsEnabled();
    }

    @Override
    public boolean isGolemOfflineEnabled() {
        return governanceSettings.golemOfflineNotificationsEnabled();
    }

    @Override
    public boolean isCommandFailedEnabled() {
        return governanceSettings.commandFailedNotificationsEnabled();
    }
}
