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

package me.golemcore.hive.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import me.golemcore.hive.adapter.outbound.storage.LocalJsonStorageAdapter;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
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

        AuditService auditService = new AuditService(storagePort, objectMapper);
        GolemPresenceService golemPresenceService = mock(GolemPresenceService.class);
        when(golemPresenceService.calculateMissedHeartbeats(any(Golem.class), any(Instant.class))).thenReturn(0);
        when(golemPresenceService.resolveState(any(Golem.class), any(Instant.class)))
                .thenReturn(GolemState.PENDING_ENROLLMENT);

        GolemRegistryService golemRegistryService = new GolemRegistryService(
                storagePort,
                objectMapper,
                properties,
                golemPresenceService,
                auditService);

        PolicyGroupService service = new PolicyGroupService(storagePort, objectMapper, auditService,
                golemRegistryService);

        PolicyGroup group = service.createPolicyGroup("default-routing", "Default Routing", "Primary policy", "op-1",
                "Hive Admin");
        group = service.updateDraft(group.getId(), buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1"));

        PolicyGroupVersion version1 = service.publish(group.getId(), "Initial publish", "op-1", "Hive Admin");

        assertEquals(1, version1.getVersion());
        assertEquals(1, service.getPolicyGroup(group.getId()).getCurrentVersion());
        assertNotNull(version1.getChecksum());

        Golem golem = golemRegistryService.registerGolem(
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
    void shouldPreserveProviderSecretsWhenDraftUpdateOmitsApiKey() {
        HiveProperties properties = new HiveProperties();
        properties.getStorage().setBasePath(tempDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        LocalJsonStorageAdapter storagePort = new LocalJsonStorageAdapter(properties);
        storagePort.init();

        AuditService auditService = new AuditService(storagePort, objectMapper);
        GolemPresenceService golemPresenceService = mock(GolemPresenceService.class);
        when(golemPresenceService.calculateMissedHeartbeats(any(Golem.class), any(Instant.class))).thenReturn(0);
        when(golemPresenceService.resolveState(any(Golem.class), any(Instant.class)))
                .thenReturn(GolemState.PENDING_ENROLLMENT);

        GolemRegistryService golemRegistryService = new GolemRegistryService(
                storagePort,
                objectMapper,
                properties,
                golemPresenceService,
                auditService);

        PolicyGroupService service = new PolicyGroupService(storagePort, objectMapper, auditService,
                golemRegistryService);

        PolicyGroup group = service.createPolicyGroup("default-routing", "Default Routing", "Primary policy", "op-1",
                "Hive Admin");
        group = service.updateDraft(group.getId(), buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1"));

        PolicyGroupSpec updatedWithoutSecret = buildSpec("openai", "openai/gpt-5.1", "openai/gpt-5.1");
        updatedWithoutSecret.getLlmProviders().get("openai").setApiKey(null);
        updatedWithoutSecret.getLlmProviders().get("openai").setRequestTimeoutSeconds(45);

        PolicyGroup updatedGroup = service.updateDraft(group.getId(), updatedWithoutSecret);

        assertEquals("secret-openai", updatedGroup.getDraftSpec().getLlmProviders().get("openai").getApiKey());
        assertEquals(45, updatedGroup.getDraftSpec().getLlmProviders().get("openai").getRequestTimeoutSeconds());
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
                .build();
    }
}
