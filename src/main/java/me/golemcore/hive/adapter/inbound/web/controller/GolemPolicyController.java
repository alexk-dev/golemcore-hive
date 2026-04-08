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
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.golems.PolicyApplyResultRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.PolicyPackageResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemPolicyBindingResponse;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.adapter.inbound.web.dto.policies.UpdateGolemPolicyBindingRequest;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.domain.service.PolicyGroupService;
import me.golemcore.hive.domain.service.PolicyRolloutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/golems")
@RequiredArgsConstructor
public class GolemPolicyController {

    private final PolicyGroupService policyGroupService;
    private final PolicyRolloutService policyRolloutService;

    @GetMapping("/{golemId}/policy-package")
    public Mono<ResponseEntity<PolicyPackageResponse>> getPolicyPackage(Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireGolemScope(principal, golemId, GolemScope.POLICY_READ.value());
            GolemPolicyBinding binding = policyGroupService.getBinding(golemId);
            PolicyGroupVersion version = policyGroupService.getTargetVersionForGolem(golemId);
            return ResponseEntity.ok(new PolicyPackageResponse(
                    binding.getPolicyGroupId(),
                    binding.getTargetVersion(),
                    version.getChecksum(),
                    version.getSpecSnapshot().getLlmProviders(),
                    version.getSpecSnapshot().getModelRouter(),
                    version.getSpecSnapshot().getModelCatalog()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}/policy-apply-result")
    public Mono<ResponseEntity<GolemPolicyBindingResponse>> reportPolicyApplyResult(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody PolicyApplyResultRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireGolemScope(principal, golemId, GolemScope.POLICY_WRITE.value());
            PolicySyncStatus syncStatus = parseSyncStatus(request.syncStatus());
            GolemPolicyBinding binding = policyGroupService.recordApplyResult(
                    golemId,
                    request.policyGroupId(),
                    request.targetVersion(),
                    request.appliedVersion(),
                    syncStatus,
                    request.checksum(),
                    request.errorDigest());
            return ResponseEntity.ok(toBindingResponse(binding));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{golemId}/policy-binding")
    public Mono<ResponseEntity<GolemPolicyBindingResponse>> bindPolicyGroup(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody UpdateGolemPolicyBindingRequest request) {
        return Mono.fromCallable(() -> {
            me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor = ControllerActorSupport
                    .requirePrivilegedOperator(principal);
            GolemPolicyBinding binding = policyGroupService.bindGolem(
                    golemId,
                    request.policyGroupId(),
                    actor.getSubjectId(),
                    actor.getName());
            policyRolloutService.requestSyncIfSupported(binding);
            return ResponseEntity.ok(toBindingResponse(binding));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{golemId}/policy-binding")
    public Mono<ResponseEntity<Void>> unbindPolicyGroup(Principal principal, @PathVariable String golemId) {
        return Mono.<ResponseEntity<Void>>fromCallable(() -> {
            me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor = ControllerActorSupport
                    .requirePrivilegedOperator(principal);
            policyGroupService.unbindGolem(golemId, actor.getSubjectId(), actor.getName());
            return ResponseEntity.<Void>noContent().build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    static GolemPolicyBindingResponse toBindingResponse(GolemPolicyBinding binding) {
        return new GolemPolicyBindingResponse(
                binding.getPolicyGroupId(),
                binding.getTargetVersion(),
                binding.getAppliedVersion(),
                binding.getSyncStatus() != null ? binding.getSyncStatus().name() : null,
                binding.getLastSyncRequestedAt(),
                binding.getLastAppliedAt(),
                binding.getLastErrorDigest(),
                binding.getLastErrorAt(),
                binding.getDriftSince());
    }

    private PolicySyncStatus parseSyncStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return PolicySyncStatus.valueOf(rawStatus.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unknown policy sync status: " + rawStatus);
        }
    }
}
