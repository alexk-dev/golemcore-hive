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
import me.golemcore.hive.adapter.inbound.web.dto.organization.CreateObjectiveRequest;
import me.golemcore.hive.adapter.inbound.web.dto.organization.ObjectiveResponse;
import me.golemcore.hive.adapter.inbound.web.dto.organization.UpdateObjectiveRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.domain.model.ObjectiveStatus;
import me.golemcore.hive.workflow.application.port.in.ObjectiveWorkflowUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/objectives")
@RequiredArgsConstructor
public class ObjectivesController {

    private final ObjectiveWorkflowUseCase objectiveWorkflowUseCase;

    @GetMapping
    public Mono<ResponseEntity<List<ObjectiveResponse>>> listObjectives(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(
                    objectiveWorkflowUseCase.listObjectives().stream().map(this::toObjectiveResponse).toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{objectiveId}")
    public Mono<ResponseEntity<ObjectiveResponse>> getObjective(Principal principal, @PathVariable String objectiveId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toObjectiveResponse(objectiveWorkflowUseCase.getObjective(objectiveId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<ObjectiveResponse>> createObjective(
            Principal principal,
            @Valid @RequestBody CreateObjectiveRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Objective objective = objectiveWorkflowUseCase.createObjective(
                    request.name(),
                    request.description(),
                    parseStatus(request.status()),
                    request.ownerTeamId(),
                    request.serviceIds(),
                    request.participatingTeamIds(),
                    request.targetDate(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toObjectiveResponse(objective));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{objectiveId}")
    public Mono<ResponseEntity<ObjectiveResponse>> updateObjective(
            Principal principal,
            @PathVariable String objectiveId,
            @RequestBody UpdateObjectiveRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Objective objective = objectiveWorkflowUseCase.updateObjective(
                    objectiveId,
                    request != null ? request.name() : null,
                    request != null ? request.description() : null,
                    request != null ? parseStatus(request.status()) : null,
                    request != null ? request.ownerTeamId() : null,
                    request != null ? request.serviceIds() : null,
                    request != null ? request.participatingTeamIds() : null,
                    request != null ? request.targetDate() : null,
                    request != null ? request.clearTargetDate() : null,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toObjectiveResponse(objective));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ObjectiveStatus parseStatus(String value) {
        return value != null && !value.isBlank()
                ? ObjectiveStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT))
                : null;
    }

    private ObjectiveResponse toObjectiveResponse(Objective objective) {
        return new ObjectiveResponse(
                objective.getId(),
                objective.getSlug(),
                objective.getName(),
                objective.getDescription(),
                objective.getStatus().name(),
                objective.getOwnerTeamId(),
                objective.getServiceIds(),
                objective.getParticipatingTeamIds(),
                objective.getTargetDate(),
                objective.getCreatedAt(),
                objective.getUpdatedAt());
    }
}
