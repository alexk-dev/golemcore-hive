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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import me.golemcore.hive.infrastructure.control.InMemoryGolemControlChannelGateway;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.fleet.adapter.out.support.GolemRegistryService;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyGroupsControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GolemRegistryService golemRegistryService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    void shouldCreatePublishBindRepublishAndRollbackPolicyGroup() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerGolem(operatorToken, "Policy Runner", false);
        String golemId = golem.golemId();

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
                          },
                          "tools":{
                            "filesystemEnabled":true,
                            "shellEnabled":true,
                            "skillManagementEnabled":true,
                            "skillTransitionEnabled":true,
                            "tierEnabled":true,
                            "goalManagementEnabled":true,
                            "shellEnvironmentVariables":[
                              {"name":"GITHUB_TOKEN","value":"secret-shell-token"}
                            ]
                          },
                          "memory":{
                            "version":2,
                            "enabled":true,
                            "softPromptBudgetTokens":1800,
                            "maxPromptBudgetTokens":6000,
                            "workingTopK":6,
                            "episodicTopK":8,
                            "semanticTopK":8,
                            "proceduralTopK":4,
                            "promotionEnabled":true,
                            "promotionMinConfidence":0.75,
                            "decayEnabled":true,
                            "decayDays":90,
                            "retrievalLookbackDays":30,
                            "codeAwareExtractionEnabled":true,
                            "disclosure":{
                              "mode":"summary",
                              "promptStyle":"balanced",
                              "toolExpansionEnabled":true,
                              "disclosureHintsEnabled":true,
                              "detailMinScore":0.8
                            },
                            "reranking":{"enabled":true,"profile":"balanced"},
                            "diagnostics":{"verbosity":"basic"}
                          },
                          "mcp":{
                            "enabled":true,
                            "defaultStartupTimeout":30,
                            "defaultIdleTimeout":5,
                            "catalog":[
                              {
                                "name":"github",
                                "description":"GitHub tools",
                                "command":"npx -y @modelcontextprotocol/server-github",
                                "env":{"GITHUB_TOKEN":"secret-mcp-token"},
                                "startupTimeoutSeconds":45,
                                "idleTimeoutMinutes":10,
                                "enabled":true
                              }
                            ]
                          },
                          "autonomy":{
                            "enabled":true,
                            "tickIntervalSeconds":1,
                            "taskTimeLimitMinutes":10,
                            "autoStart":true,
                            "maxGoals":3,
                            "modelTier":"balanced",
                            "reflectionEnabled":true,
                            "reflectionFailureThreshold":2,
                            "reflectionModelTier":"reasoning",
                            "reflectionTierPriority":true,
                            "notifyMilestones":true
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
                          },
                          "tools":{
                            "filesystemEnabled":true,
                            "shellEnabled":true,
                            "skillManagementEnabled":true,
                            "skillTransitionEnabled":true,
                            "tierEnabled":true,
                            "goalManagementEnabled":true,
                            "shellEnvironmentVariables":[
                              {"name":"GITHUB_TOKEN","value":"secret-shell-token"}
                            ]
                          },
                          "memory":{
                            "version":2,
                            "enabled":true,
                            "softPromptBudgetTokens":1800,
                            "maxPromptBudgetTokens":6000,
                            "workingTopK":6,
                            "episodicTopK":8,
                            "semanticTopK":8,
                            "proceduralTopK":4,
                            "promotionEnabled":true,
                            "promotionMinConfidence":0.75,
                            "decayEnabled":true,
                            "decayDays":90,
                            "retrievalLookbackDays":30,
                            "codeAwareExtractionEnabled":true,
                            "disclosure":{
                              "mode":"summary",
                              "promptStyle":"balanced",
                              "toolExpansionEnabled":true,
                              "disclosureHintsEnabled":true,
                              "detailMinScore":0.8
                            },
                            "reranking":{"enabled":true,"profile":"balanced"},
                            "diagnostics":{"verbosity":"basic"}
                          },
                          "mcp":{
                            "enabled":true,
                            "defaultStartupTimeout":30,
                            "defaultIdleTimeout":5,
                            "catalog":[
                              {
                                "name":"github",
                                "description":"GitHub tools",
                                "command":"npx -y @modelcontextprotocol/server-github",
                                "env":{"GITHUB_TOKEN":"secret-mcp-token"},
                                "startupTimeoutSeconds":45,
                                "idleTimeoutMinutes":10,
                                "enabled":true
                              }
                            ]
                          },
                          "autonomy":{
                            "enabled":true,
                            "tickIntervalSeconds":1,
                            "taskTimeLimitMinutes":10,
                            "autoStart":true,
                            "maxGoals":3,
                            "modelTier":"balanced",
                            "reflectionEnabled":true,
                            "reflectionFailureThreshold":2,
                            "reflectionModelTier":"reasoning",
                            "reflectionTierPriority":true,
                            "notifyMilestones":true
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.draftSpec.tools.shellEnvironmentVariables[0].valuePresent").isEqualTo(true)
                .jsonPath("$.draftSpec.mcp.catalog[0].envPresent.GITHUB_TOKEN").isEqualTo(true)
                .jsonPath("$.draftSpec.memory.disclosure.mode").isEqualTo("summary")
                .jsonPath("$.draftSpec.autonomy.enabled").isEqualTo(true);

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

    @Test
    void shouldExposePolicyPackageAndAcceptApplyResultFromBoundGolem() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerGolem(operatorToken, "Policy Sync Runner", false);

        EntityExchangeResult<String> createGroupResult = webTestClient.post()
                .uri("/api/v1/policy-groups")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "slug":"machine-routing",
                          "name":"Machine Routing",
                          "description":"Machine policy"
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
                .bodyValue(runtimePolicyDraftJson())
                .exchange()
                .expectStatus().isOk();

        EntityExchangeResult<String> publishResult = webTestClient.post()
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
                .expectBody(String.class)
                .returnResult();

        String checksum = objectMapper.readTree(publishResult.getResponseBody()).get("checksum").asText();

        webTestClient.put()
                .uri("/api/v1/golems/{golemId}/policy-binding", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "policyGroupId":"%s"
                        }
                        """.formatted(policyGroupId))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}/policy-package", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, golem.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyGroupId").isEqualTo(policyGroupId)
                .jsonPath("$.targetVersion").isEqualTo(1)
                .jsonPath("$.checksum").isEqualTo(checksum)
                .jsonPath("$.llmProviders.openai.apiKey").isEqualTo("secret-openai")
                .jsonPath("$.tools.shellEnvironmentVariables[0].value").isEqualTo("secret-shell-token")
                .jsonPath("$.memory.disclosure.mode").isEqualTo("summary")
                .jsonPath("$.mcp.catalog[0].env.GITHUB_TOKEN").isEqualTo("secret-mcp-token")
                .jsonPath("$.autonomy.enabled").isEqualTo(true);

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/policy-apply-result", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, golem.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "policyGroupId":"%s",
                          "targetVersion":1,
                          "appliedVersion":1,
                          "syncStatus":"IN_SYNC",
                          "checksum":"%s"
                        }
                        """.formatted(policyGroupId, checksum))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyGroupId").isEqualTo(policyGroupId)
                .jsonPath("$.appliedVersion").isEqualTo(1)
                .jsonPath("$.syncStatus").isEqualTo("IN_SYNC");

        webTestClient.get()
                .uri("/api/v1/golems/{golemId}", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyBinding.policyGroupId").isEqualTo(policyGroupId)
                .jsonPath("$.policyBinding.targetVersion").isEqualTo(1)
                .jsonPath("$.policyBinding.appliedVersion").isEqualTo(1)
                .jsonPath("$.policyBinding.syncStatus").isEqualTo("IN_SYNC");
    }

    @Test
    void shouldProjectHeartbeatPolicyStateAndGateSyncEventDelivery() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem unsupportedGolem = registerGolem(operatorToken, "Legacy Runner", false);
        RegisteredGolem supportedGolem = registerGolem(operatorToken, "Managed Runner", true);

        InMemoryGolemControlChannelGateway controlChannelService = applicationContext.getBean(
                InMemoryGolemControlChannelGateway.class);
        BlockingQueue<String> unsupportedMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> supportedMessages = new LinkedBlockingQueue<>();
        Sinks.Many<String> unsupportedSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<String> supportedSink = Sinks.many().unicast().onBackpressureBuffer();
        Disposable unsupportedSubscription = controlChannelService.register(unsupportedGolem.golemId(), unsupportedSink)
                .subscribe(unsupportedMessages::add);
        Disposable supportedSubscription = controlChannelService.register(supportedGolem.golemId(), supportedSink)
                .subscribe(supportedMessages::add);

        try {
            EntityExchangeResult<String> createGroupResult = webTestClient.post()
                    .uri("/api/v1/policy-groups")
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "slug":"sync-routing",
                              "name":"Sync Routing",
                              "description":"Control-triggered policy"
                            }
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(String.class)
                    .returnResult();

            String policyGroupId = objectMapper.readTree(createGroupResult.getResponseBody()).get("id").asText();

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
                    .expectStatus().isOk();

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
                    .expectStatus().isCreated();

            webTestClient.put()
                    .uri("/api/v1/golems/{golemId}/policy-binding", unsupportedGolem.golemId())
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "policyGroupId":"%s"
                            }
                            """.formatted(policyGroupId))
                    .exchange()
                    .expectStatus().isOk();

            webTestClient.put()
                    .uri("/api/v1/golems/{golemId}/policy-binding", supportedGolem.golemId())
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "policyGroupId":"%s"
                            }
                            """.formatted(policyGroupId))
                    .exchange()
                    .expectStatus().isOk();

            Assertions.assertNull(unsupportedMessages.poll(500, TimeUnit.MILLISECONDS));
            JsonNode syncEnvelope = objectMapper.readTree(pollControlMessage(supportedMessages));
            Assertions.assertEquals("policy.sync_requested", syncEnvelope.get("eventType").asText());
            Assertions.assertEquals(policyGroupId, syncEnvelope.get("policyGroupId").asText());
            Assertions.assertEquals(1, syncEnvelope.get("targetVersion").asInt());
            Assertions.assertTrue(syncEnvelope.get("checksum").asText().length() > 10);

            webTestClient.post()
                    .uri("/api/v1/golems/{golemId}/heartbeat", supportedGolem.golemId())
                    .header(HttpHeaders.AUTHORIZATION, supportedGolem.accessToken())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue("""
                            {
                              "status":"healthy",
                              "currentRunState":"IDLE",
                              "modelTier":"pro",
                              "queueDepth":0,
                              "healthSummary":"ready",
                              "uptimeSeconds":120,
                              "policyGroupId":"%s",
                              "targetPolicyVersion":1,
                              "appliedPolicyVersion":1,
                              "syncStatus":"IN_SYNC"
                            }
                            """.formatted(policyGroupId))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.lastHeartbeat.targetPolicyVersion").isEqualTo(1)
                    .jsonPath("$.lastHeartbeat.appliedPolicyVersion").isEqualTo(1)
                    .jsonPath("$.lastHeartbeat.syncStatus").isEqualTo("IN_SYNC")
                    .jsonPath("$.policyBinding.appliedVersion").isEqualTo(1)
                    .jsonPath("$.policyBinding.syncStatus").isEqualTo("IN_SYNC");

            webTestClient.get()
                    .uri("/api/v1/golems/{golemId}", supportedGolem.golemId())
                    .header(HttpHeaders.AUTHORIZATION, operatorToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.lastHeartbeat.targetPolicyVersion").isEqualTo(1)
                    .jsonPath("$.lastHeartbeat.appliedPolicyVersion").isEqualTo(1)
                    .jsonPath("$.lastHeartbeat.syncStatus").isEqualTo("IN_SYNC")
                    .jsonPath("$.policyBinding.appliedVersion").isEqualTo(1)
                    .jsonPath("$.policyBinding.syncStatus").isEqualTo("IN_SYNC");
        } finally {
            controlChannelService.unregister(unsupportedGolem.golemId(), unsupportedSink);
            controlChannelService.unregister(supportedGolem.golemId(), supportedSink);
            unsupportedSubscription.dispose();
            supportedSubscription.dispose();
        }
    }

    @Test
    void shouldIgnoreStaleHeartbeatPolicyStateAfterUnbindOrRebind() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerGolem(operatorToken, "Stale Heartbeat Runner", false);
        String firstPolicyGroupId = createAndPublishPolicyGroup(operatorToken, "stale-routing-a", "Stale Routing A");
        String secondPolicyGroupId = createAndPublishPolicyGroup(operatorToken, "stale-routing-b", "Stale Routing B");

        bindPolicyGroup(operatorToken, golem.golemId(), firstPolicyGroupId);

        webTestClient.delete()
                .uri("/api/v1/golems/{golemId}/policy-binding", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/heartbeat", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, golem.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "status":"healthy",
                          "currentRunState":"IDLE",
                          "modelTier":"pro",
                          "queueDepth":0,
                          "healthSummary":"ready",
                          "uptimeSeconds":120,
                          "policyGroupId":"%s",
                          "targetPolicyVersion":1,
                          "appliedPolicyVersion":1,
                          "syncStatus":"IN_SYNC"
                        }
                        """.formatted(firstPolicyGroupId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyBinding").isEmpty();

        bindPolicyGroup(operatorToken, golem.golemId(), secondPolicyGroupId);

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/heartbeat", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, golem.accessToken())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "status":"healthy",
                          "currentRunState":"IDLE",
                          "modelTier":"pro",
                          "queueDepth":0,
                          "healthSummary":"ready",
                          "uptimeSeconds":120,
                          "policyGroupId":"%s",
                          "targetPolicyVersion":1,
                          "appliedPolicyVersion":1,
                          "syncStatus":"IN_SYNC"
                        }
                        """.formatted(firstPolicyGroupId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyBinding.policyGroupId").isEqualTo(secondPolicyGroupId)
                .jsonPath("$.policyBinding.syncStatus").isEqualTo("SYNC_PENDING");
    }

    @Test
    void shouldRequirePolicyWriteScopeWhenHeartbeatCarriesPolicySyncState() throws Exception {
        String operatorToken = loginAsAdmin();
        RegisteredGolem golem = registerGolem(operatorToken, "Restricted Heartbeat Runner", false);
        String policyGroupId = createAndPublishPolicyGroup(operatorToken, "restricted-routing", "Restricted Routing");

        bindPolicyGroup(operatorToken, golem.golemId(), policyGroupId);

        Golem storedGolem = golemRegistryService.findGolem(golem.golemId()).orElseThrow();
        String heartbeatOnlyToken = "Bearer " + jwtTokenProvider.generateGolemAccessToken(
                storedGolem,
                List.of("golems:heartbeat"));

        webTestClient.post()
                .uri("/api/v1/golems/{golemId}/heartbeat", golem.golemId())
                .header(HttpHeaders.AUTHORIZATION, heartbeatOnlyToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "status":"healthy",
                          "currentRunState":"IDLE",
                          "modelTier":"pro",
                          "queueDepth":0,
                          "healthSummary":"ready",
                          "uptimeSeconds":120,
                          "policyGroupId":"%s",
                          "targetPolicyVersion":1,
                          "appliedPolicyVersion":1,
                          "syncStatus":"IN_SYNC"
                        }
                        """.formatted(policyGroupId))
                .exchange()
                .expectStatus().isForbidden();
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

    private RegisteredGolem registerGolem(String operatorToken, String displayName, boolean supportsPolicySync)
            throws Exception {
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
                          "displayName":"%s",
                          "hostLabel":"host-a",
                          "runtimeVersion":"bot-1.2.3",
                          "buildVersion":"build-42",
                          "supportedChannels":["control","events"],
                          "capabilities":{
                            "providers":["openai"],
                            "modelFamilies":["gpt"],
                            "enabledTools":["shell"],
                            "enabledAutonomyFeatures":[%s],
                            "capabilityTags":["policy"],
                            "supportedChannels":["control","events"],
                            "snapshotHash":"abc123",
                            "defaultModel":"gpt-5"
                          }
                        }
                        """.formatted(
                        enrollmentToken,
                        displayName,
                        supportsPolicySync ? "\"planning\",\"policy-sync-v1\"" : "\"planning\""))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode registerPayload = objectMapper.readTree(registerResult.getResponseBody());
        return new RegisteredGolem(
                registerPayload.get("golemId").asText(),
                "Bearer " + registerPayload.get("accessToken").asText());
    }

    private String createAndPublishPolicyGroup(String operatorToken, String slug, String name) throws Exception {
        EntityExchangeResult<String> createGroupResult = webTestClient.post()
                .uri("/api/v1/policy-groups")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "slug":"%s",
                          "name":"%s",
                          "description":"Policy test group"
                        }
                        """.formatted(slug, name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        String policyGroupId = objectMapper.readTree(createGroupResult.getResponseBody()).get("id").asText();

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
                .expectStatus().isOk();

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
                .expectStatus().isCreated();

        return policyGroupId;
    }

    private String runtimePolicyDraftJson() {
        return """
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
                  },
                  "tools":{
                    "filesystemEnabled":true,
                    "shellEnabled":true,
                    "skillManagementEnabled":true,
                    "skillTransitionEnabled":true,
                    "tierEnabled":true,
                    "goalManagementEnabled":true,
                    "shellEnvironmentVariables":[
                      {"name":"GITHUB_TOKEN","value":"secret-shell-token"}
                    ]
                  },
                  "memory":{
                    "version":2,
                    "enabled":true,
                    "softPromptBudgetTokens":1800,
                    "maxPromptBudgetTokens":6000,
                    "workingTopK":6,
                    "episodicTopK":8,
                    "semanticTopK":8,
                    "proceduralTopK":4,
                    "promotionEnabled":true,
                    "promotionMinConfidence":0.75,
                    "decayEnabled":true,
                    "decayDays":90,
                    "retrievalLookbackDays":30,
                    "codeAwareExtractionEnabled":true,
                    "disclosure":{
                      "mode":"summary",
                      "promptStyle":"balanced",
                      "toolExpansionEnabled":true,
                      "disclosureHintsEnabled":true,
                      "detailMinScore":0.8
                    },
                    "reranking":{"enabled":true,"profile":"balanced"},
                    "diagnostics":{"verbosity":"basic"}
                  },
                  "mcp":{
                    "enabled":true,
                    "defaultStartupTimeout":30,
                    "defaultIdleTimeout":5,
                    "catalog":[
                      {
                        "name":"github",
                        "description":"GitHub tools",
                        "command":"npx -y @modelcontextprotocol/server-github",
                        "env":{"GITHUB_TOKEN":"secret-mcp-token"},
                        "startupTimeoutSeconds":45,
                        "idleTimeoutMinutes":10,
                        "enabled":true
                      }
                    ]
                  },
                  "autonomy":{
                    "enabled":true,
                    "tickIntervalSeconds":1,
                    "taskTimeLimitMinutes":10,
                    "autoStart":true,
                    "maxGoals":3,
                    "modelTier":"balanced",
                    "reflectionEnabled":true,
                    "reflectionFailureThreshold":2,
                    "reflectionModelTier":"reasoning",
                    "reflectionTierPriority":true,
                    "notifyMilestones":true
                  }
                }
                """;
    }

    private void bindPolicyGroup(String operatorToken, String golemId, String policyGroupId) {
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
                .expectStatus().isOk();
    }

    private record RegisteredGolem(String golemId, String accessToken) {
    }

    private String pollControlMessage(BlockingQueue<String> controlMessages) throws InterruptedException {
        String payload = controlMessages.poll(2, TimeUnit.SECONDS);
        Assertions.assertNotNull(payload, "Expected a control-channel message");
        return payload;
    }
}
