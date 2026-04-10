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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.governance.application.port.out.AuditEventRepositoryPort;
import org.junit.jupiter.api.Test;

class AuditLogApplicationServiceTest {

    @Test
    void shouldFilterAuditEventsByActorEventTypeAndTimeRange() {
        InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
        AuditLogApplicationService auditLogApplicationService = new AuditLogApplicationService(repository);

        auditLogApplicationService.record(AuditEvent.builder()
                .id("audit-1")
                .eventType("approval.requested")
                .actorId("operator-1")
                .golemId("golem-1")
                .boardId("board-1")
                .cardId("card-1")
                .createdAt(Instant.parse("2026-04-09T10:00:00Z"))
                .build());
        auditLogApplicationService.record(AuditEvent.builder()
                .id("audit-2")
                .eventType("approval.rejected")
                .actorId("operator-1")
                .golemId("golem-1")
                .boardId("board-1")
                .cardId("card-1")
                .createdAt(Instant.parse("2026-04-09T11:00:00Z"))
                .build());
        auditLogApplicationService.record(AuditEvent.builder()
                .id("audit-3")
                .eventType("approval.rejected")
                .actorId("operator-2")
                .golemId("golem-2")
                .boardId("board-2")
                .cardId("card-2")
                .createdAt(Instant.parse("2026-04-09T12:00:00Z"))
                .build());

        List<AuditEvent> filteredEvents = auditLogApplicationService.listEvents(
                "operator-1",
                "golem-1",
                "board-1",
                "card-1",
                Instant.parse("2026-04-09T10:30:00Z"),
                Instant.parse("2026-04-09T11:30:00Z"),
                "approval.rejected");

        assertEquals(1, filteredEvents.size());
        assertEquals("audit-2", filteredEvents.getFirst().getId());
    }

    private static final class InMemoryAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> auditEvents = new ArrayList<>();

        @Override
        public List<AuditEvent> findAll() {
            return new ArrayList<>(auditEvents);
        }

        @Override
        public void save(AuditEvent auditEvent) {
            auditEvents.add(auditEvent);
        }
    }
}
