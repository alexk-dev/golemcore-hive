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
import java.nio.file.Files;
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
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApprovalsControllerSelfEvolvingIntegrationTest {

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
    void shouldExposePromotionApprovalContextInApprovalResponses() throws Exception {
        Files.createDirectories(tempDir.resolve("approvals"));
        Files.writeString(
                tempDir.resolve("approvals/apr_promo_1.json"),
                """
                        {
                          "schemaVersion": 1,
                          "id": "apr_promo_1",
                          "golemId": "golem-1",
                          "requestedByActorType": "OPERATOR",
                          "requestedByActorId": "operator-1",
                          "requestedByActorName": "Hive Admin",
                          "status": "PENDING",
                          "requestedAt": "2026-03-31T18:00:00Z",
                          "updatedAt": "2026-03-31T18:00:00Z",
                          "subjectType": "SELF_EVOLVING_PROMOTION",
                          "promotionContext": {
                            "candidateId": "candidate-1",
                            "goal": "fix",
                            "artifactType": "skill",
                            "riskLevel": "medium",
                            "expectedImpact": "Reduce routing failures",
                            "sourceRunIds": ["run-1", "run-2"]
                          }
                        }
                        """);

        String operatorToken = loginAsAdmin();

        webTestClient.get()
                .uri("/api/v1/approvals/{approvalId}", "apr_promo_1")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("apr_promo_1")
                .jsonPath("$.subjectType").isEqualTo("SELF_EVOLVING_PROMOTION")
                .jsonPath("$.promotionContext.candidateId").isEqualTo("candidate-1")
                .jsonPath("$.promotionContext.sourceRunIds[0]").isEqualTo("run-1");
    }

    private String loginAsAdmin() throws Exception {
        String responseBody = webTestClient.post()
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
                .returnResult()
                .getResponseBody();
        JsonNode payload = objectMapper.readTree(responseBody);
        String accessToken = payload.get("accessToken").asText();
        Assertions.assertNotNull(accessToken);
        return "Bearer " + accessToken;
    }
}
