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
import java.util.HashSet;
import java.util.Set;
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
class BoardControllerIntegrationTest {

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
        registry.add("hive.bootstrap.admin.enabled", () -> true);
        registry.add("hive.bootstrap.admin.username", () -> "admin");
        registry.add("hive.bootstrap.admin.password", () -> "change-me-now");
        registry.add("hive.bootstrap.admin.display-name", () -> "Hive Admin");
    }

    @Test
    void shouldManageBoardsCardsAssignmentsAndFlowRemap() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");

        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Dev", "host-dev", "developer");
        RegisteredGolem fallback = registerOnlineGolem(operatorToken, "Scout Ops", "host-ops", null);

        String boardId = createBoard(operatorToken);
        updateBoardTeam(operatorToken, boardId, developer.golemId());

        webTestClient.get()
                .uri("/api/v1/boards/{boardId}/team", boardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.boardId").isEqualTo(boardId)
                .jsonPath("$.candidates[0].golemId").isEqualTo(developer.golemId())
                .jsonPath("$.candidates[0].reasons[0]").isEqualTo("Matches role filter: developer");

        String residentCardId = createCard(
                operatorToken,
                boardId,
                """
                        {
                          "boardId":"%s",
                          "title":"Resident in progress card",
                          "description":"Keeps the target column occupied before remap.",
                          "prompt":"Continue the resident in-progress work item and report status.",
                          "columnId":"in_progress",
                          "assigneeGolemId":"%s",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(boardId, fallback.golemId()));

        EntityExchangeResult<String> autoAssignedCardResult = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "boardId":"%s",
                          "title":"Implement board filters",
                          "description":"Auto-assign against the board team.",
                          "prompt":"Implement board filters against the board team routing rules.",
                          "assignmentPolicy":"AUTOMATIC",
                          "autoAssign":true
                        }
                        """.formatted(boardId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode autoAssignedCardPayload = objectMapper.readTree(autoAssignedCardResult.getResponseBody());
        String autoAssignedCardId = autoAssignedCardPayload.get("id").asText();
        Assertions.assertEquals(developer.golemId(), autoAssignedCardPayload.get("assigneeGolemId").asText());
        Assertions.assertEquals("Implement board filters against the board team routing rules.",
                autoAssignedCardPayload.get("prompt").asText());
        Assertions.assertTrue(autoAssignedCardPayload.get("threadId").asText().startsWith("thread_"));

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}/assignees", autoAssignedCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.teamCandidates[0].golemId").isEqualTo(developer.golemId())
                .jsonPath("$.allCandidates[0].golemId").isEqualTo(developer.golemId())
                .jsonPath("$.allCandidates[1].golemId").isEqualTo(fallback.golemId());

        webTestClient.post()
                .uri("/api/v1/cards/{cardId}:move", autoAssignedCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "targetColumnId":"ready",
                          "targetIndex":0,
                          "summary":"Manual promotion to ready"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.columnId").isEqualTo("ready");

        webTestClient.post()
                .uri("/api/v1/boards/{boardId}/flow:preview", boardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(flowWithoutReady())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.removedColumnIds[0]").isEqualTo("ready")
                .jsonPath("$.affectedCardCounts.ready").isEqualTo(1);

        webTestClient.put()
                .uri("/api/v1/boards/{boardId}/flow", boardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "flow": %s,
                          "columnRemap": {
                            "ready": "in_progress"
                          }
                        }
                        """.formatted(flowWithoutReady()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.flow.columns.length()").isEqualTo(5);

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}", autoAssignedCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.columnId").isEqualTo("in_progress")
                .jsonPath("$.transitions[2].origin").isEqualTo("FLOW_REMAP")
                .jsonPath("$.transitions[2].toColumnId").isEqualTo("in_progress");

        EntityExchangeResult<String> cardsResult = webTestClient.get()
                .uri("/api/v1/cards?boardId={boardId}", boardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        JsonNode cardsPayload = objectMapper.readTree(cardsResult.getResponseBody());
        Set<Integer> inProgressPositions = new HashSet<>();
        int inProgressCount = 0;
        for (JsonNode cardNode : cardsPayload) {
            String cardId = cardNode.get("id").asText();
            if (residentCardId.equals(cardId) || autoAssignedCardId.equals(cardId)) {
                Assertions.assertEquals("in_progress", cardNode.get("columnId").asText());
                inProgressPositions.add(cardNode.get("position").asInt());
                inProgressCount++;
            }
        }
        Assertions.assertEquals(2, inProgressCount);
        Assertions.assertEquals(Set.of(0, 1), inProgressPositions);
    }

    @Test
    void shouldRequirePromptWhenCreatingCard() throws Exception {
        String operatorToken = loginAsAdmin();
        String boardId = createBoard(operatorToken);

        webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "boardId":"%s",
                          "title":"Card without prompt",
                          "description":"This request should fail validation.",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(boardId))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private String createBoard(String operatorToken) throws Exception {
        EntityExchangeResult<String> createBoardResult = webTestClient.post()
                .uri("/api/v1/boards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Platform Flow",
                          "description":"Board API integration test",
                          "templateKey":"engineering",
                          "defaultAssignmentPolicy":"AUTOMATIC"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode boardPayload = objectMapper.readTree(createBoardResult.getResponseBody());
        return boardPayload.get("id").asText();
    }

    private void updateBoardTeam(String operatorToken, String boardId, String golemId) {
        webTestClient.put()
                .uri("/api/v1/boards/{boardId}/team", boardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "explicitGolemIds": [],
                          "filters": [
                            {
                              "type":"ROLE_SLUG",
                              "value":"developer"
                            }
                          ]
                        }
                        """.formatted(golemId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.team.filters[0].value").isEqualTo("developer");
    }

    private String createCard(String operatorToken, String boardId, String body) throws Exception {
        EntityExchangeResult<String> result = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(result.getResponseBody());
        return payload.get("id").asText();
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
        String golemAccessToken = "Bearer " + registerPayload.get("accessToken").asText();

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/heartbeat", golemId)
                .header(HttpHeaders.AUTHORIZATION, golemAccessToken)
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

        return new RegisteredGolem(golemId);
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

    private String flowWithoutReady() {
        return """
                {
                  "flowId":"engineering-default",
                  "name":"Engineering",
                  "defaultColumnId":"inbox",
                  "columns":[
                    {"id":"inbox","name":"Inbox","description":"Fresh requests","wipLimit":null,"terminal":false},
                    {"id":"in_progress","name":"In Progress","description":"Active work","wipLimit":null,"terminal":false},
                    {"id":"blocked","name":"Blocked","description":"Waiting on unblock","wipLimit":null,"terminal":false},
                    {"id":"review","name":"Review","description":"Needs review","wipLimit":null,"terminal":false},
                    {"id":"done","name":"Done","description":"Finished","wipLimit":null,"terminal":true}
                  ],
                  "transitions":[
                    {"fromColumnId":"inbox","toColumnId":"in_progress"},
                    {"fromColumnId":"in_progress","toColumnId":"blocked"},
                    {"fromColumnId":"blocked","toColumnId":"in_progress"},
                    {"fromColumnId":"in_progress","toColumnId":"review"},
                    {"fromColumnId":"review","toColumnId":"in_progress"},
                    {"fromColumnId":"review","toColumnId":"done"}
                  ],
                  "signalMappings":[
                    {"signalType":"WORK_STARTED","decision":"AUTO_APPLY","targetColumnId":"in_progress"},
                    {"signalType":"BLOCKER_RAISED","decision":"AUTO_APPLY","targetColumnId":"blocked"},
                    {"signalType":"BLOCKER_CLEARED","decision":"SUGGEST_ONLY","targetColumnId":"in_progress"},
                    {"signalType":"REVIEW_REQUESTED","decision":"AUTO_APPLY","targetColumnId":"review"},
                    {"signalType":"WORK_COMPLETED","decision":"AUTO_APPLY","targetColumnId":"review"},
                    {"signalType":"PROGRESS_REPORTED","decision":"IGNORE","targetColumnId":null}
                  ]
                }
                """;
    }

    private record RegisteredGolem(String golemId) {
    }
}
