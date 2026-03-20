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

package me.golemcore.hive.adapter.inbound.web.controller;

import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.system.NotificationEventResponse;
import me.golemcore.hive.adapter.inbound.web.dto.system.SystemSettingsResponse;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.service.NotificationService;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final HiveProperties properties;
    private final StoragePort storagePort;
    private final NotificationService notificationService;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        storagePort.ensureDirectory("meta");
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "golemcore-hive",
                "storageBasePath", properties.getStorage().getBasePath(),
                "storageReady", true)));
    }

    @GetMapping("/settings")
    public Mono<ResponseEntity<SystemSettingsResponse>> settings(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            SystemSettingsResponse response = new SystemSettingsResponse(
                    properties.getDeployment().isProductionMode(),
                    properties.getStorage().getBasePath(),
                    properties.getSecurity().getCookie().isSecure(),
                    properties.getGovernance().getApprovals().getHighCostThresholdMicros(),
                    new SystemSettingsResponse.RetentionDefaults(
                            properties.getGovernance().getRetention().getApprovalsDays(),
                            properties.getGovernance().getRetention().getAuditDays(),
                            properties.getGovernance().getRetention().getNotificationsDays()),
                    new SystemSettingsResponse.NotificationDefaults(
                            properties.getGovernance().getNotifications().isApprovalRequested(),
                            properties.getGovernance().getNotifications().isBlockerRaised(),
                            properties.getGovernance().getNotifications().isGolemOffline(),
                            properties.getGovernance().getNotifications().isCommandFailed()),
                    notificationService.listNotifications().stream().limit(20).map(this::toNotificationResponse)
                            .toList());
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/notifications/{notificationId}:ack")
    public Mono<ResponseEntity<NotificationEventResponse>> acknowledgeNotification(
            Principal principal,
            @PathVariable String notificationId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toNotificationResponse(notificationService.acknowledge(notificationId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private NotificationEventResponse toNotificationResponse(NotificationEvent event) {
        return new NotificationEventResponse(
                event.getId(),
                event.getType(),
                event.getSeverity().name(),
                event.getTitle(),
                event.getMessage(),
                event.getBoardId(),
                event.getCardId(),
                event.getThreadId(),
                event.getGolemId(),
                event.getCommandId(),
                event.getApprovalId(),
                event.isAcknowledged(),
                event.getCreatedAt(),
                event.getAcknowledgedAt());
    }
}
