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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.adapter.inbound.web.security.SubjectType;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;

class MailboxControllerTest {

    private NotificationUseCase notificationUseCase;
    private WebTestClient webTestClient;
    private WebTestClient operatorWebTestClient;

    @BeforeEach
    void setUp() {
        notificationUseCase = mock(NotificationUseCase.class);
        webTestClient = WebTestClient
                .bindToController(new MailboxController(notificationUseCase))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
        operatorWebTestClient = WebTestClient
                .bindToController(new MailboxController(notificationUseCase))
                .controllerAdvice(new ApiExceptionHandler())
                .webFilter(operatorPrincipalFilter())
                .build();
    }

    @Test
    void shouldListInternalNotificationMailboxMessagesFilteredBySenderAndTags() {
        when(notificationUseCase.listNotifications()).thenReturn(List.of(
                buildNotification(
                        "ntf_2",
                        "COMMAND_FAILED",
                        "Deploy failed",
                        "Production deploy failed",
                        false,
                        "2026-04-16T09:20:00Z",
                        "Release Bot",
                        List.of("deploy", "critical")),
                buildNotification(
                        "ntf_1",
                        "GOLEM_OFFLINE",
                        "Atlas offline",
                        "Atlas missed heartbeats",
                        true,
                        "2026-04-16T09:10:00Z",
                        "Atlas",
                        List.of("fleet", "availability"))));

        operatorWebTestClient.get()
                .uri("/api/v1/mailbox/messages?limit=1&sender=release&tag=deploy")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.hasMore").isEqualTo(false)
                .jsonPath("$.messages[0].id").isEqualTo("ntf_2")
                .jsonPath("$.messages[0].senderDisplayName").isEqualTo("Release Bot")
                .jsonPath("$.messages[0].tags[0]").isEqualTo("deploy")
                .jsonPath("$.messages[0].tags[1]").isEqualTo("critical");
    }

    @Test
    void shouldPaginateMailboxMessagesBeforeCursor() {
        when(notificationUseCase.listNotifications()).thenReturn(List.of(
                buildNotification(
                        "ntf_3",
                        "COMMAND_FAILED",
                        "Newest",
                        "Newest message",
                        false,
                        "2026-04-16T09:30:00Z",
                        "Release Bot",
                        List.of("deploy")),
                buildNotification(
                        "ntf_2",
                        "COMMAND_FAILED",
                        "Middle",
                        "Middle message",
                        false,
                        "2026-04-16T09:20:00Z",
                        "Release Bot",
                        List.of("deploy")),
                buildNotification(
                        "ntf_1",
                        "COMMAND_FAILED",
                        "Oldest",
                        "Oldest message",
                        false,
                        "2026-04-16T09:10:00Z",
                        "Release Bot",
                        List.of("deploy"))));

        operatorWebTestClient.get()
                .uri("/api/v1/mailbox/messages?limit=1&before=2026-04-16T09:30:00Z")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.hasMore").isEqualTo(true)
                .jsonPath("$.messages[0].id").isEqualTo("ntf_2");
    }

    @Test
    void shouldMarkMailboxMessageAsRead() {
        when(notificationUseCase.acknowledge("ntf_1")).thenReturn(buildNotification(
                "ntf_1",
                "GOLEM_OFFLINE",
                "Atlas offline",
                "Atlas missed heartbeats",
                true,
                "2026-04-16T09:10:00Z",
                "Atlas",
                List.of("fleet", "availability")));

        operatorWebTestClient.post()
                .uri("/api/v1/mailbox/messages/ntf_1:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("ntf_1")
                .jsonPath("$.acknowledged").isEqualTo(true);
    }

    @Test
    void shouldRejectMailboxMessageCreation() {
        operatorWebTestClient.post()
                .uri("/api/v1/mailbox/messages")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"message\":\"operators cannot write here\"}")
                .exchange()
                .expectStatus().isEqualTo(405);
    }

    @Test
    void shouldRejectInvalidBeforeCursor() {
        operatorWebTestClient.get()
                .uri("/api/v1/mailbox/messages?before=not-an-instant")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRequireOperatorForMailboxAccess() {
        when(notificationUseCase.listNotifications()).thenReturn(List.of());

        webTestClient.get()
                .uri("/api/v1/mailbox/messages")
                .exchange()
                .expectStatus().isForbidden();
    }

    private NotificationEvent buildNotification(
            String id,
            String type,
            String title,
            String message,
            boolean acknowledged,
            String createdAt,
            String senderDisplayName,
            List<String> tags) {
        return NotificationEvent.builder()
                .id(id)
                .type(type)
                .severity(NotificationSeverity.CRITICAL)
                .title(title)
                .message(message)
                .senderDisplayName(senderDisplayName)
                .tags(tags)
                .acknowledged(acknowledged)
                .createdAt(Instant.parse(createdAt))
                .build();
    }

    private WebFilter operatorPrincipalFilter() {
        AuthenticatedActor actor = new AuthenticatedActor(
                SubjectType.OPERATOR,
                "operator_1",
                "Admin",
                List.of("ADMIN"),
                List.of(),
                "session_1");
        return (exchange, chain) -> chain.filter(exchange.mutate()
                .principal(reactor.core.publisher.Mono.just(actor))
                .build());
    }
}
