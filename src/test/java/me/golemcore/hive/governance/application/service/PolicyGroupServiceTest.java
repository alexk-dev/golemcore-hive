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

package me.golemcore.hive.governance.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import me.golemcore.hive.adapter.outbound.storage.LocalJsonStorageAdapter;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonGolemRepository;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonGolemRoleRepository;
import me.golemcore.hive.fleet.adapter.out.persistence.JsonHeartbeatRepository;
import me.golemcore.hive.fleet.application.FleetSettings;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import me.golemcore.hive.fleet.application.service.GolemFleetApplicationService;
import me.golemcore.hive.governance.adapter.out.persistence.JsonAuditEventRepository;
import me.golemcore.hive.governance.adapter.out.persistence.JacksonPolicySpecCodecAdapter;
import me.golemcore.hive.governance.adapter.out.persistence.JsonPolicyBindingRepository;
import me.golemcore.hive.governance.adapter.out.persistence.JsonPolicyGroupRepository;
import me.golemcore.hive.governance.adapter.out.persistence.JsonPolicyGroupVersionRepository;
import me.golemcore.hive.governance.adapter.out.support.FleetPolicyGolemAdapter;
import me.golemcore.hive.governance.adapter.out.support.GovernanceFleetAuditAdapter;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.service.AuditLogApplicationService;
import me.golemcore.hive.governance.application.service.PolicyGroupAdministrationApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyGroupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPublishBindRepublishAndRollbackPolicyGroup() {
        HiveProperties properties = new HiveProperties();
        properties.getStorage().setBasePath(tempDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        LocalJsonStorageAdapter storagePort = new LocalJsonStorageAdapter(properties);
        storagePort.init();

        AuditLogUseCase auditLogUseCase = new AuditLogApplicationService(
                new JsonAuditEventRepository(storagePort, objectMapper));
        GolemFleetApplicationService golemFleetApplicationService = createGolemFleetApplicationService(
                storagePort,
                objectMapper,
                properties,
                auditLogUseCase);
        PolicyGroupAdministrationApplicationService service = createPolicyGroupAdministrationService(
                storagePort,
                objectMapper,
                auditLogUseCase,
                golemFleetApplicationService);

        PolicyGroup group = service.createPolicyGroup("default-routing", "Default Routing", "Primary policy", "op-1",
                "Hive Admin");
        group = service.updateDraft(group.getId(), buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1"));

        PolicyGroupVersion version1 = service.publish(group.getId(), "Initial publish", "op-1", "Hive Admin");

        assertEquals(1, version1.getVersion());
        assertEquals(1, service.getPolicyGroup(group.getId()).getCurrentVersion());
        assertNotNull(version1.getChecksum());

        Golem golem = golemFleetApplicationService.registerGolem(
                "Builder",
                "lab-a",
                "1.0.0",
                "build-1",
                Set.of("control"),
                GolemCapabilitySnapshot.builder()
                        .providers(new LinkedHashSet<>(Set.of("openai")))
                        .build(),
                "token-1");

        GolemPolicyBinding binding = service.bindGolem(golem.getId(), group.getId(), "op-1", "Hive Admin");

        assertEquals(group.getId(), binding.getPolicyGroupId());
        assertEquals(1, binding.getTargetVersion());
        assertNull(binding.getAppliedVersion());
        assertEquals(PolicySyncStatus.SYNC_PENDING, binding.getSyncStatus());

        group = service.updateDraft(group.getId(), buildSpec("anthropic", "anthropic/claude-opus-4-1",
                "anthropic/claude-opus-4-1"));

        PolicyGroupVersion version2 = service.publish(group.getId(), "Switch provider", "op-1", "Hive Admin");
        GolemPolicyBinding republishedBinding = service.getBinding(golem.getId());

        assertEquals(2, version2.getVersion());
        assertEquals(2, republishedBinding.getTargetVersion());
        assertEquals(PolicySyncStatus.SYNC_PENDING, republishedBinding.getSyncStatus());
        assertNotNull(republishedBinding.getDriftSince());

        PolicyGroup rolledBack = service.rollback(group.getId(), 1, "Rollback", "op-1", "Hive Admin");
        GolemPolicyBinding rolledBackBinding = service.getBinding(golem.getId());

        assertEquals(1, rolledBack.getCurrentVersion());
        assertEquals(1, rolledBackBinding.getTargetVersion());
        assertEquals(PolicySyncStatus.SYNC_PENDING, rolledBackBinding.getSyncStatus());
    }

    @Test
    void shouldPreserveSecretsWhenDraftUpdateOmitsRuntimeSecretValues() {
        HiveProperties properties = new HiveProperties();
        properties.getStorage().setBasePath(tempDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        LocalJsonStorageAdapter storagePort = new LocalJsonStorageAdapter(properties);
        storagePort.init();

        AuditLogUseCase auditLogUseCase = new AuditLogApplicationService(
                new JsonAuditEventRepository(storagePort, objectMapper));
        GolemFleetApplicationService golemFleetApplicationService = createGolemFleetApplicationService(
                storagePort,
                objectMapper,
                properties,
                auditLogUseCase);
        PolicyGroupAdministrationApplicationService service = createPolicyGroupAdministrationService(
                storagePort,
                objectMapper,
                auditLogUseCase,
                golemFleetApplicationService);

        PolicyGroup group = service.createPolicyGroup("default-routing", "Default Routing", "Primary policy", "op-1",
                "Hive Admin");
        group = service.updateDraft(group.getId(), buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1"));

        PolicyGroupSpec updatedWithoutSecret = buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1");
        updatedWithoutSecret.getLlmProviders().get("openai").setApiKey(null);
        updatedWithoutSecret.getLlmProviders().get("openai").setRequestTimeoutSeconds(45);
        updatedWithoutSecret.getTools().getShellEnvironmentVariables().get(0).setValue(null);
        updatedWithoutSecret.getMcp().getCatalog().get(0).getEnv().put("GITHUB_TOKEN", "");

        PolicyGroup updatedGroup = service.updateDraft(group.getId(), updatedWithoutSecret);

        assertEquals("secret-openai", updatedGroup.getDraftSpec().getLlmProviders().get("openai").getApiKey());
        assertEquals(45, updatedGroup.getDraftSpec().getLlmProviders().get("openai").getRequestTimeoutSeconds());
        assertEquals("secret-shell-token",
                updatedGroup.getDraftSpec().getTools().getShellEnvironmentVariables().get(0).getValue());
        assertEquals("secret-mcp-token",
                updatedGroup.getDraftSpec().getMcp().getCatalog().get(0).getEnv().get("GITHUB_TOKEN"));

        PolicyGroupSpec updatedWithoutMcpEnv = buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1");
        updatedWithoutMcpEnv.getMcp().getCatalog().get(0).setEnv(null);

        PolicyGroup updatedAgain = service.updateDraft(group.getId(), updatedWithoutMcpEnv);

        assertEquals("secret-mcp-token",
                updatedAgain.getDraftSpec().getMcp().getCatalog().get(0).getEnv().get("GITHUB_TOKEN"));
    }

    @Test
    void shouldKeepLastAppliedAtWhenNewTargetFailsToApply() throws Exception {
        HiveProperties properties = new HiveProperties();
        properties.getStorage().setBasePath(tempDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        LocalJsonStorageAdapter storagePort = new LocalJsonStorageAdapter(properties);
        storagePort.init();

        AuditLogUseCase auditLogUseCase = new AuditLogApplicationService(
                new JsonAuditEventRepository(storagePort, objectMapper));
        GolemFleetApplicationService golemFleetApplicationService = createGolemFleetApplicationService(
                storagePort,
                objectMapper,
                properties,
                auditLogUseCase);
        PolicyGroupAdministrationApplicationService service = createPolicyGroupAdministrationService(
                storagePort,
                objectMapper,
                auditLogUseCase,
                golemFleetApplicationService);

        PolicyGroup group = service.createPolicyGroup("default-routing", "Default Routing", "Primary policy", "op-1",
                "Hive Admin");
        group = service.updateDraft(group.getId(), buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1"));

        PolicyGroupVersion version1 = service.publish(group.getId(), "Initial publish", "op-1", "Hive Admin");

        Golem golem = golemFleetApplicationService.registerGolem(
                "Builder",
                "lab-a",
                "1.0.0",
                "build-1",
                Set.of("control"),
                GolemCapabilitySnapshot.builder()
                        .providers(new LinkedHashSet<>(Set.of("openai")))
                        .build(),
                "token-1");

        service.bindGolem(golem.getId(), group.getId(), "op-1", "Hive Admin");
        GolemPolicyBinding syncedBinding = service.recordApplyResult(
                golem.getId(),
                group.getId(),
                version1.getVersion(),
                version1.getVersion(),
                PolicySyncStatus.IN_SYNC,
                version1.getChecksum(),
                null);

        Instant successfulApplyAt = syncedBinding.getLastAppliedAt();
        assertNotNull(successfulApplyAt);

        Thread.sleep(10L);

        group = service.updateDraft(group.getId(), buildSpec("anthropic", "anthropic/claude-opus-4-1",
                "anthropic/claude-opus-4-1"));
        PolicyGroupVersion version2 = service.publish(group.getId(), "Switch provider", "op-1", "Hive Admin");

        GolemPolicyBinding failedBinding = service.recordApplyResult(
                golem.getId(),
                group.getId(),
                version2.getVersion(),
                version1.getVersion(),
                PolicySyncStatus.APPLY_FAILED,
                version2.getChecksum(),
                "provider timeout");

        assertEquals(PolicySyncStatus.APPLY_FAILED, failedBinding.getSyncStatus());
        assertEquals(version1.getVersion(), failedBinding.getAppliedVersion());
        assertEquals(successfulApplyAt, failedBinding.getLastAppliedAt());
        assertEquals("provider timeout", failedBinding.getLastErrorDigest());
    }

    private PolicyGroupSpec buildSpec(String providerName, String defaultModel, String routingModel) {
        Map<String, PolicyGroupSpec.PolicyProviderConfig> providers = new LinkedHashMap<>();
        providers.put(providerName, PolicyGroupSpec.PolicyProviderConfig.builder()
                .apiType(providerName)
                .apiKey("secret-" + providerName)
                .baseUrl("https://api.example.com/" + providerName)
                .requestTimeoutSeconds(30)
                .build());

        Map<String, PolicyGroupSpec.PolicyTierBinding> tiers = new LinkedHashMap<>();
        tiers.put("balanced", PolicyGroupSpec.PolicyTierBinding.builder()
                .model(routingModel)
                .reasoning("low")
                .build());

        Map<String, PolicyGroupSpec.PolicyModelConfig> models = new LinkedHashMap<>();
        models.put(defaultModel, PolicyGroupSpec.PolicyModelConfig.builder()
                .provider(providerName)
                .displayName(defaultModel)
                .supportsVision(true)
                .supportsTemperature(true)
                .maxInputTokens(200_000)
                .build());

        Map<String, String> mcpEnv = new LinkedHashMap<>();
        mcpEnv.put("GITHUB_TOKEN", "secret-mcp-token");

        return PolicyGroupSpec.builder()
                .schemaVersion(1)
                .llmProviders(providers)
                .modelRouter(PolicyGroupSpec.PolicyModelRouter.builder()
                        .temperature(0.7d)
                        .dynamicTierEnabled(true)
                        .routing(PolicyGroupSpec.PolicyTierBinding.builder()
                                .model(routingModel)
                                .reasoning("low")
                                .build())
                        .tiers(tiers)
                        .build())
                .modelCatalog(PolicyGroupSpec.PolicyModelCatalog.builder()
                        .defaultModel(defaultModel)
                        .models(models)
                        .build())
                .tools(PolicyGroupSpec.PolicyToolsConfig.builder()
                        .filesystemEnabled(true)
                        .shellEnabled(true)
                        .skillManagementEnabled(true)
                        .skillTransitionEnabled(true)
                        .tierEnabled(true)
                        .goalManagementEnabled(true)
                        .shellEnvironmentVariables(List.of(PolicyGroupSpec.PolicyEnvironmentVariable.builder()
                                .name("GITHUB_TOKEN")
                                .value("secret-shell-token")
                                .build()))
                        .build())
                .memory(PolicyGroupSpec.PolicyMemoryConfig.builder()
                        .version(2)
                        .enabled(true)
                        .softPromptBudgetTokens(1_800)
                        .maxPromptBudgetTokens(6_000)
                        .workingTopK(6)
                        .episodicTopK(8)
                        .semanticTopK(8)
                        .proceduralTopK(4)
                        .promotionEnabled(true)
                        .promotionMinConfidence(0.75d)
                        .decayEnabled(true)
                        .decayDays(90)
                        .retrievalLookbackDays(30)
                        .codeAwareExtractionEnabled(true)
                        .disclosure(PolicyGroupSpec.PolicyMemoryDisclosureConfig.builder()
                                .mode("summary")
                                .promptStyle("balanced")
                                .toolExpansionEnabled(true)
                                .disclosureHintsEnabled(true)
                                .detailMinScore(0.8d)
                                .build())
                        .reranking(PolicyGroupSpec.PolicyMemoryRerankingConfig.builder()
                                .enabled(true)
                                .profile("balanced")
                                .build())
                        .diagnostics(PolicyGroupSpec.PolicyMemoryDiagnosticsConfig.builder()
                                .verbosity("basic")
                                .build())
                        .build())
                .mcp(PolicyGroupSpec.PolicyMcpConfig.builder()
                        .enabled(true)
                        .defaultStartupTimeout(30)
                        .defaultIdleTimeout(5)
                        .catalog(List.of(PolicyGroupSpec.PolicyMcpCatalogEntry.builder()
                                .name("github")
                                .description("GitHub tools")
                                .command("npx -y @modelcontextprotocol/server-github")
                                .env(mcpEnv)
                                .startupTimeoutSeconds(45)
                                .idleTimeoutMinutes(10)
                                .enabled(true)
                                .build()))
                        .build())
                .autonomy(PolicyGroupSpec.PolicyAutonomyConfig.builder()
                        .enabled(true)
                        .tickIntervalSeconds(1)
                        .taskTimeLimitMinutes(10)
                        .autoStart(true)
                        .maxGoals(3)
                        .modelTier("balanced")
                        .reflectionEnabled(true)
                        .reflectionFailureThreshold(2)
                        .reflectionModelTier("reasoning")
                        .reflectionTierPriority(true)
                        .notifyMilestones(true)
                        .build())
                .build();
    }

    private PolicyGroupAdministrationApplicationService createPolicyGroupAdministrationService(
            LocalJsonStorageAdapter storagePort,
            ObjectMapper objectMapper,
            AuditLogUseCase auditLogUseCase,
            GolemFleetApplicationService golemFleetApplicationService) {
        return new PolicyGroupAdministrationApplicationService(
                new JsonPolicyGroupRepository(storagePort, objectMapper),
                new JsonPolicyGroupVersionRepository(storagePort, objectMapper),
                new JsonPolicyBindingRepository(storagePort, objectMapper),
                new FleetPolicyGolemAdapter(golemFleetApplicationService, golemFleetApplicationService),
                new JacksonPolicySpecCodecAdapter(objectMapper),
                auditLogUseCase);
    }

    private GolemFleetApplicationService createGolemFleetApplicationService(
            LocalJsonStorageAdapter storagePort,
            ObjectMapper objectMapper,
            HiveProperties properties,
            AuditLogUseCase auditLogUseCase) {
        FleetSettings fleetSettings = new FleetSettings(
                properties.getFleet().getControlChannelUrl(),
                properties.getFleet().getHeartbeatIntervalSeconds(),
                properties.getFleet().getDegradedAfterMisses(),
                properties.getFleet().getOfflineAfterMisses(),
                properties.getFleet().getEnrollmentTokenTtlMinutes(),
                properties.getSecurity().getJwt().getGolemAccessExpirationMinutes(),
                properties.getSecurity().getJwt().getGolemRefreshExpirationDays());
        FleetNotificationPort notificationPort = new FleetNotificationPort() {
            @Override
            public boolean isGolemOfflineEnabled() {
                return false;
            }

            @Override
            public void create(me.golemcore.hive.domain.model.NotificationEvent notificationEvent) {
            }
        };
        GolemFleetApplicationService golemFleetApplicationService = new GolemFleetApplicationService(
                new JsonGolemRepository(storagePort, objectMapper),
                new JsonHeartbeatRepository(storagePort, objectMapper),
                new JsonGolemRoleRepository(storagePort, objectMapper),
                new GovernanceFleetAuditAdapter(auditLogUseCase),
                notificationPort,
                fleetSettings);
        return golemFleetApplicationService;
    }
}
