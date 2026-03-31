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
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfEvolvingControllerArtifactWorkspaceIntegrationTest {

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
    void shouldServeMirroredArtifactWorkspaceAndFleetCompare() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem leftGolem = registerGolem("Atlas Artifact", "host-artifact-left");
        RegisteredGolem rightGolem = registerGolem("Atlas Compare", "host-artifact-right");

        ingestArtifactBatch(leftGolem, "rev-2", "hash-2");
        ingestArtifactBatch(rightGolem, "rev-3", "hash-3");

        webTestClient.get()
                .uri("/api/v1/self-evolving/golems/{golemId}/artifacts", leftGolem.golemId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].artifactStreamId").isEqualTo("stream-1");

        webTestClient.get()
                .uri("/api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}", leftGolem.golemId(),
                        "stream-1")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.artifactStreamId").isEqualTo("stream-1")
                .jsonPath("$.activeRevisionId").isEqualTo("rev-1");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/self-evolving/artifacts/compare")
                        .queryParam("artifactStreamId", "stream-1")
                        .queryParam("leftGolemId", leftGolem.golemId())
                        .queryParam("rightGolemId", rightGolem.golemId())
                        .queryParam("leftRevisionId", "rev-2")
                        .queryParam("rightRevisionId", "rev-3")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.artifactStreamId").isEqualTo("stream-1")
                .jsonPath("$.sameContent").isEqualTo(false);

        String searchBody = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/self-evolving/artifacts/search")
                        .queryParam("artifactType", "skill")
                        .queryParam("q", "planner")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        JsonNode searchPayload = objectMapper.readTree(searchBody);
        Assertions.assertEquals(2, searchPayload.size());
        Set<String> golemIds = new HashSet<>();
        for (JsonNode node : searchPayload) {
            Assertions.assertEquals("stream-1", node.get("artifactStreamId").asText());
            golemIds.add(node.get("golemId").asText());
        }
        Assertions.assertTrue(golemIds.contains(leftGolem.golemId()));
        Assertions.assertTrue(golemIds.contains(rightGolem.golemId()));
    }

    private void ingestArtifactBatch(RegisteredGolem golem, String revisionId, String normalizedHash) {
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
                              "eventType": "selfevolving.artifact.upserted",
                              "golemId": "%s",
                              "payload": {
                                "artifactStreamId": "stream-1",
                                "originArtifactStreamId": "stream-1",
                                "artifactKey": "skill:planner",
                                "artifactAliases": ["skill:planner"],
                                "artifactType": "skill",
                                "artifactSubtype": "skill",
                                "activeRevisionId": "rev-1",
                                "latestCandidateRevisionId": "%s",
                                "projectionSchemaVersion": 1,
                                "sourceBotVersion": "dev",
                                "projectedAt": "2026-03-31T20:00:00Z"
                              },
                              "createdAt": "2026-03-31T20:00:01Z"
                            },
                            {
                              "eventType": "selfevolving.artifact.normalized-revision.upserted",
                              "golemId": "%s",
                              "payload": {
                                "artifactStreamId": "stream-1",
                                "contentRevisionId": "%s",
                                "normalizationSchemaVersion": 1,
                                "normalizedContent": "planner",
                                "normalizedHash": "%s",
                                "semanticSections": ["planner"],
                                "sourceBotVersion": "dev",
                                "projectedAt": "2026-03-31T20:01:00Z"
                              },
                              "createdAt": "2026-03-31T20:01:01Z"
                            },
                            {
                              "eventType": "selfevolving.artifact.lineage.upserted",
                              "golemId": "%s",
                              "payload": {
                                "artifactStreamId": "stream-1",
                                "originArtifactStreamId": "stream-1",
                                "artifactKey": "skill:planner",
                                "nodes": [
                                  {
                                    "nodeId": "candidate-1:proposed",
                                    "contentRevisionId": "%s",
                                    "rolloutStage": "proposed"
                                  }
                                ],
                                "edges": [],
                                "railOrder": ["candidate-1:proposed"],
                                "branches": [],
                                "defaultSelectedNodeId": "candidate-1:proposed",
                                "defaultSelectedRevisionId": "%s",
                                "projectionSchemaVersion": 1,
                                "sourceBotVersion": "dev",
                                "projectedAt": "2026-03-31T20:02:00Z"
                              },
                              "createdAt": "2026-03-31T20:02:01Z"
                            }
                          ]
                        }
                        """.formatted(
                        golem.golemId(),
                        golem.golemId(),
                        revisionId,
                        golem.golemId(),
                        revisionId,
                        normalizedHash,
                        golem.golemId(),
                        revisionId,
                        revisionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.acceptedEvents").isEqualTo(3);
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
