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

package me.golemcore.hive.fleet.adapter.out.support;

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.service.NotificationService;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationServiceFleetNotificationAdapter implements FleetNotificationPort {

    private final NotificationService notificationService;

    @Override
    public boolean isGolemOfflineEnabled() {
        return notificationService.isGolemOfflineEnabled();
    }

    @Override
    public void create(NotificationEvent notificationEvent) {
        notificationService.create(NotificationEvent.builder()
                .id(notificationEvent.getId())
                .schemaVersion(notificationEvent.getSchemaVersion())
                .type(notificationEvent.getType())
                .severity(notificationEvent.getSeverity())
                .title(notificationEvent.getTitle())
                .message(notificationEvent.getMessage())
                .boardId(notificationEvent.getBoardId())
                .cardId(notificationEvent.getCardId())
                .threadId(notificationEvent.getThreadId())
                .golemId(notificationEvent.getGolemId())
                .commandId(notificationEvent.getCommandId())
                .approvalId(notificationEvent.getApprovalId())
                .acknowledged(notificationEvent.isAcknowledged())
                .createdAt(notificationEvent.getCreatedAt())
                .acknowledgedAt(notificationEvent.getAcknowledgedAt()));
    }
}
