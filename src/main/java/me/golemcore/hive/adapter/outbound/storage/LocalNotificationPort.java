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

package me.golemcore.hive.adapter.outbound.storage;

import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.infrastructure.notification.NotificationPort;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocalNotificationPort implements NotificationPort {

    @Override
    public void deliver(NotificationEvent event) {
        log.info("[Notifications] {} {} {}", event.getSeverity(), event.getType(), event.getTitle());
    }
}
