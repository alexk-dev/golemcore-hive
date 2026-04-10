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

package me.golemcore.hive.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final String TEAMS_DIR = "teams";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final GolemRegistryService golemRegistryService;
    private final BoardService boardService;
    private final AuditService auditService;

    public List<Team> listTeams() {
        List<Team> teams = new ArrayList<>();
        for (String path : storagePort.listObjects(TEAMS_DIR, "")) {
            loadTeamByPath(path).ifPresent(teams::add);
        }
        teams.sort(Comparator.comparing(Team::getUpdatedAt).reversed()
                .thenComparing(Team::getName, String.CASE_INSENSITIVE_ORDER));
        return teams;
    }

    public Optional<Team> findTeam(String teamId) {
        String content = storagePort.getText(TEAMS_DIR, teamId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Team.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize team " + teamId, exception);
        }
    }

    public Team getTeam(String teamId) {
        return findTeam(teamId).orElseThrow(() -> new IllegalArgumentException("Unknown team: " + teamId));
    }

    public Team createTeam(String name,
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
        saveTeam(team);
        auditService.record(AuditEvent.builder()
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

    public Team updateTeam(String teamId,
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
        saveTeam(team);
        auditService.record(AuditEvent.builder()
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
            if (golemId == null || golemId.isBlank() || golemRegistryService.findGolem(golemId).isEmpty()) {
                throw new IllegalArgumentException("Unknown golem: " + golemId);
            }
        }
    }

    private void validateServiceIds(Set<String> ownedServiceIds) {
        if (ownedServiceIds == null) {
            return;
        }
        for (String serviceId : ownedServiceIds) {
            if (serviceId == null || serviceId.isBlank() || boardService.findBoard(serviceId).isEmpty()) {
                throw new IllegalArgumentException("Unknown service: " + serviceId);
            }
        }
    }

    private Optional<Team> loadTeamByPath(String path) {
        String content = storagePort.getText(TEAMS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Team.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize team " + path, exception);
        }
    }

    private void saveTeam(Team team) {
        try {
            storagePort.putTextAtomic(TEAMS_DIR, team.getId() + ".json", objectMapper.writeValueAsString(team));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize team " + team.getId(), exception);
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
