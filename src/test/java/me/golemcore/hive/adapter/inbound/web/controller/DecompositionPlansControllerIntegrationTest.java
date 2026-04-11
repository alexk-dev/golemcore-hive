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
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecompositionPlansControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OperatorAuthApplicationService operatorAuthApplicationService;

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
    void shouldCreateApproveAndApplyDecompositionPlan() throws Exception {
        String operatorToken = loginAsAdmin();
        String boardId = createBoard(operatorToken);
        String sourceCardId = createSourceCard(operatorToken, boardId);

        EntityExchangeResult<String> planResult = webTestClient.post()
                .uri("/api/v1/cards/{cardId}/decomposition-plans", sourceCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "rationale":"Break a large work item into executable pieces",
                          "items":[
                            {
                              "clientItemId":"task-1",
                              "kind":"TASK",
                              "title":"Draft analysis",
                              "description":"Analyze the source task",
                              "prompt":"Analyze the source task and write the breakdown.",
                              "acceptanceCriteria":["analysis complete"]
                            },
                            {
                              "clientItemId":"task-2",
                              "kind":"TASK",
                              "title":"Implement slice",
                              "description":"Implement the first slice",
                              "prompt":"Implement the first slice and report completion.",
                              "acceptanceCriteria":["implementation complete"]
                            }
                          ],
                          "links":[
                            {
                              "fromClientItemId":"task-2",
                              "toClientItemId":"task-1",
                              "type":"DEPENDS_ON"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode createdPlan = objectMapper.readTree(planResult.getResponseBody());
        String planId = createdPlan.get("id").asText();
        Assertions.assertEquals("DRAFT", createdPlan.get("status").asText());
        Assertions.assertEquals(2, createdPlan.get("items").size());

        webTestClient.post()
                .uri("/api/v1/decomposition-plans/{planId}:approve", planId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "comment":"Approved for execution"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED");

        EntityExchangeResult<String> applyResult = webTestClient.post()
                .uri("/api/v1/decomposition-plans/{planId}:apply", planId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        JsonNode appliedPlan = objectMapper.readTree(applyResult.getResponseBody());
        Assertions.assertFalse(appliedPlan.get("alreadyApplied").asBoolean());
        Assertions.assertEquals("APPLIED", appliedPlan.get("plan").get("status").asText());
        Assertions.assertEquals(2, appliedPlan.get("createdCards").size());
        Assertions.assertEquals("task-1", appliedPlan.get("createdCards").get(0).get("clientItemId").asText());
        Assertions.assertEquals("task-2", appliedPlan.get("createdCards").get(1).get("clientItemId").asText());
        Assertions.assertTrue(appliedPlan.get("createdCards").get(0).get("cardId").asText().startsWith("card_"));
        Assertions.assertTrue(appliedPlan.get("createdCards").get(1).get("cardId").asText().startsWith("card_"));

        webTestClient.get()
                .uri("/api/v1/cards/{cardId}/decomposition-plans", sourceCardId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("APPLIED")
                .jsonPath("$[0].items[0].createdCardId").exists()
                .jsonPath("$[0].items[1].createdCardId").exists();
    }

    private String createBoard(String operatorToken) throws Exception {
        EntityExchangeResult<String> createBoardResult = webTestClient.post()
                .uri("/api/v1/boards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "name":"Planning Service",
                          "description":"Service used by decomposition tests",
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

    private String createSourceCard(String operatorToken, String boardId) throws Exception {
        EntityExchangeResult<String> result = webTestClient.post()
                .uri("/api/v1/cards")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "boardId":"%s",
                          "title":"Source work item",
                          "description":"This task should be decomposed",
                          "prompt":"Decompose the work into smaller items.",
                          "columnId":"inbox",
                          "assignmentPolicy":"MANUAL",
                          "autoAssign":false
                        }
                        """.formatted(boardId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(result.getResponseBody());
        return payload.get("id").asText();
    }

    private String loginAsAdmin() throws Exception {
        String accessToken = operatorAuthApplicationService.authenticate("admin", "change-me-now")
                .orElseThrow()
                .accessToken();
        Assertions.assertNotNull(accessToken);
        return "Bearer " + accessToken;
    }
}
