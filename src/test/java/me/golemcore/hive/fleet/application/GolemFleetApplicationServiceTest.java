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

package me.golemcore.hive.fleet.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.fleet.application.port.out.FleetAuditPort;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import me.golemcore.hive.fleet.application.port.out.GolemRepository;
import me.golemcore.hive.fleet.application.port.out.GolemRoleRepository;
import me.golemcore.hive.fleet.application.port.out.HeartbeatRepository;
import me.golemcore.hive.fleet.application.service.GolemFleetApplicationService;
import org.junit.jupiter.api.Test;

class GolemFleetApplicationServiceTest {

    @Test
    void shouldUpdateHeartbeatAndBringGolemOnline() {
        GolemRepository golemRepository = mock(GolemRepository.class);
        HeartbeatRepository heartbeatRepository = mock(HeartbeatRepository.class);
        GolemRoleRepository golemRoleRepository = mock(GolemRoleRepository.class);
        FleetAuditPort auditPort = mock(FleetAuditPort.class);
        FleetNotificationPort notificationPort = mock(FleetNotificationPort.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);
        Golem golem = Golem.builder()
                .id("golem_1")
                .displayName("Builder")
                .state(GolemState.PENDING_ENROLLMENT)
                .heartbeatIntervalSeconds(30)
                .build();
        HeartbeatPing heartbeatPing = HeartbeatPing.builder()
                .golemId("golem_1")
                .receivedAt(Instant.parse("2026-04-08T18:00:00Z"))
                .status("healthy")
                .build();
        when(golemRepository.findById("golem_1")).thenReturn(Optional.of(golem));

        GolemFleetApplicationService service = new GolemFleetApplicationService(
                golemRepository,
                heartbeatRepository,
                golemRoleRepository,
                auditPort,
                notificationPort,
                settings);

        Golem updated = service.updateHeartbeat("golem_1", heartbeatPing);

        assertEquals(GolemState.ONLINE, updated.getState());
        assertEquals(0, updated.getMissedHeartbeatCount());
        assertEquals(heartbeatPing, updated.getLastHeartbeat());
        verify(heartbeatRepository).save(heartbeatPing);
        verify(golemRepository).save(argThat(saved -> saved.getLastHeartbeatAt().equals(heartbeatPing.getReceivedAt())
                && saved.getState() == GolemState.ONLINE));
    }

    @Test
    void shouldAssignRolesRecordingOperatorActor() {
        GolemRepository golemRepository = mock(GolemRepository.class);
        HeartbeatRepository heartbeatRepository = mock(HeartbeatRepository.class);
        GolemRoleRepository golemRoleRepository = mock(GolemRoleRepository.class);
        FleetAuditPort auditPort = mock(FleetAuditPort.class);
        FleetNotificationPort notificationPort = mock(FleetNotificationPort.class);
        FleetSettings settings = new FleetSettings("wss://hive.example.test/control", 30, 2, 4, 60, 15, 30);
        Golem golem = Golem.builder()
                .id("golem_1")
                .displayName("Builder")
                .state(GolemState.ONLINE)
                .roleBindings(new java.util.ArrayList<>())
                .build();
        GolemRole role = GolemRole.builder()
                .slug("developer")
                .name("Developer")
                .capabilityTags(Set.of("java"))
                .build();
        when(golemRepository.findById("golem_1")).thenReturn(Optional.of(golem));
        when(golemRoleRepository.findBySlug("developer")).thenReturn(Optional.of(role));

        GolemFleetApplicationService service = new GolemFleetApplicationService(
                golemRepository,
                heartbeatRepository,
                golemRoleRepository,
                auditPort,
                notificationPort,
                settings);

        Golem updated = service.assignRoles("golem_1", List.of("developer"), new ActorContext("op_1", "admin"));

        assertEquals(1, updated.getRoleBindings().size());
        assertEquals("developer", updated.getRoleBindings().getFirst().getRoleSlug());
        assertEquals("op_1", updated.getRoleBindings().getFirst().getAssignedByOperatorId());
        assertEquals("admin", updated.getRoleBindings().getFirst().getAssignedByOperatorUsername());
        verify(golemRepository).save(argThat(saved -> saved.getRoleBindings().size() == 1));
        verify(auditPort).record(argThat(event -> "golem.roles_assigned".equals(event.getEventType())));
        assertTrue(updated.getUpdatedAt() != null);
    }
}
