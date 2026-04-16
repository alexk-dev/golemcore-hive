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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.mailbox.MailboxMessagesResponse;
import me.golemcore.hive.adapter.inbound.web.dto.system.NotificationEventResponse;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/mailbox")
@RequiredArgsConstructor
public class MailboxController {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 200;

    private final NotificationUseCase notificationUseCase;

    @GetMapping("/messages")
    public Mono<ResponseEntity<MailboxMessagesResponse>> listMailboxMessages(
            Principal principal,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            int normalizedLimit = normalizeLimit(limit);
            Instant beforeInstant = parseBefore(before);
            List<NotificationEvent> filteredNotifications = notificationUseCase.listNotifications().stream()
                    .filter(notification -> beforeInstant == null
                            || notification.getCreatedAt().isBefore(beforeInstant))
                    .filter(notification -> !unreadOnly || !notification.isAcknowledged())
                    .filter(notification -> matchesSender(notification, sender))
                    .filter(notification -> matchesTags(notification, tag))
                    .toList();
            boolean hasMore = filteredNotifications.size() > normalizedLimit;
            List<NotificationEventResponse> messages = filteredNotifications.stream()
                    .limit(normalizedLimit)
                    .map(this::toNotificationResponse)
                    .toList();
            return ResponseEntity.ok(new MailboxMessagesResponse(messages, hasMore));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/messages/{notificationId}:read")
    public Mono<ResponseEntity<NotificationEventResponse>> markMailboxMessageRead(
            Principal principal,
            @PathVariable String notificationId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toNotificationResponse(notificationUseCase.acknowledge(notificationId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }

    private Instant parseBefore(String before) {
        if (before == null || before.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(before);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid before cursor");
        }
    }

    private boolean matchesSender(NotificationEvent notification, String sender) {
        if (sender == null || sender.isBlank()) {
            return true;
        }
        String normalizedSender = sender.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(notification.getSenderDisplayName(), normalizedSender);
    }

    private boolean matchesTags(NotificationEvent notification, String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        List<String> requestedTags = java.util.Arrays.stream(tag.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (requestedTags.isEmpty()) {
            return true;
        }
        List<String> notificationTags = notification.getTags() != null
                ? notification.getTags().stream().map(value -> value.toLowerCase(Locale.ROOT)).toList()
                : List.of();
        return requestedTags.stream().allMatch(notificationTags::contains);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private NotificationEventResponse toNotificationResponse(NotificationEvent event) {
        return new NotificationEventResponse(
                event.getId(),
                event.getType(),
                event.getSeverity().name(),
                event.getTitle(),
                event.getMessage(),
                event.getSenderDisplayName(),
                event.getTags(),
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
