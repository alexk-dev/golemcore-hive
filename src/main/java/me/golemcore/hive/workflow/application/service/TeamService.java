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

package me.golemcore.hive.workflow.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.TeamRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;

public class TeamService implements TeamWorkflowUseCase {

    private final TeamRepository teamRepository;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final WorkflowAuditPort workflowAuditPort;

    public TeamService(
            TeamRepository teamRepository,
            GolemDirectoryUseCase golemDirectoryUseCase,
            BoardWorkflowUseCase boardWorkflowUseCase,
            WorkflowAuditPort workflowAuditPort) {
        this.teamRepository = teamRepository;
        this.golemDirectoryUseCase = golemDirectoryUseCase;
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.workflowAuditPort = workflowAuditPort;
    }

    @Override
    public List<Team> listTeams() {
        List<Team> teams = new ArrayList<>(teamRepository.list());
        teams.sort(Comparator.comparing(Team::getUpdatedAt).reversed()
                .thenComparing(Team::getName, String.CASE_INSENSITIVE_ORDER));
        return teams;
    }

    @Override
    public Optional<Team> findTeam(String teamId) {
        return teamRepository.findById(teamId);
    }

    @Override
    public Team getTeam(String teamId) {
        return findTeam(teamId).orElseThrow(() -> new IllegalArgumentException("Unknown team: " + teamId));
    }

    @Override
    public Team createTeam(
            String name,
            String description,
            Set<String> golemIds,
            Set<String> ownedServiceIds,
            String actorId,
            String actorName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Team name is required");
        }
        validateReferences(golemIds, ownedServiceIds);
        Instant now = Instant.now();
        Team team = Team.builder()
                .id("team_" + UUID.randomUUID().toString().replace("-", ""))
                .slug(buildUniqueSlug(name))
                .name(name)
                .description(description)
                .golemIds(golemIds != null ? new LinkedHashSet<>(golemIds) : new LinkedHashSet<>())
                .ownedServiceIds(
                        ownedServiceIds != null ? new LinkedHashSet<>(ownedServiceIds) : new LinkedHashSet<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
        teamRepository.save(team);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("team.created")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("TEAM")
                .targetId(team.getId())
                .summary("Team created")
                .details(team.getName()));
        return team;
    }

    @Override
    public Team updateTeam(
            String teamId,
            String name,
            String description,
            Set<String> golemIds,
            Set<String> ownedServiceIds,
            String actorId,
            String actorName) {
        Team team = getTeam(teamId);
        if (name != null && !name.isBlank()) {
            team.setName(name);
        }
        if (description != null) {
            team.setDescription(description);
        }
        if (golemIds != null) {
            validateGolemIds(golemIds);
            team.setGolemIds(new LinkedHashSet<>(golemIds));
        }
        if (ownedServiceIds != null) {
            validateServiceIds(ownedServiceIds);
            team.setOwnedServiceIds(new LinkedHashSet<>(ownedServiceIds));
        }
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("team.updated")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("TEAM")
                .targetId(team.getId())
                .summary("Team updated")
                .details(team.getName()));
        return team;
    }

    private void validateReferences(Set<String> golemIds, Set<String> ownedServiceIds) {
        validateGolemIds(golemIds);
        validateServiceIds(ownedServiceIds);
    }

    private void validateGolemIds(Set<String> golemIds) {
        if (golemIds == null) {
            return;
        }
        for (String golemId : golemIds) {
            if (golemId == null || golemId.isBlank() || golemDirectoryUseCase.findGolem(golemId).isEmpty()) {
                throw new IllegalArgumentException("Unknown golem: " + golemId);
            }
        }
    }

    private void validateServiceIds(Set<String> ownedServiceIds) {
        if (ownedServiceIds == null) {
            return;
        }
        for (String serviceId : ownedServiceIds) {
            if (serviceId == null || serviceId.isBlank() || boardWorkflowUseCase.findBoard(serviceId).isEmpty()) {
                throw new IllegalArgumentException("Unknown service: " + serviceId);
            }
        }
    }

    private String buildUniqueSlug(String name) {
        String baseSlug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (baseSlug.isBlank()) {
            baseSlug = "team";
        }
        Set<String> existingSlugs = listTeams().stream().map(Team::getSlug)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingSlugs.contains(baseSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (existingSlugs.contains(baseSlug + "-" + suffix)) {
            suffix++;
        }
        return baseSlug + "-" + suffix;
    }
}
