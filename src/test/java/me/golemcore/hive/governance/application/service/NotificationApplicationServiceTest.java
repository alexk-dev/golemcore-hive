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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.port.out.NotificationDeliveryPort;
import me.golemcore.hive.governance.application.port.out.NotificationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationApplicationServiceTest {

    private NotificationApplicationService service;
    private NotificationRepositoryPort notificationRepositoryPort;

    @BeforeEach
    void setUp() {
        GovernanceSettings governanceSettings = mock(GovernanceSettings.class);
        notificationRepositoryPort = mock(NotificationRepositoryPort.class);
        NotificationDeliveryPort notificationDeliveryPort = mock(NotificationDeliveryPort.class);
        service = new NotificationApplicationService(governanceSettings, notificationRepositoryPort,
                notificationDeliveryPort);
    }

    @Test
    void shouldPreserveSenderDisplayNameAndTagsWhenCreatingNotification() {
        when(notificationRepositoryPort.findAll()).thenReturn(List.of());
        when(notificationRepositoryPort.findById(any())).thenAnswer(invocation -> java.util.Optional.empty());

        NotificationEvent notification = service.create(NotificationEvent.builder()
                .type("COMMAND_FAILED")
                .title("Deploy failed")
                .message("Deployment failed during rollout")
                .senderDisplayName("Release Bot")
                .tags(List.of("deploy", "critical")));

        assertEquals("Release Bot", notification.getSenderDisplayName());
        assertEquals(List.of("deploy", "critical"), notification.getTags());
        assertTrue(notification.getId().startsWith("ntf_"));
    }
}
