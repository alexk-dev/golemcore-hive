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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.golemcore.hive.adapter.inbound.web.dto.golems.PolicyPackageResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupVersionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.UpdatePolicyGroupDraftRequest;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;

final class PolicyMappingSupport {

    private PolicyMappingSupport() {
    }

    static PolicyGroupResponse toPolicyGroupResponse(PolicyGroup policyGroup, int boundGolemCount) {
        return new PolicyGroupResponse(
                policyGroup.getId(),
                policyGroup.getSlug(),
                policyGroup.getName(),
                policyGroup.getDescription(),
                policyGroup.getStatus(),
                policyGroup.getCurrentVersion(),
                toPolicyGroupSpecResponse(policyGroup.getDraftSpec()),
                policyGroup.getCreatedAt(),
                policyGroup.getUpdatedAt(),
                policyGroup.getLastPublishedAt(),
                policyGroup.getLastPublishedBy(),
                policyGroup.getLastPublishedByName(),
                boundGolemCount);
    }

    static PolicyGroupVersionResponse toPolicyGroupVersionResponse(PolicyGroupVersion version) {
        return new PolicyGroupVersionResponse(
                version.getVersion(),
                toPolicyGroupSpecResponse(version.getSpecSnapshot()),
                version.getChecksum(),
                version.getChangeSummary(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                version.getPublishedByName());
    }

    static PolicyPackageResponse toPolicyPackageResponse(
            String policyGroupId,
            int targetVersion,
            PolicyGroupSpec specSnapshot,
            String checksum) {
        return new PolicyPackageResponse(
                policyGroupId,
                targetVersion,
                checksum,
                toProviderPackageResponse(specSnapshot != null ? specSnapshot.getLlmProviders() : null),
                toModelRouterPackageResponse(specSnapshot != null ? specSnapshot.getModelRouter() : null),
                toModelCatalogPackageResponse(specSnapshot != null ? specSnapshot.getModelCatalog() : null),
                toToolsPackageResponse(specSnapshot != null ? specSnapshot.getTools() : null),
                toMemoryPackageResponse(specSnapshot != null ? specSnapshot.getMemory() : null),
                toMcpPackageResponse(specSnapshot != null ? specSnapshot.getMcp() : null),
                toAutonomyPackageResponse(specSnapshot != null ? specSnapshot.getAutonomy() : null));
    }

    static PolicyGroupSpec toPolicyGroupSpec(UpdatePolicyGroupDraftRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, PolicyGroupSpec.PolicyProviderConfig> providers = new LinkedHashMap<>();
        for (Map.Entry<String, UpdatePolicyGroupDraftRequest.PolicyProviderConfigRequest> entry : request.llmProviders()
                .entrySet()) {
            UpdatePolicyGroupDraftRequest.PolicyProviderConfigRequest provider = entry.getValue();
            providers.put(entry.getKey(), provider != null ? PolicyGroupSpec.PolicyProviderConfig.builder()
                    .apiKey(provider.apiKey())
                    .baseUrl(provider.baseUrl())
                    .requestTimeoutSeconds(provider.requestTimeoutSeconds())
                    .apiType(provider.apiType())
                    .legacyApi(provider.legacyApi())
                    .build() : null);
        }
        return PolicyGroupSpec.builder()
                .schemaVersion(request.schemaVersion() != null ? request.schemaVersion() : 1)
                .llmProviders(providers)
                .modelRouter(toPolicyModelRouter(request.modelRouter()))
                .modelCatalog(toPolicyModelCatalog(request.modelCatalog()))
                .tools(toPolicyTools(request.tools()))
                .memory(toPolicyMemory(request.memory()))
                .mcp(toPolicyMcp(request.mcp()))
                .autonomy(toPolicyAutonomy(request.autonomy()))
                .build();
    }

    private static PolicyGroupResponse.PolicyGroupSpecResponse toPolicyGroupSpecResponse(PolicyGroupSpec spec) {
        if (spec == null) {
            return null;
        }
        Map<String, PolicyGroupResponse.PolicyProviderConfigResponse> providers = new LinkedHashMap<>();
        if (spec.getLlmProviders() != null) {
            for (Map.Entry<String, PolicyGroupSpec.PolicyProviderConfig> entry : spec.getLlmProviders().entrySet()) {
                PolicyGroupSpec.PolicyProviderConfig provider = entry.getValue();
                providers.put(entry.getKey(), new PolicyGroupResponse.PolicyProviderConfigResponse(
                        provider != null && provider.getApiKey() != null && !provider.getApiKey().isBlank(),
                        provider != null ? provider.getBaseUrl() : null,
                        provider != null ? provider.getRequestTimeoutSeconds() : null,
                        provider != null ? provider.getApiType() : null,
                        provider != null ? provider.getLegacyApi() : null));
            }
        }

        Map<String, PolicyGroupResponse.PolicyTierBindingResponse> tiers = new LinkedHashMap<>();
        if (spec.getModelRouter() != null && spec.getModelRouter().getTiers() != null) {
            for (Map.Entry<String, PolicyGroupSpec.PolicyTierBinding> entry : spec.getModelRouter().getTiers()
                    .entrySet()) {
                tiers.put(entry.getKey(), toTierBindingResponse(entry.getValue()));
            }
        }

        Map<String, PolicyGroupResponse.PolicyModelConfigResponse> models = new LinkedHashMap<>();
        if (spec.getModelCatalog() != null && spec.getModelCatalog().getModels() != null) {
            for (Map.Entry<String, PolicyGroupSpec.PolicyModelConfig> entry : spec.getModelCatalog().getModels()
                    .entrySet()) {
                PolicyGroupSpec.PolicyModelConfig model = entry.getValue();
                models.put(entry.getKey(), new PolicyGroupResponse.PolicyModelConfigResponse(
                        model != null ? model.getProvider() : null,
                        model != null ? model.getDisplayName() : null,
                        model != null ? model.getSupportsVision() : null,
                        model != null ? model.getSupportsTemperature() : null,
                        model != null ? model.getMaxInputTokens() : null));
            }
        }

        return new PolicyGroupResponse.PolicyGroupSpecResponse(
                spec.getSchemaVersion(),
                providers,
                spec.getModelRouter() != null ? new PolicyGroupResponse.PolicyModelRouterResponse(
                        spec.getModelRouter().getTemperature(),
                        toTierBindingResponse(spec.getModelRouter().getRouting()),
                        tiers,
                        spec.getModelRouter().getDynamicTierEnabled()) : null,
                spec.getModelCatalog() != null ? new PolicyGroupResponse.PolicyModelCatalogResponse(
                        spec.getModelCatalog().getDefaultModel(),
                        models) : null,
                toToolsResponse(spec.getTools()),
                toMemoryResponse(spec.getMemory()),
                toMcpResponse(spec.getMcp()),
                toAutonomyResponse(spec.getAutonomy()),
                spec.getChecksum());
    }

    private static Map<String, PolicyPackageResponse.PolicyProviderConfigResponse> toProviderPackageResponse(
            Map<String, PolicyGroupSpec.PolicyProviderConfig> providers) {
        Map<String, PolicyPackageResponse.PolicyProviderConfigResponse> response = new LinkedHashMap<>();
        if (providers == null) {
            return response;
        }
        for (Map.Entry<String, PolicyGroupSpec.PolicyProviderConfig> entry : providers.entrySet()) {
            PolicyGroupSpec.PolicyProviderConfig provider = entry.getValue();
            response.put(entry.getKey(), provider != null ? new PolicyPackageResponse.PolicyProviderConfigResponse(
                    provider.getApiKey(),
                    provider.getBaseUrl(),
                    provider.getRequestTimeoutSeconds(),
                    provider.getApiType(),
                    provider.getLegacyApi()) : null);
        }
        return response;
    }

    private static PolicyPackageResponse.PolicyModelRouterResponse toModelRouterPackageResponse(
            PolicyGroupSpec.PolicyModelRouter modelRouter) {
        if (modelRouter == null) {
            return null;
        }
        Map<String, PolicyPackageResponse.PolicyTierBindingResponse> tiers = new LinkedHashMap<>();
        if (modelRouter.getTiers() != null) {
            for (Map.Entry<String, PolicyGroupSpec.PolicyTierBinding> entry : modelRouter.getTiers().entrySet()) {
                tiers.put(entry.getKey(), toPackageTierBindingResponse(entry.getValue()));
            }
        }
        return new PolicyPackageResponse.PolicyModelRouterResponse(
                modelRouter.getTemperature(),
                toPackageTierBindingResponse(modelRouter.getRouting()),
                tiers,
                modelRouter.getDynamicTierEnabled());
    }

    private static PolicyPackageResponse.PolicyModelCatalogResponse toModelCatalogPackageResponse(
            PolicyGroupSpec.PolicyModelCatalog modelCatalog) {
        if (modelCatalog == null) {
            return null;
        }
        Map<String, PolicyPackageResponse.PolicyModelConfigResponse> models = new LinkedHashMap<>();
        if (modelCatalog.getModels() != null) {
            for (Map.Entry<String, PolicyGroupSpec.PolicyModelConfig> entry : modelCatalog.getModels().entrySet()) {
                PolicyGroupSpec.PolicyModelConfig model = entry.getValue();
                models.put(entry.getKey(), model != null ? new PolicyPackageResponse.PolicyModelConfigResponse(
                        model.getProvider(),
                        model.getDisplayName(),
                        model.getSupportsVision(),
                        model.getSupportsTemperature(),
                        model.getMaxInputTokens()) : null);
            }
        }
        return new PolicyPackageResponse.PolicyModelCatalogResponse(modelCatalog.getDefaultModel(), models);
    }

    private static PolicyGroupResponse.PolicyToolsConfigResponse toToolsResponse(
            PolicyGroupSpec.PolicyToolsConfig tools) {
        if (tools == null) {
            return null;
        }
        List<PolicyGroupResponse.PolicyEnvironmentVariableResponse> variables = new ArrayList<>();
        if (tools.getShellEnvironmentVariables() != null) {
            for (PolicyGroupSpec.PolicyEnvironmentVariable variable : tools.getShellEnvironmentVariables()) {
                variables.add(new PolicyGroupResponse.PolicyEnvironmentVariableResponse(
                        variable != null ? variable.getName() : null,
                        variable != null && variable.getValue() != null && !variable.getValue().isBlank()));
            }
        }
        return new PolicyGroupResponse.PolicyToolsConfigResponse(
                tools.getFilesystemEnabled(),
                tools.getShellEnabled(),
                tools.getSkillManagementEnabled(),
                tools.getSkillTransitionEnabled(),
                tools.getTierEnabled(),
                tools.getGoalManagementEnabled(),
                variables);
    }

    private static PolicyPackageResponse.PolicyToolsConfigResponse toToolsPackageResponse(
            PolicyGroupSpec.PolicyToolsConfig tools) {
        if (tools == null) {
            return null;
        }
        List<PolicyPackageResponse.PolicyEnvironmentVariableResponse> variables = new ArrayList<>();
        if (tools.getShellEnvironmentVariables() != null) {
            for (PolicyGroupSpec.PolicyEnvironmentVariable variable : tools.getShellEnvironmentVariables()) {
                variables.add(variable != null ? new PolicyPackageResponse.PolicyEnvironmentVariableResponse(
                        variable.getName(),
                        variable.getValue()) : null);
            }
        }
        return new PolicyPackageResponse.PolicyToolsConfigResponse(
                tools.getFilesystemEnabled(),
                tools.getShellEnabled(),
                tools.getSkillManagementEnabled(),
                tools.getSkillTransitionEnabled(),
                tools.getTierEnabled(),
                tools.getGoalManagementEnabled(),
                variables);
    }

    private static PolicyGroupResponse.PolicyMemoryConfigResponse toMemoryResponse(
            PolicyGroupSpec.PolicyMemoryConfig memory) {
        if (memory == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyMemoryConfigResponse(
                memory.getVersion(),
                memory.getEnabled(),
                memory.getSoftPromptBudgetTokens(),
                memory.getMaxPromptBudgetTokens(),
                memory.getWorkingTopK(),
                memory.getEpisodicTopK(),
                memory.getSemanticTopK(),
                memory.getProceduralTopK(),
                memory.getPromotionEnabled(),
                memory.getPromotionMinConfidence(),
                memory.getDecayEnabled(),
                memory.getDecayDays(),
                memory.getRetrievalLookbackDays(),
                memory.getCodeAwareExtractionEnabled(),
                toMemoryDisclosureResponse(memory.getDisclosure()),
                toMemoryRerankingResponse(memory.getReranking()),
                toMemoryDiagnosticsResponse(memory.getDiagnostics()));
    }

    private static PolicyPackageResponse.PolicyMemoryConfigResponse toMemoryPackageResponse(
            PolicyGroupSpec.PolicyMemoryConfig memory) {
        if (memory == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyMemoryConfigResponse(
                memory.getVersion(),
                memory.getEnabled(),
                memory.getSoftPromptBudgetTokens(),
                memory.getMaxPromptBudgetTokens(),
                memory.getWorkingTopK(),
                memory.getEpisodicTopK(),
                memory.getSemanticTopK(),
                memory.getProceduralTopK(),
                memory.getPromotionEnabled(),
                memory.getPromotionMinConfidence(),
                memory.getDecayEnabled(),
                memory.getDecayDays(),
                memory.getRetrievalLookbackDays(),
                memory.getCodeAwareExtractionEnabled(),
                toMemoryDisclosurePackageResponse(memory.getDisclosure()),
                toMemoryRerankingPackageResponse(memory.getReranking()),
                toMemoryDiagnosticsPackageResponse(memory.getDiagnostics()));
    }

    private static PolicyGroupResponse.PolicyMcpConfigResponse toMcpResponse(
            PolicyGroupSpec.PolicyMcpConfig mcp) {
        if (mcp == null) {
            return null;
        }
        List<PolicyGroupResponse.PolicyMcpCatalogEntryResponse> catalog = new ArrayList<>();
        if (mcp.getCatalog() != null) {
            for (PolicyGroupSpec.PolicyMcpCatalogEntry entry : mcp.getCatalog()) {
                catalog.add(toMcpCatalogEntryResponse(entry));
            }
        }
        return new PolicyGroupResponse.PolicyMcpConfigResponse(
                mcp.getEnabled(),
                mcp.getDefaultStartupTimeout(),
                mcp.getDefaultIdleTimeout(),
                catalog);
    }

    private static PolicyPackageResponse.PolicyMcpConfigResponse toMcpPackageResponse(
            PolicyGroupSpec.PolicyMcpConfig mcp) {
        if (mcp == null) {
            return null;
        }
        List<PolicyPackageResponse.PolicyMcpCatalogEntryResponse> catalog = new ArrayList<>();
        if (mcp.getCatalog() != null) {
            for (PolicyGroupSpec.PolicyMcpCatalogEntry entry : mcp.getCatalog()) {
                catalog.add(toMcpCatalogEntryPackageResponse(entry));
            }
        }
        return new PolicyPackageResponse.PolicyMcpConfigResponse(
                mcp.getEnabled(),
                mcp.getDefaultStartupTimeout(),
                mcp.getDefaultIdleTimeout(),
                catalog);
    }

    private static PolicyGroupResponse.PolicyAutonomyConfigResponse toAutonomyResponse(
            PolicyGroupSpec.PolicyAutonomyConfig autonomy) {
        if (autonomy == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyAutonomyConfigResponse(
                autonomy.getEnabled(),
                autonomy.getTickIntervalSeconds(),
                autonomy.getTaskTimeLimitMinutes(),
                autonomy.getAutoStart(),
                autonomy.getMaxGoals(),
                autonomy.getModelTier(),
                autonomy.getReflectionEnabled(),
                autonomy.getReflectionFailureThreshold(),
                autonomy.getReflectionModelTier(),
                autonomy.getReflectionTierPriority(),
                autonomy.getNotifyMilestones());
    }

    private static PolicyPackageResponse.PolicyAutonomyConfigResponse toAutonomyPackageResponse(
            PolicyGroupSpec.PolicyAutonomyConfig autonomy) {
        if (autonomy == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyAutonomyConfigResponse(
                autonomy.getEnabled(),
                autonomy.getTickIntervalSeconds(),
                autonomy.getTaskTimeLimitMinutes(),
                autonomy.getAutoStart(),
                autonomy.getMaxGoals(),
                autonomy.getModelTier(),
                autonomy.getReflectionEnabled(),
                autonomy.getReflectionFailureThreshold(),
                autonomy.getReflectionModelTier(),
                autonomy.getReflectionTierPriority(),
                autonomy.getNotifyMilestones());
    }

    private static PolicyGroupSpec.PolicyModelRouter toPolicyModelRouter(
            UpdatePolicyGroupDraftRequest.PolicyModelRouterRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, PolicyGroupSpec.PolicyTierBinding> tiers = new LinkedHashMap<>();
        for (Map.Entry<String, UpdatePolicyGroupDraftRequest.PolicyTierBindingRequest> entry : request.tiers()
                .entrySet()) {
            tiers.put(entry.getKey(), toPolicyTierBinding(entry.getValue()));
        }
        return PolicyGroupSpec.PolicyModelRouter.builder()
                .temperature(request.temperature())
                .routing(toPolicyTierBinding(request.routing()))
                .tiers(tiers)
                .dynamicTierEnabled(request.dynamicTierEnabled())
                .build();
    }

    private static PolicyGroupSpec.PolicyModelCatalog toPolicyModelCatalog(
            UpdatePolicyGroupDraftRequest.PolicyModelCatalogRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, PolicyGroupSpec.PolicyModelConfig> models = new LinkedHashMap<>();
        for (Map.Entry<String, UpdatePolicyGroupDraftRequest.PolicyModelConfigRequest> entry : request.models()
                .entrySet()) {
            UpdatePolicyGroupDraftRequest.PolicyModelConfigRequest model = entry.getValue();
            models.put(entry.getKey(), model != null ? PolicyGroupSpec.PolicyModelConfig.builder()
                    .provider(model.provider())
                    .displayName(model.displayName())
                    .supportsVision(model.supportsVision())
                    .supportsTemperature(model.supportsTemperature())
                    .maxInputTokens(model.maxInputTokens())
                    .build() : null);
        }
        return PolicyGroupSpec.PolicyModelCatalog.builder()
                .defaultModel(request.defaultModel())
                .models(models)
                .build();
    }

    private static PolicyGroupSpec.PolicyToolsConfig toPolicyTools(
            UpdatePolicyGroupDraftRequest.PolicyToolsConfigRequest request) {
        if (request == null) {
            return null;
        }
        List<PolicyGroupSpec.PolicyEnvironmentVariable> variables = new ArrayList<>();
        for (UpdatePolicyGroupDraftRequest.PolicyEnvironmentVariableRequest variable : request
                .shellEnvironmentVariables()) {
            variables.add(variable != null ? PolicyGroupSpec.PolicyEnvironmentVariable.builder()
                    .name(variable.name())
                    .value(variable.value())
                    .build() : null);
        }
        return PolicyGroupSpec.PolicyToolsConfig.builder()
                .filesystemEnabled(request.filesystemEnabled())
                .shellEnabled(request.shellEnabled())
                .skillManagementEnabled(request.skillManagementEnabled())
                .skillTransitionEnabled(request.skillTransitionEnabled())
                .tierEnabled(request.tierEnabled())
                .goalManagementEnabled(request.goalManagementEnabled())
                .shellEnvironmentVariables(variables)
                .build();
    }

    private static PolicyGroupSpec.PolicyMemoryConfig toPolicyMemory(
            UpdatePolicyGroupDraftRequest.PolicyMemoryConfigRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyMemoryConfig.builder()
                .version(request.version())
                .enabled(request.enabled())
                .softPromptBudgetTokens(request.softPromptBudgetTokens())
                .maxPromptBudgetTokens(request.maxPromptBudgetTokens())
                .workingTopK(request.workingTopK())
                .episodicTopK(request.episodicTopK())
                .semanticTopK(request.semanticTopK())
                .proceduralTopK(request.proceduralTopK())
                .promotionEnabled(request.promotionEnabled())
                .promotionMinConfidence(request.promotionMinConfidence())
                .decayEnabled(request.decayEnabled())
                .decayDays(request.decayDays())
                .retrievalLookbackDays(request.retrievalLookbackDays())
                .codeAwareExtractionEnabled(request.codeAwareExtractionEnabled())
                .disclosure(toPolicyMemoryDisclosure(request.disclosure()))
                .reranking(toPolicyMemoryReranking(request.reranking()))
                .diagnostics(toPolicyMemoryDiagnostics(request.diagnostics()))
                .build();
    }

    private static PolicyGroupSpec.PolicyMcpConfig toPolicyMcp(
            UpdatePolicyGroupDraftRequest.PolicyMcpConfigRequest request) {
        if (request == null) {
            return null;
        }
        List<PolicyGroupSpec.PolicyMcpCatalogEntry> catalog = new ArrayList<>();
        for (UpdatePolicyGroupDraftRequest.PolicyMcpCatalogEntryRequest entry : request.catalog()) {
            catalog.add(entry != null ? PolicyGroupSpec.PolicyMcpCatalogEntry.builder()
                    .name(entry.name())
                    .description(entry.description())
                    .command(entry.command())
                    .env(entry.env() != null ? entry.env() : null)
                    .startupTimeoutSeconds(entry.startupTimeoutSeconds())
                    .idleTimeoutMinutes(entry.idleTimeoutMinutes())
                    .enabled(entry.enabled())
                    .build() : null);
        }
        return PolicyGroupSpec.PolicyMcpConfig.builder()
                .enabled(request.enabled())
                .defaultStartupTimeout(request.defaultStartupTimeout())
                .defaultIdleTimeout(request.defaultIdleTimeout())
                .catalog(catalog)
                .build();
    }

    private static PolicyGroupSpec.PolicyAutonomyConfig toPolicyAutonomy(
            UpdatePolicyGroupDraftRequest.PolicyAutonomyConfigRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyAutonomyConfig.builder()
                .enabled(request.enabled())
                .tickIntervalSeconds(request.tickIntervalSeconds())
                .taskTimeLimitMinutes(request.taskTimeLimitMinutes())
                .autoStart(request.autoStart())
                .maxGoals(request.maxGoals())
                .modelTier(request.modelTier())
                .reflectionEnabled(request.reflectionEnabled())
                .reflectionFailureThreshold(request.reflectionFailureThreshold())
                .reflectionModelTier(request.reflectionModelTier())
                .reflectionTierPriority(request.reflectionTierPriority())
                .notifyMilestones(request.notifyMilestones())
                .build();
    }

    private static PolicyGroupSpec.PolicyTierBinding toPolicyTierBinding(
            UpdatePolicyGroupDraftRequest.PolicyTierBindingRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyTierBinding.builder()
                .model(request.model())
                .reasoning(request.reasoning())
                .build();
    }

    private static PolicyGroupResponse.PolicyTierBindingResponse toTierBindingResponse(
            PolicyGroupSpec.PolicyTierBinding binding) {
        if (binding == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyTierBindingResponse(binding.getModel(), binding.getReasoning());
    }

    private static PolicyPackageResponse.PolicyTierBindingResponse toPackageTierBindingResponse(
            PolicyGroupSpec.PolicyTierBinding binding) {
        if (binding == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyTierBindingResponse(binding.getModel(), binding.getReasoning());
    }

    private static PolicyGroupResponse.PolicyMemoryDisclosureResponse toMemoryDisclosureResponse(
            PolicyGroupSpec.PolicyMemoryDisclosureConfig disclosure) {
        if (disclosure == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyMemoryDisclosureResponse(
                disclosure.getMode(),
                disclosure.getPromptStyle(),
                disclosure.getToolExpansionEnabled(),
                disclosure.getDisclosureHintsEnabled(),
                disclosure.getDetailMinScore());
    }

    private static PolicyPackageResponse.PolicyMemoryDisclosureResponse toMemoryDisclosurePackageResponse(
            PolicyGroupSpec.PolicyMemoryDisclosureConfig disclosure) {
        if (disclosure == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyMemoryDisclosureResponse(
                disclosure.getMode(),
                disclosure.getPromptStyle(),
                disclosure.getToolExpansionEnabled(),
                disclosure.getDisclosureHintsEnabled(),
                disclosure.getDetailMinScore());
    }

    private static PolicyGroupResponse.PolicyMemoryRerankingResponse toMemoryRerankingResponse(
            PolicyGroupSpec.PolicyMemoryRerankingConfig reranking) {
        if (reranking == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyMemoryRerankingResponse(reranking.getEnabled(), reranking.getProfile());
    }

    private static PolicyPackageResponse.PolicyMemoryRerankingResponse toMemoryRerankingPackageResponse(
            PolicyGroupSpec.PolicyMemoryRerankingConfig reranking) {
        if (reranking == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyMemoryRerankingResponse(reranking.getEnabled(), reranking.getProfile());
    }

    private static PolicyGroupResponse.PolicyMemoryDiagnosticsResponse toMemoryDiagnosticsResponse(
            PolicyGroupSpec.PolicyMemoryDiagnosticsConfig diagnostics) {
        if (diagnostics == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyMemoryDiagnosticsResponse(diagnostics.getVerbosity());
    }

    private static PolicyPackageResponse.PolicyMemoryDiagnosticsResponse toMemoryDiagnosticsPackageResponse(
            PolicyGroupSpec.PolicyMemoryDiagnosticsConfig diagnostics) {
        if (diagnostics == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyMemoryDiagnosticsResponse(diagnostics.getVerbosity());
    }

    private static PolicyGroupSpec.PolicyMemoryDisclosureConfig toPolicyMemoryDisclosure(
            UpdatePolicyGroupDraftRequest.PolicyMemoryDisclosureRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyMemoryDisclosureConfig.builder()
                .mode(request.mode())
                .promptStyle(request.promptStyle())
                .toolExpansionEnabled(request.toolExpansionEnabled())
                .disclosureHintsEnabled(request.disclosureHintsEnabled())
                .detailMinScore(request.detailMinScore())
                .build();
    }

    private static PolicyGroupSpec.PolicyMemoryRerankingConfig toPolicyMemoryReranking(
            UpdatePolicyGroupDraftRequest.PolicyMemoryRerankingRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyMemoryRerankingConfig.builder()
                .enabled(request.enabled())
                .profile(request.profile())
                .build();
    }

    private static PolicyGroupSpec.PolicyMemoryDiagnosticsConfig toPolicyMemoryDiagnostics(
            UpdatePolicyGroupDraftRequest.PolicyMemoryDiagnosticsRequest request) {
        if (request == null) {
            return null;
        }
        return PolicyGroupSpec.PolicyMemoryDiagnosticsConfig.builder()
                .verbosity(request.verbosity())
                .build();
    }

    private static PolicyGroupResponse.PolicyMcpCatalogEntryResponse toMcpCatalogEntryResponse(
            PolicyGroupSpec.PolicyMcpCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        Map<String, Boolean> envPresent = new LinkedHashMap<>();
        if (entry.getEnv() != null) {
            for (Map.Entry<String, String> envEntry : entry.getEnv().entrySet()) {
                envPresent.put(envEntry.getKey(), envEntry.getValue() != null && !envEntry.getValue().isBlank());
            }
        }
        return new PolicyGroupResponse.PolicyMcpCatalogEntryResponse(
                entry.getName(),
                entry.getDescription(),
                entry.getCommand(),
                envPresent,
                entry.getStartupTimeoutSeconds(),
                entry.getIdleTimeoutMinutes(),
                entry.getEnabled());
    }

    private static PolicyPackageResponse.PolicyMcpCatalogEntryResponse toMcpCatalogEntryPackageResponse(
            PolicyGroupSpec.PolicyMcpCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        return new PolicyPackageResponse.PolicyMcpCatalogEntryResponse(
                entry.getName(),
                entry.getDescription(),
                entry.getCommand(),
                entry.getEnv(),
                entry.getStartupTimeoutSeconds(),
                entry.getIdleTimeoutMinutes(),
                entry.getEnabled());
    }
}
