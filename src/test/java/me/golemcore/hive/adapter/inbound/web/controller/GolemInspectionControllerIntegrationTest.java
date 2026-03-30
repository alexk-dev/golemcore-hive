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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.service.AuditService;
import me.golemcore.hive.domain.service.GolemControlChannelService;
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
class GolemInspectionControllerIntegrationTest {

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
    void shouldListSessionsThroughInspectionRpcAndRecordAuditEvent() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerOnlineGolem("Atlas Inspect", "host-inspect");
        BlockingQueue<String> controlMessages = new LinkedBlockingQueue<>();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        GolemControlChannelService controlChannelService = applicationContext.getBean(GolemControlChannelService.class);
        Disposable subscription = controlChannelService.register(golem.golemId(), sink).subscribe(controlMessages::add);
        try {
            CompletableFuture<EntityExchangeResult<String>> inspectionFuture = CompletableFuture
                    .supplyAsync(() -> webTestClient
                            .get()
                            .uri("/api/v1/golems/{golemId}/inspection/sessions?channel=web", golem.golemId())
                            .header(HttpHeaders.AUTHORIZATION, operatorToken)
                            .exchange()
                            .expectStatus().isOk()
                            .expectBody(String.class)
                            .returnResult());

            JsonNode envelope = objectMapper.readTree(pollControlMessage(controlMessages));
            Assertions.assertEquals("inspection.request", envelope.get("eventType").asText());
            Assertions.assertEquals("sessions.list", envelope.get("inspection").get("operation").asText());
            String requestId = envelope.get("requestId").asText();

            webTestClient.post()
                    .uri("/api/v1/golems/{golemId}/events:batch", golem.golemId())
                    .header(HttpHeaders.AUTHORIZATION, golem.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "schemaVersion": 1,
                              "golemId": "%s",
                              "events": [
                                {
                                  "eventType": "inspection_response",
                                  "requestId": "%s",
                                  "threadId": "inspection:%s",
                                  "operation": "sessions.list",
                                  "success": true,
                                  "payload": [
                                    {
                                      "id": "web:conv-1",
                                      "channelType": "web",
                                      "chatId": "legacy-chat",
                                      "conversationKey": "conv-1",
                                      "transportChatId": "client-1",
                                      "messageCount": 3,
                                      "state": "ACTIVE",
                                      "title": "Conv 1",
                                      "preview": "hello"
                                    }
                                  ],
                                  "createdAt": "2026-03-30T21:00:01Z"
                                }
                              ]
                            }
                            """.formatted(golem.golemId(), requestId, golem.golemId()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.acceptedEvents").isEqualTo(1);

            EntityExchangeResult<String> inspectionResult = inspectionFuture.get(5, TimeUnit.SECONDS);
            JsonNode payload = objectMapper.readTree(inspectionResult.getResponseBody());
            Assertions.assertEquals("web:conv-1", payload.get(0).get("id").asText());

            List<AuditEvent> auditEvents = applicationContext.getBean(AuditService.class)
                    .listEvents(null, golem.golemId(), null, null, null, null, "golem.inspection.requested");
            Assertions.assertEquals(1, auditEvents.size());
        } finally {
            subscription.dispose();
            controlChannelService.unregister(golem.golemId(), sink);
        }
    }

    @Test
    void shouldRejectInspectionWhenGolemIsNotConnected() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerOnlineGolem("Atlas Offline Inspect", "host-offline");

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}/inspection/sessions", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("GOLEM_OFFLINE")
                .jsonPath("$.retryable").isEqualTo(true);
    }

    private String pollControlMessage(BlockingQueue<String> controlMessages) throws InterruptedException {
        String payload = controlMessages.poll(2, TimeUnit.SECONDS);
        Assertions.assertNotNull(payload);
        return payload;
    }

    private RegisteredGolem registerOnlineGolem(String displayName, String hostLabel) throws Exception {
        EntityExchangeResult<String> enrollmentTokenResult = webTestClient.post()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, loginAsAdmin())
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
}
