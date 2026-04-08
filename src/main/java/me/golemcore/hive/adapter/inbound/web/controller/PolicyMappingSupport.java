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

import java.util.LinkedHashMap;
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
                toModelCatalogPackageResponse(specSnapshot != null ? specSnapshot.getModelCatalog() : null));
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
}
