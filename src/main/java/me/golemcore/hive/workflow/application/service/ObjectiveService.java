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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.EntityLifecycleState;
import me.golemcore.hive.domain.model.Objective;
import me.golemcore.hive.domain.model.ObjectiveStatus;
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ObjectiveWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.TeamWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.ObjectiveRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;

public class ObjectiveService implements ObjectiveWorkflowUseCase {

    private final ObjectiveRepository objectiveRepository;
    private final TeamWorkflowUseCase teamWorkflowUseCase;
    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final WorkflowAuditPort workflowAuditPort;

    public ObjectiveService(
            ObjectiveRepository objectiveRepository,
            TeamWorkflowUseCase teamWorkflowUseCase,
            BoardWorkflowUseCase boardWorkflowUseCase,
            WorkflowAuditPort workflowAuditPort) {
        this.objectiveRepository = objectiveRepository;
        this.teamWorkflowUseCase = teamWorkflowUseCase;
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.workflowAuditPort = workflowAuditPort;
    }

    @Override
    public List<Objective> listObjectives() {
        return listObjectives(false);
    }

    @Override
    public List<Objective> listObjectives(boolean includeArchived) {
        List<Objective> objectives = new ArrayList<>();
        for (Objective storedObjective : objectiveRepository.list()) {
            Objective objective = normalizeObjective(storedObjective);
            if (!includeArchived && objective.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
                continue;
            }
            objectives.add(objective);
        }
        objectives.sort(Comparator.comparing(Objective::getUpdatedAt).reversed()
                .thenComparing(Objective::getName, String.CASE_INSENSITIVE_ORDER));
        return objectives;
    }

    @Override
    public Optional<Objective> findObjective(String objectiveId) {
        return objectiveRepository.findById(objectiveId).map(this::normalizeObjective);
    }

