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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThreadControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .configureClient()
                .build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("hive.storage.base-path", () -> tempDir.toString());
        registry.add("hive.security.cookie.secure", () -> false);
        registry.add("hive.bootstrap.admin.username", () -> "admin");
        registry.add("hive.bootstrap.admin.password", () -> "change-me-now");
        registry.add("hive.bootstrap.admin.display-name", () -> "Hive Admin");
    }

    @Test
    void shouldDispatchCommandsPersistThreadsAndResolveSignals() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Dev", "host-dev", "developer");
        String boardId = createBoard(operatorToken);

        String firstCardId = createCard(operatorToken, boardId, "Ship thread orchestration", "ready",
                developer.golemId());
        String firstThreadId = getThreadId(operatorToken, firstCardId);
        CommandEnvelope firstCommand = createCommand(operatorToken, firstThreadId,
                "Implement thread orchestration and report back.");

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/events:batch", developer.golemId())
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion": 1,
                          "golemId": "%s",
                          "events": [
                            {
                              "eventType": "runtime_event",
                              "runtimeEventType": "THREAD_MESSAGE",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "summary": "Working on orchestration",
                              "details": "I started implementing the transport contracts."
                            },
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_started",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "WORK_STARTED",
                              "summary": "Work is now in progress"
                            },
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_completed",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "WORK_COMPLETED",
                              "summary": "Implementation finished"
                            }
                          ]
                        }
                        """.formatted(
                        developer.golemId(),
                        firstCardId,
                        firstThreadId,
                        firstCommand.commandId(),
                        firstCommand.runId(),
                        firstCardId,
                        firstThreadId,
                        firstCommand.commandId(),
                        firstCommand.runId(),
                        firstCardId,
                        firstThreadId,
                        firstCommand.commandId(),
                        firstCommand.runId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.acceptedEvents").isEqualTo(3)
                .jsonPath("$.autoAppliedTransitions").isEqualTo(2);

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}", firstCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.columnId").isEqualTo("review");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/commands", firstThreadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("COMPLETED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/runs", firstThreadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("COMPLETED")
                .jsonPath("$[0].lastSignalType").isEqualTo("WORK_COMPLETED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/signals", firstThreadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].resolutionOutcome").isEqualTo("AUTO_APPLIED")
                .jsonPath("$[1].resolutionOutcome").isEqualTo("AUTO_APPLIED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/messages", firstThreadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(4);

        String secondCardId = createCard(operatorToken, boardId, "Investigate blocker path", "in_progress",
                developer.golemId());
        String secondThreadId = getThreadId(operatorToken, secondCardId);
        CommandEnvelope secondCommand = createCommand(operatorToken, secondThreadId, "Check blocker escalation.");

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/events:batch", developer.golemId())
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion": 1,
                          "golemId": "%s",
                          "events": [
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_blocked",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "BLOCKER_RAISED",
                              "summary": "Waiting for credentials"
                            },
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_unblocked",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "BLOCKER_CLEARED",
                              "summary": "Credentials were provided"
                            }
                          ]
                        }
                        """.formatted(
                        developer.golemId(),
                        secondCardId,
                        secondThreadId,
                        secondCommand.commandId(),
                        secondCommand.runId(),
                        secondCardId,
                        secondThreadId,
                        secondCommand.commandId(),
                        secondCommand.runId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.acceptedEvents").isEqualTo(2)
                .jsonPath("$.autoAppliedTransitions").isEqualTo(1)
                .jsonPath("$.suggestedTransitions").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}", secondCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.columnId").isEqualTo("blocked");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/signals", secondThreadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].resolutionOutcome").isEqualTo("AUTO_APPLIED")
                .jsonPath("$[1].resolutionOutcome").isEqualTo("SUGGESTED")
                .jsonPath("$[1].resolvedTargetColumnId").isEqualTo("in_progress");
    }

    @Test
    void shouldAutoAdvanceWorkStartedFromInboxToInProgress() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Inbox", "host-inbox", "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Start work from inbox", "inbox", developer.golemId());
        String threadId = getThreadId(operatorToken, cardId);
        CommandEnvelope command = createCommand(operatorToken, threadId, "Start the work.");

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/events:batch", developer.golemId())
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion": 1,
                          "golemId": "%s",
                          "events": [
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_started_from_inbox",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "WORK_STARTED",
                              "summary": "Work is now active"
                            }
                          ]
                        }
                        """.formatted(
                        developer.golemId(),
                        cardId,
                        threadId,
                        command.commandId(),
                        command.runId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.acceptedEvents").isEqualTo(1)
                .jsonPath("$.autoAppliedTransitions").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}", cardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.columnId").isEqualTo("in_progress");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/signals", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].resolutionOutcome").isEqualTo("AUTO_APPLIED")
                .jsonPath("$[0].resolvedTargetColumnId").isEqualTo("in_progress");
    }

    @Test
    void shouldAutoDispatchCardPromptOnlyOnFirstManualMoveToInProgress() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Starter", "host-starter", "developer");
        BlockingQueue<String> controlMessages = new LinkedBlockingQueue<>();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        Disposable subscription = applicationContext
                .getBean(me.golemcore.hive.adapter.inbound.ws.InMemoryGolemControlChannelAdapter.class)
                .register(developer.golemId(), sink)
                .subscribe(controlMessages::add);
        try {
            String boardId = createBoard(operatorToken);
            String cardId = createCard(operatorToken, boardId, "Kick off queued work", "ready", developer.golemId());
            String threadId = getThreadId(operatorToken, cardId);

            webTestClient.post()
                    .uri("/api/v1/cards/{cardId}:move", cardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "targetColumnId":"in_progress",
                              "summary":"Start the assigned work"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.columnId").isEqualTo("in_progress")
                    .jsonPath("$.controlState.commandId").isNotEmpty()
                    .jsonPath("$.controlState.runId").isNotEmpty();

            JsonNode initialEnvelope = objectMapper.readTree(pollControlMessage(controlMessages));
            Assertions.assertEquals("command", initialEnvelope.get("eventType").asText());
            Assertions.assertEquals(cardId, initialEnvelope.get("cardId").asText());
            Assertions.assertEquals(threadId, initialEnvelope.get("threadId").asText());
            Assertions.assertEquals("Execute the full card prompt and report concrete progress.",
                    initialEnvelope.get("body").asText());

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/commands", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].body").isEqualTo("Execute the full card prompt and report concrete progress.");

            webTestClient.post()
                    .uri("/api/v1/cards/{cardId}:move", cardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "targetColumnId":"review",
                              "summary":"Ready for review"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk();

            webTestClient.post()
                    .uri("/api/v1/cards/{cardId}:move", cardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "targetColumnId":"in_progress",
                              "summary":"Apply review feedback"
                            }
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.columnId").isEqualTo("in_progress");

            Assertions.assertNull(controlMessages.poll(500, TimeUnit.MILLISECONDS));

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/commands", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1);
        } finally {
            subscription.dispose();
            applicationContext.getBean(me.golemcore.hive.adapter.inbound.ws.InMemoryGolemControlChannelAdapter.class)
                    .unregister(developer.golemId(), sink);
        }
    }

    @Test
    void shouldCancelQueuedCommandBeforeDispatch() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Queue", "host-queue", "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Cancel queued work", "ready", developer.golemId());
        String threadId = getThreadId(operatorToken, cardId);
        CommandEnvelope command = createCommand(operatorToken, threadId, "Queue this command and then cancel it.");

        webTestClient.post()
                .uri("/api/v1/threads/{threadId}/runs/{runId}/cancel", threadId, command.runId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/commands", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("CANCELLED")
                .jsonPath("$[0].queueReason").isEqualTo("Cancelled by operator before dispatch");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/runs", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("CANCELLED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/messages", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[1].type").isEqualTo("COMMAND_STATUS")
                .jsonPath("$[1].participantType").isEqualTo("OPERATOR")
                .jsonPath("$[1].body").isEqualTo("Cancelled queued command before dispatch");
    }

    @Test
    void shouldSendCancelControlEnvelopeForDeliveredRun() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Live", "host-live", "developer");
        BlockingQueue<String> controlMessages = new LinkedBlockingQueue<>();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        Disposable subscription = applicationContext
                .getBean(me.golemcore.hive.adapter.inbound.ws.InMemoryGolemControlChannelAdapter.class)
                .register(developer.golemId(), sink)
                .subscribe(controlMessages::add);
        try {
            String boardId = createBoard(operatorToken);
            String cardId = createCard(operatorToken, boardId, "Cancel active work", "ready", developer.golemId());
            String threadId = getThreadId(operatorToken, cardId);
            CommandEnvelope command = createCommand(operatorToken, threadId, "Dispatch and then request cancellation.",
                    "DELIVERED");

            JsonNode initialEnvelope = objectMapper.readTree(pollControlMessage(controlMessages));
            Assertions.assertEquals("command", initialEnvelope.get("eventType").asText());
            Assertions.assertEquals(command.commandId(), initialEnvelope.get("commandId").asText());

            webTestClient.post()
                    .uri("/api/v1/threads/{threadId}/runs/{runId}/cancel", threadId, command.runId())
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("QUEUED")
                    .jsonPath("$.cancelRequestedAt").isNotEmpty()
                    .jsonPath("$.cancelRequestedByActorName").isNotEmpty();

            JsonNode cancelEnvelope = objectMapper.readTree(pollControlMessage(controlMessages));
            Assertions.assertEquals("command.cancel", cancelEnvelope.get("eventType").asText());
            Assertions.assertEquals(command.commandId(), cancelEnvelope.get("commandId").asText());
            Assertions.assertEquals(command.runId(), cancelEnvelope.get("runId").asText());
            Assertions.assertTrue(cancelEnvelope.path("body").isMissingNode() || cancelEnvelope.path("body").isNull());

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/commands", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].status").isEqualTo("DELIVERED")
                    .jsonPath("$[0].cancelRequestedAt").isNotEmpty()
                    .jsonPath("$[0].cancelRequestedByActorName").isNotEmpty();

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/runs", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].status").isEqualTo("QUEUED")
                    .jsonPath("$[0].cancelRequestedAt").isNotEmpty()
                    .jsonPath("$[0].cancelRequestedByActorName").isNotEmpty();

            webTestClient.get()
                    .uri("/api/v1/cards/{cardId}", cardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.controlState.runId").isEqualTo(command.runId())
                    .jsonPath("$.controlState.commandId").isEqualTo(command.commandId())
                    .jsonPath("$.controlState.cancelRequestedPending").isEqualTo(true)
                    .jsonPath("$.controlState.canCancel").isEqualTo(false);

            webTestClient.get()
                    .uri("/api/v1/cards?boardId={boardId}", boardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].controlState.runId").isEqualTo(command.runId())
                    .jsonPath("$[0].controlState.cancelRequestedPending").isEqualTo(true);

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/messages", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[1].type").isEqualTo("COMMAND_STATUS")
                    .jsonPath("$[1].participantType").isEqualTo("OPERATOR")
                    .jsonPath("$[1].body").isEqualTo("Requested stop for active run");

            webTestClient.post()
                    .uri("/api/v1/golems/{golemId}/events:batch", developer.golemId())
                    .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "schemaVersion": 1,
                              "golemId": "%s",
                              "events": [
                                {
                                  "eventType": "runtime_event",
                                  "runtimeEventType": "RUN_CANCELLED",
                                  "cardId": "%s",
                                  "threadId": "%s",
                                  "commandId": "%s",
                                  "runId": "%s",
                                  "summary": "Run cancelled by operator"
                                },
                                {
                                  "eventType": "card_lifecycle_signal",
                                  "signalId": "sig_cancelled",
                                  "cardId": "%s",
                                  "threadId": "%s",
                                  "commandId": "%s",
                                  "runId": "%s",
                                  "signalType": "WORK_CANCELLED",
                                  "summary": "Work cancelled after operator stop request"
                                }
                              ]
                            }
                            """.formatted(
                            developer.golemId(),
                            cardId,
                            threadId,
                            command.commandId(),
                            command.runId(),
                            cardId,
                            threadId,
                            command.commandId(),
                            command.runId()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.acceptedEvents").isEqualTo(2)
                    .jsonPath("$.runtimeEvents").isEqualTo(1)
                    .jsonPath("$.lifecycleSignals").isEqualTo(1);

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/commands", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].status").isEqualTo("CANCELLED")
                    .jsonPath("$[0].cancelRequestedAt").isNotEmpty();

            webTestClient.get()
                    .uri("/api/v1/threads/{threadId}/runs", threadId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].status").isEqualTo("CANCELLED")
                    .jsonPath("$[0].lastSignalType").isEqualTo("WORK_CANCELLED")
                    .jsonPath("$[0].cancelRequestedAt").isNotEmpty();

            webTestClient.get()
                    .uri("/api/v1/cards/{cardId}", cardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.controlState").isEmpty();

            webTestClient.get()
                    .uri("/api/v1/cards?boardId={boardId}", boardId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].controlState").isEmpty();
        } finally {
            subscription.dispose();
            applicationContext.getBean(me.golemcore.hive.adapter.inbound.ws.InMemoryGolemControlChannelAdapter.class)
                    .unregister(developer.golemId(), sink);
        }
    }

    private String createBoard(String operatorToken) throws Exception {
        EntityExchangeResult<String> createBoardResult = webTestClient.post()
                .uri("/api/v1/boards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Phase 4 Board",
                          "description":"Thread and lifecycle integration",
                          "templateKey":"engineering",
                          "defaultAssignmentPolicy":"MANUAL"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode boardPayload = objectMapper.readTree(createBoardResult.getResponseBody());
        return boardPayload.get("id").asText();
    }

    private String createCard(String operatorToken, String boardId, String title, String columnId,
            String assigneeGolemId) throws Exception {
        EntityExchangeResult<String> createCardResult = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "boardId":"%s",
                          "title":"%s",
                          "description":"Integration test card",
                          "prompt":"Execute the full card prompt and report concrete progress.",
                          "columnId":"%s",
                          "assigneeGolemId":"%s",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(boardId, title, columnId, assigneeGolemId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode cardPayload = objectMapper.readTree(createCardResult.getResponseBody());
        return cardPayload.get("id").asText();
    }

    private String getThreadId(String operatorToken, String cardId) throws Exception {
        EntityExchangeResult<String> threadResult = webTestClient.get()
                .uri("/api/v1/cards/{cardId}/thread", cardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(threadResult.getResponseBody());
        return payload.get("threadId").asText();
    }

    private CommandEnvelope createCommand(String operatorToken, String threadId, String body) throws Exception {
        return createCommand(operatorToken, threadId, body, "QUEUED");
    }

    private CommandEnvelope createCommand(String operatorToken, String threadId, String body, String expectedStatus)
            throws Exception {
        EntityExchangeResult<String> commandResult = webTestClient.post()
                .uri("/api/v1/threads/{threadId}/commands", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "body":"%s"
                        }
                        """.formatted(body))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(commandResult.getResponseBody());
        Assertions.assertEquals(expectedStatus, payload.get("status").asText());
        return new CommandEnvelope(payload.get("id").asText(), payload.get("runId").asText());
    }

    private String pollControlMessage(BlockingQueue<String> controlMessages) throws InterruptedException {
        String payload = controlMessages.poll(2, TimeUnit.SECONDS);
        Assertions.assertNotNull(payload);
        return payload;
    }

    private void createRole(String operatorToken, String roleSlug) {
        EntityExchangeResult<String> result = webTestClient.post()
                .uri("/api/v1/golem-roles")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "slug":"%s",
                          "name":"Developer",
                          "description":"Builds product features",
                          "capabilityTags":["java","spring","react"]
                        }
                        """.formatted(roleSlug))
                .exchange()
                .expectBody(String.class)
                .returnResult();
        int status = result.getStatus().value();
        if (status == 201) {
            return;
        }
        Assertions.assertEquals(400, status);
        Assertions.assertTrue(
                result.getResponseBody() != null && result.getResponseBody().contains("Role already exists"));
    }

    private RegisteredGolem registerOnlineGolem(String operatorToken, String displayName, String hostLabel,
            String roleSlug) throws Exception {
        EntityExchangeResult<String> enrollmentTokenResult = webTestClient.post()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "note":"%s enrollment",
                          "expiresInMinutes":60
                        }
                        """.formatted(displayName))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode enrollmentTokenPayload = objectMapper.readTree(enrollmentTokenResult.getResponseBody());
        String enrollmentToken = enrollmentTokenPayload.get("token").asText();

        EntityExchangeResult<String> registerResult = webTestClient.post()
                .uri("/api/v1/golems/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "enrollmentToken":"%s",
                          "displayName":"%s",
                          "hostLabel":"%s",
                          "runtimeVersion":"bot-1.2.3",
                          "buildVersion":"build-42",
                          "supportedChannels":["control","events"],
                          "capabilities":{
                            "providers":["openai"],
                            "modelFamilies":["gpt"],
                            "enabledTools":["shell","git"],
                            "enabledAutonomyFeatures":["planning"],
                            "capabilityTags":["review","java"],
                            "supportedChannels":["control","events"],
                            "snapshotHash":"abc123",
                            "defaultModel":"gpt-5"
                          }
                        }
                        """.formatted(enrollmentToken, displayName, hostLabel))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode registerPayload = objectMapper.readTree(registerResult.getResponseBody());
        String golemId = registerPayload.get("golemId").asText();
        String accessToken = "Bearer " + registerPayload.get("accessToken").asText();

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/heartbeat", golemId)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "status":"healthy",
                          "currentRunState":"IDLE",
                          "modelTier":"pro",
                          "queueDepth":0,
                          "healthSummary":"ready",
                          "uptimeSeconds":90,
                          "capabilitySnapshotHash":"abc123"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        if (roleSlug != null) {
            webTestClient.post()
                    .uri("/api/v1/golems/{golemId}/roles:assign", golemId)
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "roleSlugs":["%s"]
                            }
                            """.formatted(roleSlug))
                    .exchange()
                    .expectStatus().isOk();
        }

        return new RegisteredGolem(golemId, accessToken);
    }

    private String loginAsAdmin() throws Exception {
        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "username":"admin",
                          "password":"change-me-now"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(loginResult.getResponseBody());
        String accessToken = payload.get("accessToken").asText();
        Assertions.assertNotNull(accessToken);
        return "Bearer " + accessToken;
    }

    private record RegisteredGolem(String golemId, String accessToken) {
    }

    private record CommandEnvelope(String commandId, String runId) {
    }
}
