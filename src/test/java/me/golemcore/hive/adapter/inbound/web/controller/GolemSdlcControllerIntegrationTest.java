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
class GolemSdlcControllerIntegrationTest {

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
    void shouldAllowGolemMachineTokenToUseSdlcEndpointsForAssignedCard() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas SDLC", "host-sdlc", "developer");
        RegisteredGolem reviewer = registerOnlineGolem(operatorToken, "Review SDLC", "host-review", "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Machine SDLC access", "ready", developer.golemId());
        String threadId = getThreadId(operatorToken, cardId);

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}/sdlc/cards/{cardId}", developer.golemId(), cardId)
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(cardId)
                .jsonPath("$.threadId").isEqualTo(threadId);

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}/sdlc/cards?boardId={boardId}", developer.golemId(), boardId)
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(cardId);

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/sdlc/threads/{threadId}/messages", developer.golemId(), threadId)
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "body":"SDLC note from golem"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.participantType").isEqualTo("GOLEM")
                .jsonPath("$.authorId").isEqualTo(developer.golemId())
                .jsonPath("$.body").isEqualTo("SDLC note from golem");

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/sdlc/cards/{cardId}:request-review", developer.golemId(), cardId)
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "reviewerGolemIds":["%s"],
                          "requiredReviewCount":1
                        }
                        """.formatted(reviewer.golemId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.reviewStatus").isEqualTo("REQUIRED")
                .jsonPath("$.reviewerGolemIds[0]").isEqualTo(reviewer.golemId());

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/sdlc/cards", developer.golemId())
                .header(HttpHeaders.AUTHORIZATION, developer.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "title":"Follow-up from golem",
                          "description":"Created by machine SDLC API",
                          "prompt":"Investigate the follow-up.",
                          "parentCardId":"%s",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(cardId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.parentCardId").isEqualTo(cardId)
                .jsonPath("$.title").isEqualTo("Follow-up from golem");
    }

    @Test
    void shouldDenyGolemSdlcAccessToUnassignedCards() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Owner", "host-owner", "developer");
        RegisteredGolem other = registerOnlineGolem(operatorToken, "Atlas Other", "host-other", "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Private SDLC card", "ready", developer.golemId());

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}/sdlc/cards/{cardId}", other.golemId(), cardId)
                .header(HttpHeaders.AUTHORIZATION, other.accessToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectLifecycleSignalsFromGolemThatCannotAccessCard() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem owner = registerOnlineGolem(operatorToken, "Atlas Signal Owner", "host-signal-owner",
                "developer");
        RegisteredGolem other = registerOnlineGolem(operatorToken, "Atlas Signal Other", "host-signal-other",
                "developer");
        String boardId = createBoard(operatorToken);
        String cardId = createCard(operatorToken, boardId, "Private signal card", "ready", owner.golemId());
        String threadId = getThreadId(operatorToken, cardId);

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/events:batch", other.golemId())
                .header(HttpHeaders.AUTHORIZATION, other.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion": 1,
                          "golemId": "%s",
                          "events": [
                            {
                              "eventType": "card_lifecycle_signal",
                              "signalId": "sig_cross_golem_started",
                              "cardId": "%s",
                              "threadId": "%s",
                              "signalType": "WORK_STARTED",
                              "summary": "Cross-golem work started"
                            }
                          ]
                        }
                        """.formatted(other.golemId(), cardId, threadId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectGolemCreatedCardWhenAnyRelatedCardIsInaccessible() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem owner = registerOnlineGolem(operatorToken, "Atlas Link Owner", "host-link-owner", "developer");
        RegisteredGolem other = registerOnlineGolem(operatorToken, "Atlas Link Other", "host-link-other", "developer");
        String boardId = createBoard(operatorToken);
        String ownerCardId = createCard(operatorToken, boardId, "Accessible parent", "ready", owner.golemId());
        String otherCardId = createCard(operatorToken, boardId, "Inaccessible dependency", "ready", other.golemId());

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/sdlc/cards", owner.golemId())
                .header(HttpHeaders.AUTHORIZATION, owner.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "title":"Blocked follow-up",
                          "description":"Should not be allowed",
                          "prompt":"Investigate the inaccessible dependency.",
                          "parentCardId":"%s",
                          "dependsOnCardIds":["%s"],
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(ownerCardId, otherCardId))
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/sdlc/cards", owner.golemId())
                .header(HttpHeaders.AUTHORIZATION, owner.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "title":"Blocked review follow-up",
                          "description":"Should not be allowed",
                          "prompt":"Investigate the inaccessible review target.",
                          "parentCardId":"%s",
                          "reviewOfCardId":"%s",
                          "kind":"REVIEW",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(ownerCardId, otherCardId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectLifecycleSignalsWithMismatchedRunCard() throws Exception {
        String operatorToken = loginAsAdmin();
        createRole(operatorToken, "developer");
        RegisteredGolem developer = registerOnlineGolem(operatorToken, "Atlas Run Guard", "host-run-guard",
                "developer");
        String boardId = createBoard(operatorToken);
        String firstCardId = createCard(operatorToken, boardId, "First guarded card", "ready", developer.golemId());
        String secondCardId = createCard(operatorToken, boardId, "Second guarded card", "ready", developer.golemId());
        String firstThreadId = getThreadId(operatorToken, firstCardId);
        String secondThreadId = getThreadId(operatorToken, secondCardId);
        CommandEnvelope firstCommand = createCommand(operatorToken, firstThreadId, "Start guarded work.");

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
                              "signalId": "sig_mismatched_run_card",
                              "cardId": "%s",
                              "threadId": "%s",
                              "commandId": "%s",
                              "runId": "%s",
                              "signalType": "WORK_STARTED",
                              "summary": "Mismatched run card"
                            }
                          ]
                        }
                        """.formatted(
                        developer.golemId(),
                        secondCardId,
                        secondThreadId,
                        firstCommand.commandId(),
                        firstCommand.runId()))
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
                          "name":"SDLC Board",
                          "description":"Machine SDLC integration",
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
        return new CommandEnvelope(payload.get("id").asText(), payload.get("runId").asText());
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
