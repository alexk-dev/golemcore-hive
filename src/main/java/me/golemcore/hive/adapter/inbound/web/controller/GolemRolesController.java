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

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.golems.CreateGolemRoleRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemRoleResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.RoleBindingRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.UpdateGolemRoleRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.service.GolemRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
public class GolemRolesController {

    private final GolemRegistryService golemRegistryService;

    @GetMapping("/api/v1/golem-roles")
    public Mono<ResponseEntity<List<GolemRoleResponse>>> listRoles(Principal principal) {
        return Mono.fromCallable(() -> {
            requireOperatorActor(principal);
            List<GolemRoleResponse> roles = golemRegistryService.listRoles().stream().map(this::toRoleResponse)
                    .toList();
            return ResponseEntity.ok(roles);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/api/v1/golem-roles")
    public Mono<ResponseEntity<GolemRoleResponse>> createRole(
            Principal principal,
            @Valid @RequestBody CreateGolemRoleRequest request) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            GolemRole role = golemRegistryService.createRole(
                    request.slug(),
                    request.name(),
                    request.description(),
                    request.capabilityTags());
            return ResponseEntity.status(HttpStatus.CREATED).body(toRoleResponse(role));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/api/v1/golem-roles/{roleSlug}")
    public Mono<ResponseEntity<GolemRoleResponse>> updateRole(
            Principal principal,
            @PathVariable String roleSlug,
            @RequestBody UpdateGolemRoleRequest request) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            GolemRole role = golemRegistryService.updateRole(
                    roleSlug,
                    request != null ? request.name() : null,
                    request != null ? request.description() : null,
                    request != null ? request.capabilityTags() : null);
            return ResponseEntity.ok(toRoleResponse(role));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/api/v1/golems/{golemId}/roles:assign")
    public Mono<ResponseEntity<List<String>>> assignRoles(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody RoleBindingRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = requirePrivilegedOperator(principal);
            Golem golem = golemRegistryService.assignRoles(golemId, request.roleSlugs(), actor);
            return ResponseEntity
                    .ok(golem.getRoleBindings().stream().map(binding -> binding.getRoleSlug()).sorted().toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/api/v1/golems/{golemId}/roles:unassign")
    public Mono<ResponseEntity<List<String>>> unassignRoles(
            Principal principal,
            @PathVariable String golemId,
            @Valid @RequestBody RoleBindingRequest request) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            Golem golem = golemRegistryService.unassignRoles(golemId, request.roleSlugs());
            return ResponseEntity
                    .ok(golem.getRoleBindings().stream().map(binding -> binding.getRoleSlug()).sorted().toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private GolemRoleResponse toRoleResponse(GolemRole role) {
        return new GolemRoleResponse(
                role.getSlug(),
                role.getName(),
                role.getDescription(),
                role.getCapabilityTags(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    private AuthenticatedActor requireOperatorActor(Principal principal) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isOperator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator token required");
        }
        return actor;
    }

    private AuthenticatedActor requirePrivilegedOperator(Principal principal) {
        AuthenticatedActor actor = requireOperatorActor(principal);
        if (!actor.hasAnyRole(List.of("ADMIN", "OPERATOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or operator role required");
        }
        return actor;
    }

    private AuthenticatedActor extractActor(Principal principal) {
        if (principal instanceof AuthenticatedActor actor) {
            return actor;
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return actor;
        }
        return null;
    }
}
