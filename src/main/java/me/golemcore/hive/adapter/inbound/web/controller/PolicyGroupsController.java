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
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.policies.CreatePolicyGroupRequest;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PolicyGroupVersionResponse;
import me.golemcore.hive.adapter.inbound.web.dto.policies.PublishPolicyGroupRequest;
import me.golemcore.hive.adapter.inbound.web.dto.policies.RollbackPolicyGroupRequest;
import me.golemcore.hive.adapter.inbound.web.dto.policies.UpdatePolicyGroupDraftRequest;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.service.PolicyLifecycleService;
import me.golemcore.hive.domain.service.PolicyGroupService;
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
    private final PolicyLifecycleService policyLifecycleService;

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
            @RequestBody UpdatePolicyGroupDraftRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            PolicyGroup policyGroup = policyGroupService.updateDraft(groupId,
                    PolicyMappingSupport.toPolicyGroupSpec(request));
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
            PolicyGroupVersion version = policyLifecycleService.publish(
                    groupId,
                    request != null ? request.changeSummary() : null,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(PolicyMappingSupport.toPolicyGroupVersionResponse(
                    version));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{groupId}/versions")
    public Mono<ResponseEntity<List<PolicyGroupVersionResponse>>> listVersions(
            Principal principal,
            @PathVariable String groupId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<PolicyGroupVersionResponse> response = policyGroupService.listVersions(groupId).stream()
                    .map(PolicyMappingSupport::toPolicyGroupVersionResponse)
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
            PolicyGroup policyGroup = policyLifecycleService.rollback(
                    groupId,
                    request.version() != null ? request.version() : 0,
                    request.changeSummary(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toPolicyGroupResponse(policyGroup));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private PolicyGroupResponse toPolicyGroupResponse(PolicyGroup policyGroup) {
        int boundGolemCount = policyGroupService.listBindingsForPolicyGroup(policyGroup.getId()).size();
        return PolicyMappingSupport.toPolicyGroupResponse(policyGroup, boundGolemCount);
    }
}
