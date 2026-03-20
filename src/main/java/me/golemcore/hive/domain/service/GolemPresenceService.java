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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GolemPresenceService {

    private static final String GOLEMS_DIR = "golems";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final HiveProperties properties;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public GolemState resolveState(Golem golem, Instant now) {
        if (golem.getState() == GolemState.REVOKED) {
            return GolemState.REVOKED;
        }
        if (golem.getState() == GolemState.PAUSED) {
            return GolemState.PAUSED;
        }
        return resolveOperationalState(golem, now);
    }

    public GolemState resolveOperationalState(Golem golem, Instant now) {
        if (golem.getLastHeartbeatAt() == null) {
            return GolemState.PENDING_ENROLLMENT;
        }

        int missedHeartbeats = calculateMissedHeartbeats(golem, now);
        if (missedHeartbeats >= properties.getFleet().getOfflineAfterMisses()) {
            return GolemState.OFFLINE;
        }
        if (missedHeartbeats >= properties.getFleet().getDegradedAfterMisses()) {
            return GolemState.DEGRADED;
        }
        return GolemState.ONLINE;
    }

    public int calculateMissedHeartbeats(Golem golem, Instant now) {
        if (golem.getLastHeartbeatAt() == null) {
            return 0;
        }
        int heartbeatInterval = Math.max(golem.getHeartbeatIntervalSeconds(),
                properties.getFleet().getHeartbeatIntervalSeconds());
        long secondsSinceHeartbeat = Duration.between(golem.getLastHeartbeatAt(), now).getSeconds();
        if (secondsSinceHeartbeat <= 0) {
            return 0;
        }
        return (int) (secondsSinceHeartbeat / heartbeatInterval);
    }

    @Scheduled(fixedDelay = 15_000)
    public void evaluatePresence() {
        Instant now = Instant.now();
        List<String> golemFiles = storagePort.listObjects(GOLEMS_DIR, "");
        for (String file : golemFiles) {
            Optional<Golem> golemOptional = loadGolem(file);
            if (golemOptional.isEmpty()) {
                continue;
            }
            Golem golem = golemOptional.get();
            GolemState resolvedState = resolveState(golem, now);
            int missedHeartbeats = calculateMissedHeartbeats(golem, now);
            if (golem.getState() != resolvedState || golem.getMissedHeartbeatCount() != missedHeartbeats) {
                GolemState previousState = golem.getState();
                golem.setState(resolvedState);
                golem.setMissedHeartbeatCount(missedHeartbeats);
                if (golem.getLastStateChangeAt() == null || resolvedState != GolemState.PAUSED) {
                    golem.setLastStateChangeAt(now);
                }
                saveGolem(golem);
                if (previousState != resolvedState) {
                    auditService.record(AuditEvent.builder()
                            .eventType("golem.state_changed")
                            .severity(resolvedState == GolemState.OFFLINE ? "WARN" : "INFO")
                            .actorType("SYSTEM")
                            .targetType("GOLEM")
                            .targetId(golem.getId())
                            .golemId(golem.getId())
                            .summary("Golem state changed to " + resolvedState)
                            .details("Previous state " + previousState));
                    if (resolvedState == GolemState.OFFLINE && notificationService.isGolemOfflineEnabled()) {
                        notificationService.create(NotificationEvent.builder()
                                .type("GOLEM_OFFLINE")
                                .severity(NotificationSeverity.CRITICAL)
                                .title("Golem offline")
                                .message(golem.getDisplayName() + " is offline")
                                .golemId(golem.getId()));
                    }
                }
            }
        }
    }

    private Optional<Golem> loadGolem(String path) {
        String content = storagePort.getText(GOLEMS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Golem.class));
        } catch (JsonProcessingException exception) {
            log.warn("[Fleet] Failed to deserialize golem '{}': {}", path, exception.getMessage());
            return Optional.empty();
        }
    }

    private void saveGolem(Golem golem) {
        try {
            storagePort.putTextAtomic(GOLEMS_DIR, golem.getId() + ".json", objectMapper.writeValueAsString(golem));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize golem " + golem.getId(), exception);
        }
    }
}
