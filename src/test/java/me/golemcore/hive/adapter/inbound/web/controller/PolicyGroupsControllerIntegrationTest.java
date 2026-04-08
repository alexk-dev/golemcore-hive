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
class PolicyGroupsControllerIntegrationTest {

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
    void shouldCreatePublishBindRepublishAndRollbackPolicyGroup() throws Exception {
        String operatorToken = loginAsAdmin();
        String golemId = registerGolem(operatorToken);

        EntityExchangeResult<String> createGroupResult = webTestClient.post()
                .uri("/api/v1/policy-groups")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "slug":"default-routing",
                          "name":"Default Routing",
                          "description":"Primary policy"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode createdGroupPayload = objectMapper.readTree(createGroupResult.getResponseBody());
        String policyGroupId = createdGroupPayload.get("id").asText();

        webTestClient.put()
                .uri("/api/v1/policy-groups/{groupId}/draft", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion":1,
                          "llmProviders":{
                            "openai":{
                              "apiKey":"secret-openai",
                              "baseUrl":"https://api.example.com/openai",
                              "requestTimeoutSeconds":30,
                              "apiType":"openai"
                            }
                          },
                          "modelRouter":{
                            "temperature":0.7,
                            "dynamicTierEnabled":true,
                            "routing":{"model":"openai/gpt-5.1","reasoning":"low"},
                            "tiers":{"balanced":{"model":"openai/gpt-5.1","reasoning":"low"}}
                          },
                          "modelCatalog":{
                            "defaultModel":"openai/gpt-5.1",
                            "models":{
                              "openai/gpt-5.1":{
                                "provider":"openai",
                                "displayName":"openai/gpt-5.1",
                                "supportsVision":true,
                                "supportsTemperature":true,
                                "maxInputTokens":200000
                              }
                            }
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.draftSpec.llmProviders.openai.apiType").isEqualTo("openai");

        webTestClient.post()
                .uri("/api/v1/policy-groups/{groupId}/publish", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "changeSummary":"Initial publish"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.version").isEqualTo(1)
                .jsonPath("$.checksum").exists();

        webTestClient.put()
                .uri("/api/v1/golems/{golemId}/policy-binding", golemId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "policyGroupId":"%s"
                        }
                        """.formatted(policyGroupId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyGroupId").isEqualTo(policyGroupId)
                .jsonPath("$.targetVersion").isEqualTo(1)
                .jsonPath("$.syncStatus").isEqualTo("SYNC_PENDING");

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}", golemId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyBinding.policyGroupId").isEqualTo(policyGroupId)
                .jsonPath("$.policyBinding.targetVersion").isEqualTo(1);

        webTestClient.put()
                .uri("/api/v1/policy-groups/{groupId}/draft", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "schemaVersion":1,
                          "llmProviders":{
                            "anthropic":{
                              "apiKey":"secret-anthropic",
                              "baseUrl":"https://api.example.com/anthropic",
                              "requestTimeoutSeconds":30,
                              "apiType":"anthropic"
                            }
                          },
                          "modelRouter":{
                            "temperature":0.2,
                            "dynamicTierEnabled":true,
                            "routing":{"model":"anthropic/claude-opus-4-1","reasoning":"medium"},
                            "tiers":{"balanced":{"model":"anthropic/claude-opus-4-1","reasoning":"medium"}}
                          },
                          "modelCatalog":{
                            "defaultModel":"anthropic/claude-opus-4-1",
                            "models":{
                              "anthropic/claude-opus-4-1":{
                                "provider":"anthropic",
                                "displayName":"anthropic/claude-opus-4-1",
                                "supportsVision":true,
                                "supportsTemperature":true,
                                "maxInputTokens":200000
                              }
                            }
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/v1/policy-groups/{groupId}/publish", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "changeSummary":"Switch provider"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.version").isEqualTo(2);

        webTestClient.post()
                .uri("/api/v1/policy-groups/{groupId}/rollback", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "version":1,
                          "changeSummary":"Rollback to v1"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currentVersion").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/policy-groups/{groupId}/versions", policyGroupId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].version").isEqualTo(2)
                .jsonPath("$[1].version").isEqualTo(1);

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}", golemId)
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyBinding.targetVersion").isEqualTo(1)
                .jsonPath("$.policyBinding.syncStatus").isEqualTo("SYNC_PENDING");
    }

    private String loginAsAdmin() throws Exception {
        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {"username":"admin","password":"change-me-now"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        JsonNode loginPayload = objectMapper.readTree(loginResult.getResponseBody());
        return "Bearer " + loginPayload.get("accessToken").asText();
    }

    private String registerGolem(String operatorToken) throws Exception {
        EntityExchangeResult<String> enrollmentTokenResult = webTestClient.post()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {"note":"policy-test","expiresInMinutes":60}
                        """)
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
                          "displayName":"Policy Runner",
                          "hostLabel":"host-a",
                          "runtimeVersion":"bot-1.2.3",
                          "buildVersion":"build-42",
                          "supportedChannels":["control","events"],
                          "capabilities":{
                            "providers":["openai"],
                            "modelFamilies":["gpt"],
                            "enabledTools":["shell"],
                            "enabledAutonomyFeatures":["planning"],
                            "capabilityTags":["policy"],
                            "supportedChannels":["control","events"],
                            "snapshotHash":"abc123",
                            "defaultModel":"gpt-5"
                          }
                        }
                        """.formatted(enrollmentToken))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode registerPayload = objectMapper.readTree(registerResult.getResponseBody());
        return registerPayload.get("golemId").asText();
    }
}
