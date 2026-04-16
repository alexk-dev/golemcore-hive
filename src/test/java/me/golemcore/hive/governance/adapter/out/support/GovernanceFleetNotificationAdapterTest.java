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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import org.junit.jupiter.api.Test;

class GovernanceFleetNotificationAdapterTest {

    @Test
    void shouldPreserveSenderDisplayNameAndTagsWhenForwardingNotification() {
        NotificationUseCase notificationUseCase = mock(NotificationUseCase.class);
        GovernanceFleetNotificationAdapter adapter = new GovernanceFleetNotificationAdapter(notificationUseCase);

        adapter.create(NotificationEvent.builder()
                .id("ntf_1")
                .type("GOLEM_OFFLINE")
                .severity(NotificationSeverity.CRITICAL)
                .title("Golem offline")
                .message("Builder is offline")
                .senderDisplayName("Builder")
                .tags(List.of("fleet", "offline"))
                .golemId("golem_1")
                .build());

        verify(notificationUseCase).create(argThat(builder -> {
            NotificationEvent event = builder.build();
            return "Builder".equals(event.getSenderDisplayName())
                    && event.getTags().contains("fleet")
                    && event.getTags().contains("offline");
        }));
    }
}
