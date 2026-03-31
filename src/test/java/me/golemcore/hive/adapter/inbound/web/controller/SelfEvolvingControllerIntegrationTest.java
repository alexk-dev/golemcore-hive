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
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfEvolvingControllerIntegrationTest {

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
    void shouldReturnReadonlyRunProjectionForGolem() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerGolem("Atlas SelfEvolving", "host-selfevolving");

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
                              "eventType": "selfevolving.run.upserted",
                              "golemId": "%s",
                              "runId": "run-1",
                              "payload": {
                                "id": "run-1",
                                "golemId": "%s",
                                "sessionId": "session-1",
                                "traceId": "trace-1",
                                "artifactBundleId": "bundle-1",
                                "status": "COMPLETED",
                                "outcomeStatus": "COMPLETED",
                                "processStatus": "HEALTHY",
                                "promotionRecommendation": "approve_gated",
                                "outcomeSummary": "done",
                                "processSummary": "good",
                                "confidence": 0.91,
                                "processFindings": ["No tier escalation required"],
                                "startedAt": "2026-03-31T16:00:00Z",
                                "completedAt": "2026-03-31T16:00:30Z"
                              },
                              "createdAt": "2026-03-31T16:00:31Z"
                            }
                          ]
                        }
                        """.formatted(golem.golemId(), golem.golemId(), golem.golemId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.acceptedEvents").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/self-evolving/golems/{golemId}/runs/{runId}", golem.golemId(), "run-1")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("run-1")
                .jsonPath("$.golemId").isEqualTo(golem.golemId())
                .jsonPath("$.outcomeStatus").isEqualTo("COMPLETED");
    }

    private RegisteredGolem registerGolem(String displayName, String hostLabel) throws Exception {
        String operatorToken = loginAsAdmin();
        String enrollmentToken = createEnrollmentToken(operatorToken, displayName);

        String responseBody = webTestClient.post()
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
                            "enabledTools":["shell"],
                            "enabledAutonomyFeatures":["planning"],
                            "capabilityTags":["review"],
                            "supportedChannels":["control","events"],
                            "snapshotHash":"abc123",
                            "defaultModel":"gpt-5"
                          }
                        }
                        """.formatted(enrollmentToken, displayName, hostLabel))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        JsonNode registerPayload = objectMapper.readTree(responseBody);
        return new RegisteredGolem(
                registerPayload.get("golemId").asText(),
                "Bearer " + registerPayload.get("accessToken").asText());
    }

    private String createEnrollmentToken(String operatorToken, String note) throws Exception {
        String responseBody = webTestClient.post()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "note":"%s enrollment",
                          "expiresInMinutes":60
                        }
                        """.formatted(note))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        JsonNode payload = objectMapper.readTree(responseBody);
        return payload.get("token").asText();
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

    private record RegisteredGolem(String golemId, String accessToken) {
    }
}
