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
import me.golemcore.hive.adapter.inbound.web.dto.organization.CreateTeamRequest;
import me.golemcore.hive.adapter.inbound.web.dto.organization.TeamResponse;
import me.golemcore.hive.adapter.inbound.web.dto.organization.UpdateTeamRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.domain.service.TeamService;
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
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamsController {

    private final TeamService teamService;

    @GetMapping
    public Mono<ResponseEntity<List<TeamResponse>>> listTeams(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(teamService.listTeams().stream().map(this::toTeamResponse).toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{teamId}")
    public Mono<ResponseEntity<TeamResponse>> getTeam(Principal principal, @PathVariable String teamId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toTeamResponse(teamService.getTeam(teamId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<TeamResponse>> createTeam(
            Principal principal,
            @Valid @RequestBody CreateTeamRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Team team = teamService.createTeam(
                    request.name(),
                    request.description(),
                    request.golemIds(),
                    request.ownedServiceIds(),
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toTeamResponse(team));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/{teamId}")
    public Mono<ResponseEntity<TeamResponse>> updateTeam(
            Principal principal,
            @PathVariable String teamId,
            @RequestBody UpdateTeamRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            Team team = teamService.updateTeam(
                    teamId,
                    request != null ? request.name() : null,
                    request != null ? request.description() : null,
                    request != null ? request.golemIds() : null,
                    request != null ? request.ownedServiceIds() : null,
                    actor.getSubjectId(),
                    actor.getName());
            return ResponseEntity.ok(toTeamResponse(team));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getSlug(),
                team.getName(),
                team.getDescription(),
                team.getGolemIds(),
                team.getOwnedServiceIds(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
