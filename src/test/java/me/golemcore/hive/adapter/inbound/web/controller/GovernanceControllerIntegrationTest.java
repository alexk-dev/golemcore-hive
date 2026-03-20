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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GovernanceControllerIntegrationTest {

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
    void shouldCreateApprovalAuditBudgetAndNotificationRecords() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem golem = registerOnlineGolem(operatorToken, "Atlas Gov", "host-gov", "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Ship governance", "ready", golem.golemId());
        String threadId = getThreadId(operatorToken, cardId);

        EntityExchangeResult<String> commandResult = webTestClient.post()
                .uri("/api/v1/threads/{threadId}/commands", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "body":"Run a destructive cleanup on the assigned workspace.",
                          "approvalRiskLevel":"DESTRUCTIVE",
                          "estimatedCostMicros":7000000,
                          "approvalReason":"Deletes local files and exceeds the budget threshold."
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        JsonNode commandPayload = objectMapper.readTree(commandResult.getResponseBody());
        Assertions.assertEquals("PENDING_APPROVAL", commandPayload.get("status").asText());
        String approvalId = commandPayload.get("approvalRequestId").asText();

        webTestClient.get()
                .uri("/api/v1/approvals?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(approvalId)
                .jsonPath("$[0].riskLevel").isEqualTo("DESTRUCTIVE");

        webTestClient.post()
                .uri("/api/v1/approvals/{approvalId}:approve", approvalId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "comment":"Approved for supervised cleanup."
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED");

        webTestClient.get()
                .uri("/api/v1/threads/{threadId}/commands", threadId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("QUEUED")
                .jsonPath("$[0].approvalRequestId").isEqualTo(approvalId);

        webTestClient.get()
                .uri("/api/v1/audit")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.eventType=='approval.requested')]").exists()
                .jsonPath("$[?(@.eventType=='approval.approved')]").exists();

        webTestClient.get()
                .uri("/api/v1/budgets?scopeType=SYSTEM")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].estimatedPendingCostMicros").isEqualTo(7000000)
                .jsonPath("$[0].commandCount").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/system/settings")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.highCostThresholdMicros").isEqualTo(5000000)
                .jsonPath("$.recentNotifications[0].type").isEqualTo("APPROVAL_REQUESTED");
    }

    private String createBoard(String operatorToken) throws Exception {
        EntityExchangeResult<String> createBoardResult = webTestClient.post()
                .uri("/api/v1/boards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Phase 5 Board",
                          "description":"Governance integration",
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
            String assigneeGolemId)
            throws Exception {
        EntityExchangeResult<String> createCardResult = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "boardId":"%s",
                          "title":"%s",
                          "description":"Integration test card",
                          "prompt":"Execute the governance task and report approval-sensitive details.",
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

    private void createRole(String operatorToken, String roleSlug) {
        webTestClient.post()
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
                .expectStatus().isCreated();
    }

    private RegisteredGolem registerOnlineGolem(String operatorToken, String displayName, String hostLabel,
            String roleSlug)
            throws Exception {
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
}
