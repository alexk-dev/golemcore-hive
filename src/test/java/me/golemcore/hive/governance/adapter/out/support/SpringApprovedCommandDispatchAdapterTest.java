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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import me.golemcore.hive.shared.approval.ApprovedCommandDispatchRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringApprovedCommandDispatchAdapterTest {

    @Test
    void shouldPublishApprovedCommandDispatchRequestEvent() {
        ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        SpringApprovedCommandDispatchAdapter adapter = new SpringApprovedCommandDispatchAdapter(
                applicationEventPublisher);

        adapter.dispatchApprovedCommand("cmd-1");

        verify(applicationEventPublisher).publishEvent(new ApprovedCommandDispatchRequestedEvent("cmd-1"));
    }
}
