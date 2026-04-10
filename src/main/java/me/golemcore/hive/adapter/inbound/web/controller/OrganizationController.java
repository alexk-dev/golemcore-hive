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
import me.golemcore.hive.adapter.inbound.web.dto.organization.OrganizationResponse;
import me.golemcore.hive.adapter.inbound.web.dto.organization.UpdateOrganizationRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Organization;
import me.golemcore.hive.domain.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public Mono<ResponseEntity<OrganizationResponse>> getOrganization(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toOrganizationResponse(organizationService.getOrganization()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping
    public Mono<ResponseEntity<OrganizationResponse>> updateOrganization(
            Principal principal,
            @RequestBody UpdateOrganizationRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Organization organization = organizationService.updateOrganization(
                    request != null ? request.name() : null,
                    request != null ? request.description() : null,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toOrganizationResponse(organization));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private OrganizationResponse toOrganizationResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getDescription(),
                organization.getCreatedAt(),
                organization.getUpdatedAt());
    }
}
