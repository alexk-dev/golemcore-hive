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

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.policies.CreatePolicyGroupRequest;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupVersionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PublishPolicyGroupRequest;
import me.golemcore.hive.adapter.inbound.web.dto.policies.RollbackPolicyGroupRequest;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.service.PolicyGroupService;
import me.golemcore.hive.domain.service.PolicyRolloutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/policy-groups")
@RequiredArgsConstructor
public class PolicyGroupsController {

    private final PolicyGroupService policyGroupService;
    private final PolicyRolloutService policyRolloutService;

    @GetMapping
    public Mono<ResponseEntity<List<PolicyGroupResponse>>> listPolicyGroups(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<PolicyGroupResponse> response = policyGroupService.listPolicyGroups().stream()
                    .map(this::toPolicyGroupResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<PolicyGroupResponse>> createPolicyGroup(
            Principal principal,
            @RequestBody CreatePolicyGroupRequest request) {
        return Mono.fromCallable(() -> {
            me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor = ControllerActorSupport
                    .requirePrivilegedOperator(principal);
            PolicyGroup policyGroup = policyGroupService.createPolicyGroup(
                    request.slug(),
                    request.name(),
                    request.description(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toPolicyGroupResponse(policyGroup));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{groupId}")
    public Mono<ResponseEntity<PolicyGroupResponse>> getPolicyGroup(Principal principal, @PathVariable String groupId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toPolicyGroupResponse(policyGroupService.getPolicyGroup(groupId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{groupId}/draft")
    public Mono<ResponseEntity<PolicyGroupResponse>> updateDraft(
            Principal principal,
            @PathVariable String groupId,
            @RequestBody PolicyGroupSpec draftSpec) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            PolicyGroup policyGroup = policyGroupService.updateDraft(groupId, draftSpec);
            return ResponseEntity.ok(toPolicyGroupResponse(policyGroup));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{groupId}/publish")
    public Mono<ResponseEntity<PolicyGroupVersionResponse>> publish(
            Principal principal,
            @PathVariable String groupId,
            @RequestBody(required = false) PublishPolicyGroupRequest request) {
        return Mono.fromCallable(() -> {
            me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor = ControllerActorSupport
                    .requirePrivilegedOperator(principal);
            PolicyGroupVersion version = policyGroupService.publish(
                    groupId,
                    request != null ? request.changeSummary() : null,
                    actor.getSubjectId(),
                    actor.getName());
            triggerSyncForGroup(groupId);
            return ResponseEntity.status(HttpStatus.CREATED).body(toPolicyGroupVersionResponse(version));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{groupId}/versions")
    public Mono<ResponseEntity<List<PolicyGroupVersionResponse>>> listVersions(
            Principal principal,
            @PathVariable String groupId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<PolicyGroupVersionResponse> response = policyGroupService.listVersions(groupId).stream()
                    .map(this::toPolicyGroupVersionResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{groupId}/rollback")
    public Mono<ResponseEntity<PolicyGroupResponse>> rollback(
            Principal principal,
            @PathVariable String groupId,
            @RequestBody RollbackPolicyGroupRequest request) {
        return Mono.fromCallable(() -> {
            me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor = ControllerActorSupport
                    .requirePrivilegedOperator(principal);
            PolicyGroup policyGroup = policyGroupService.rollback(
                    groupId,
                    request.version() != null ? request.version() : 0,
                    request.changeSummary(),
                    actor.getSubjectId(),
                    actor.getName());
            triggerSyncForGroup(groupId);
            return ResponseEntity.ok(toPolicyGroupResponse(policyGroup));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private PolicyGroupResponse toPolicyGroupResponse(PolicyGroup policyGroup) {
        int boundGolemCount = policyGroupService.listBindingsForPolicyGroup(policyGroup.getId()).size();
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

    private PolicyGroupVersionResponse toPolicyGroupVersionResponse(PolicyGroupVersion version) {
        return new PolicyGroupVersionResponse(
                version.getVersion(),
                toPolicyGroupSpecResponse(version.getSpecSnapshot()),
                version.getChecksum(),
                version.getChangeSummary(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                version.getPublishedByName());
    }

    private PolicyGroupResponse.PolicyGroupSpecResponse toPolicyGroupSpecResponse(PolicyGroupSpec spec) {
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

    private PolicyGroupResponse.PolicyTierBindingResponse toTierBindingResponse(
            PolicyGroupSpec.PolicyTierBinding binding) {
        if (binding == null) {
            return null;
        }
        return new PolicyGroupResponse.PolicyTierBindingResponse(binding.getModel(), binding.getReasoning());
    }

    private void triggerSyncForGroup(String groupId) {
        for (me.golemcore.hive.domain.model.GolemPolicyBinding binding : policyGroupService
                .listBindingsForPolicyGroup(groupId)) {
            policyRolloutService.requestSyncIfSupported(binding);
        }
    }
}