    @Override
    public Objective getObjective(String objectiveId) {
        return findObjective(objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown objective: " + objectiveId));
    }

    @Override
    public Objective createObjective(
            String name,
            String description,
            ObjectiveStatus status,
            String ownerTeamId,
            Set<String> serviceIds,
            Set<String> participatingTeamIds,
            LocalDate targetDate,
            String actorId,
            String actorName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Objective name is required");
        }
        Team ownerTeam = validateOwnerTeam(ownerTeamId);
        validateOwnerTeamServiceScope(ownerTeam, serviceIds);
        validateServiceIds(serviceIds);
        validateTeamIds(participatingTeamIds);
        Instant now = Instant.now();
        Set<String> resolvedTeams = participatingTeamIds != null
                ? new LinkedHashSet<>(participatingTeamIds)
                : new LinkedHashSet<>();
        if (ownerTeamId != null && !ownerTeamId.isBlank()) {
            resolvedTeams.add(ownerTeamId);
        }
        Objective objective = Objective.builder()
                .id("objective_" + UUID.randomUUID().toString().replace("-", ""))
                .slug(buildUniqueSlug(name))
                .name(name)
                .description(description)
                .status(status != null ? status : ObjectiveStatus.ACTIVE)
                .ownerTeamId(ownerTeamId)
                .lifecycleState(EntityLifecycleState.ACTIVE)
                .serviceIds(serviceIds != null ? new LinkedHashSet<>(serviceIds) : new LinkedHashSet<>())
                .participatingTeamIds(resolvedTeams)
                .targetDate(targetDate)
                .archivedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        objectiveRepository.save(objective);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("objective.created")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("OBJECTIVE")
                .targetId(objective.getId())
                .summary("Objective created")
                .details(objective.getName()));
        return objective;
    }

    @Override
    public Objective updateObjective(
            String objectiveId,
            String name,
            String description,
            ObjectiveStatus status,
            String ownerTeamId,
            Set<String> serviceIds,
            Set<String> participatingTeamIds,
            LocalDate targetDate,
            Boolean clearTargetDate,
            String actorId,
            String actorName) {
        Objective objective = getObjective(objectiveId);
        if (objective.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
            throw new IllegalArgumentException("Archived objective cannot be updated: " + objectiveId);
        }
        if (name != null && !name.isBlank()) {
            objective.setName(name);
        }
        if (description != null) {
            objective.setDescription(description);
        }
        if (status != null) {
            objective.setStatus(status);
        }
        if (ownerTeamId != null) {
            Team ownerTeam = validateOwnerTeam(ownerTeamId);
            validateOwnerTeamServiceScope(ownerTeam, serviceIds != null ? serviceIds : objective.getServiceIds());
            objective.setOwnerTeamId(ownerTeamId);
        }
        if (serviceIds != null) {
            validateServiceIds(serviceIds);
            Team currentOwnerTeam = validateOwnerTeam(objective.getOwnerTeamId());
            validateOwnerTeamServiceScope(currentOwnerTeam, serviceIds);
            objective.setServiceIds(new LinkedHashSet<>(serviceIds));
        }
        if (participatingTeamIds != null) {
            validateTeamIds(participatingTeamIds);
            objective.setParticipatingTeamIds(new LinkedHashSet<>(participatingTeamIds));
        }
        if (Boolean.TRUE.equals(clearTargetDate)) {
            objective.setTargetDate(null);
        } else if (targetDate != null) {
            objective.setTargetDate(targetDate);
        }
        if (objective.getOwnerTeamId() != null && !objective.getOwnerTeamId().isBlank()) {
            objective.getParticipatingTeamIds().add(objective.getOwnerTeamId());
        }
        objective.setUpdatedAt(Instant.now());
        objectiveRepository.save(objective);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("objective.updated")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("OBJECTIVE")
                .targetId(objective.getId())
                .summary("Objective updated")
                .details(objective.getName()));
        return objective;
    }

    @Override
    public Objective completeObjective(String objectiveId, String actorId, String actorName) {
        Objective objective = getObjective(objectiveId);
        if (objective.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
            throw new IllegalArgumentException("Archived objective cannot be completed: " + objectiveId);
        }
        objective.setStatus(ObjectiveStatus.COMPLETED);
        objective.setUpdatedAt(Instant.now());
        objectiveRepository.save(objective);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("objective.completed")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("OBJECTIVE")
                .targetId(objective.getId())
                .summary("Objective completed")
                .details(objective.getName()));
        return objective;
    }

    @Override
    public Objective reopenObjective(String objectiveId, String actorId, String actorName) {
        Objective objective = getObjective(objectiveId);
        if (objective.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
            throw new IllegalArgumentException("Archived objective cannot be reopened: " + objectiveId);
        }
        if (objective.getStatus() == ObjectiveStatus.COMPLETED) {
            objective.setStatus(ObjectiveStatus.ACTIVE);
            objective.setUpdatedAt(Instant.now());
            objectiveRepository.save(objective);
            workflowAuditPort.record(AuditEvent.builder()
                    .eventType("objective.reopened")
                    .severity("INFO")
                    .actorType("OPERATOR")
                    .actorId(actorId)
                    .actorName(actorName)
                    .targetType("OBJECTIVE")
                    .targetId(objective.getId())
                    .summary("Objective reopened")
                    .details(objective.getName()));
        }
        return objective;
    }

    @Override
    public Objective archiveObjective(String objectiveId, String actorId, String actorName) {
        Objective objective = getObjective(objectiveId);
        if (objective.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
            return objective;
        }
        objective.setLifecycleState(EntityLifecycleState.ARCHIVED);
        objective.setArchivedAt(Instant.now());
        objective.setUpdatedAt(objective.getArchivedAt());
        objectiveRepository.save(objective);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("objective.archived")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("OBJECTIVE")
                .targetId(objective.getId())
                .summary("Objective archived")
                .details(objective.getName()));
        return objective;
    }

    @Override
    public Objective restoreObjective(String objectiveId, String actorId, String actorName) {
        Objective objective = getObjective(objectiveId);
        if (objective.getLifecycleState() == EntityLifecycleState.ACTIVE) {
            return objective;
        }
        Team ownerTeam = validateOwnerTeam(objective.getOwnerTeamId());
        validateOwnerTeamServiceScope(ownerTeam, objective.getServiceIds());
        validateServiceIds(objective.getServiceIds());
        validateTeamIds(objective.getParticipatingTeamIds());
        objective.setLifecycleState(EntityLifecycleState.ACTIVE);
        objective.setArchivedAt(null);
        objective.setUpdatedAt(Instant.now());
        objectiveRepository.save(objective);
        workflowAuditPort.record(AuditEvent.builder()
                .eventType("objective.restored")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("OBJECTIVE")
                .targetId(objective.getId())
                .summary("Objective restored")
                .details(objective.getName()));
        return objective;
    }

    private Team validateOwnerTeam(String ownerTeamId) {
        if (ownerTeamId == null || ownerTeamId.isBlank()) {
            throw new IllegalArgumentException("Objective owner team is required");
        }
        Team team = teamWorkflowUseCase.getTeam(ownerTeamId);
        if (team.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
            throw new IllegalArgumentException("Objective owner team is archived: " + ownerTeamId);
        }
        return team;
    }

    private void validateOwnerTeamServiceScope(Team ownerTeam, Set<String> serviceIds) {
        if (ownerTeam == null || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        Set<String> ownedServiceIds = ownerTeam.getOwnedServiceIds() != null
                ? ownerTeam.getOwnedServiceIds()
                : new LinkedHashSet<>();
        for (String serviceId : serviceIds) {
            if (!ownedServiceIds.contains(serviceId)) {
                throw new IllegalArgumentException("Objective owner team does not own service: " + serviceId);
            }
        }
    }

    private void validateServiceIds(Set<String> serviceIds) {
        if (serviceIds == null) {
            return;
        }
        for (String serviceId : serviceIds) {
            if (serviceId == null || serviceId.isBlank() || boardWorkflowUseCase.findBoard(serviceId).isEmpty()) {
                throw new IllegalArgumentException("Unknown service: " + serviceId);
            }
        }
    }

    private void validateTeamIds(Set<String> teamIds) {
        if (teamIds == null) {
            return;
        }
        for (String teamId : teamIds) {
            if (teamId == null || teamId.isBlank()) {
                throw new IllegalArgumentException("Unknown team: " + teamId);
            }
            Team team = teamWorkflowUseCase.getTeam(teamId);
            if (team.getLifecycleState() == EntityLifecycleState.ARCHIVED) {
                throw new IllegalArgumentException("Archived team cannot participate in objective: " + teamId);
            }
        }
    }

    private Objective normalizeObjective(Objective objective) {
        if (objective == null) {
            return null;
        }
        objective.setSchemaVersion(Math.max(objective.getSchemaVersion(), 2));
        if (objective.getLifecycleState() == null) {
            objective.setLifecycleState(EntityLifecycleState.ACTIVE);
        }
        if (objective.getStatus() == null) {
            objective.setStatus(ObjectiveStatus.ACTIVE);
        }
        if (objective.getServiceIds() == null) {
            objective.setServiceIds(new LinkedHashSet<>());
        }
        if (objective.getParticipatingTeamIds() == null) {
            objective.setParticipatingTeamIds(new LinkedHashSet<>());
        }
        return objective;
    }

    private String buildUniqueSlug(String name) {
        String baseSlug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (baseSlug.isBlank()) {
            baseSlug = "objective";
        }
        Set<String> existingSlugs = listObjectives(true).stream().map(Objective::getSlug)
                .collect(Collectors.toSet());
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
