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
class OrganizationControllerIntegrationTest {

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
    void shouldManageOrganizationTeamsAndObjectives() throws Exception {
        String operatorToken = loginAsAdmin();

        webTestClient.get()
                .uri("/api/v1/organization")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Hive Organization");

        String serviceId = createService(operatorToken);
        String golemId = registerOnlineGolem(operatorToken, "Atlas Org", "host-org");
        String teamId = createTeam(operatorToken, golemId, serviceId);
        String objectiveId = createObjective(operatorToken, teamId, serviceId);

        webTestClient.get()
                .uri("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(teamId)
                .jsonPath("$[0].golemIds[0]").isEqualTo(golemId)
                .jsonPath("$[0].ownedServiceIds[0]").isEqualTo(serviceId);

        webTestClient.get()
                .uri("/api/v1/objectives")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(objectiveId)
                .jsonPath("$[0].ownerTeamId").isEqualTo(teamId)
                .jsonPath("$[0].serviceIds[0]").isEqualTo(serviceId)
                .jsonPath("$[0].participatingTeamIds[0]").isEqualTo(teamId);

        webTestClient.patch()
                .uri("/api/v1/organization")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Golemcore",
                          "description":"Organization-root integration test"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Golemcore")
                .jsonPath("$.description").isEqualTo("Organization-root integration test");

        webTestClient.patch()
                .uri("/api/v1/objectives/{objectiveId}", objectiveId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "status":"AT_RISK",
                          "clearTargetDate":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AT_RISK")
                .jsonPath("$.targetDate").isEmpty();
    }

    @Test
    void shouldScopeCardsToServiceTeamAndObjective() throws Exception {
        String operatorToken = loginAsAdmin();
        String serviceId = createService(operatorToken);
        String golemId = registerOnlineGolem(operatorToken, "Atlas Scope", "host-scope");
        String teamId = createTeam(operatorToken, golemId, serviceId);
        String objectiveId = createObjective(operatorToken, teamId, serviceId);
        String foreignServiceId = createService(operatorToken);
        String foreignTeamId = createTeam(operatorToken, golemId, foreignServiceId);

        EntityExchangeResult<String> createCardResult = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "serviceId":"%s",
                          "title":"Ship scoped card",
                          "description":"Card linked to org entities",
                          "prompt":"Execute the scoped service card",
                          "columnId":"inbox",
                          "teamId":"%s",
                          "objectiveId":"%s",
                          "assigneeGolemId":"%s",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(serviceId, teamId, objectiveId, golemId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode cardPayload = objectMapper.readTree(createCardResult.getResponseBody());
        String cardId = cardPayload.get("id").asText();
        Assertions.assertEquals(serviceId, cardPayload.get("serviceId").asText());
        Assertions.assertEquals(serviceId, cardPayload.get("boardId").asText());
        Assertions.assertEquals(teamId, cardPayload.get("teamId").asText());
        Assertions.assertEquals(objectiveId, cardPayload.get("objectiveId").asText());

        webTestClient.get()
                .uri("/api/v1/cards?serviceId={serviceId}", serviceId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].serviceId").isEqualTo(serviceId)
                .jsonPath("$[0].boardId").isEqualTo(serviceId)
                .jsonPath("$[0].teamId").isEqualTo(teamId)
                .jsonPath("$[0].objectiveId").isEqualTo(objectiveId);

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}/thread", cardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceId").isEqualTo(serviceId)
                .jsonPath("$.boardId").isEqualTo(serviceId)
                .jsonPath("$.teamId").isEqualTo(teamId)
                .jsonPath("$.objectiveId").isEqualTo(objectiveId);

        webTestClient.patch()
                .uri("/api/v1/cards/{cardId}", cardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "teamId":""
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.teamId").isEmpty();

        webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "serviceId":"%s",
                          "title":"Reject foreign team",
                          "description":"Card must stay within service ownership",
                          "prompt":"Do not allow cross-service team routing",
                          "columnId":"inbox",
                          "teamId":"%s",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(serviceId, foreignTeamId))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.patch()
                .uri("/api/v1/cards/{cardId}", cardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "teamId":"%s",
                          "objectiveId":""
                        }
                        """.formatted(foreignTeamId))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private String createService(String operatorToken) throws Exception {
        EntityExchangeResult<String> createServiceResult = webTestClient.post()
                .uri("/api/v1/services")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Platform Service",
                          "description":"Organization API integration test",
                          "templateKey":"engineering",
                          "defaultAssignmentPolicy":"MANUAL"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode servicePayload = objectMapper.readTree(createServiceResult.getResponseBody());
        return servicePayload.get("id").asText();
    }

    private String createTeam(String operatorToken, String golemId, String serviceId) throws Exception {
        EntityExchangeResult<String> createTeamResult = webTestClient.post()
                .uri("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Platform Team",
                          "description":"Owns the primary service queue",
                          "golemIds":["%s"],
                          "ownedServiceIds":["%s"]
                        }
                        """.formatted(golemId, serviceId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode teamPayload = objectMapper.readTree(createTeamResult.getResponseBody());
        return teamPayload.get("id").asText();
    }

    private String createObjective(String operatorToken, String teamId, String serviceId) throws Exception {
        EntityExchangeResult<String> createObjectiveResult = webTestClient.post()
                .uri("/api/v1/objectives")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Reduce onboarding latency",
                          "description":"Cross-cutting outcome tracked above queues",
                          "status":"ACTIVE",
                          "ownerTeamId":"%s",
                          "serviceIds":["%s"],
                          "participatingTeamIds":[],
                          "targetDate":"2026-05-01"
                        }
                        """.formatted(teamId, serviceId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode objectivePayload = objectMapper.readTree(createObjectiveResult.getResponseBody());
        return objectivePayload.get("id").asText();
    }

    private String registerOnlineGolem(String operatorToken, String displayName, String hostLabel) throws Exception {
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

        return golemId;
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
}
