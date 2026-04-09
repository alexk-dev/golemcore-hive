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

package me.golemcore.hive.fleet.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.model.GolemRoleBinding;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.domain.model.NotificationEvent;
import me.golemcore.hive.domain.model.NotificationSeverity;
import me.golemcore.hive.fleet.application.ActorContext;
import me.golemcore.hive.fleet.application.FleetSettings;
import me.golemcore.hive.fleet.application.port.in.EvaluateGolemPresenceUseCase;
import me.golemcore.hive.fleet.application.port.in.GolemFleetUseCase;
import me.golemcore.hive.fleet.application.port.out.FleetAuditPort;
import me.golemcore.hive.fleet.application.port.out.FleetNotificationPort;
import me.golemcore.hive.fleet.application.port.out.GolemRepository;
import me.golemcore.hive.fleet.application.port.out.GolemRoleRepository;
import me.golemcore.hive.fleet.application.port.out.HeartbeatRepository;

@RequiredArgsConstructor
public class GolemFleetApplicationService implements GolemFleetUseCase, EvaluateGolemPresenceUseCase {

    private static final Pattern ROLE_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,62}$");

    private final GolemRepository golemRepository;
    private final HeartbeatRepository heartbeatRepository;
    private final GolemRoleRepository golemRoleRepository;
    private final FleetAuditPort auditPort;
    private final FleetNotificationPort notificationPort;
    private final FleetSettings settings;

    @Override
    public Golem registerGolem(String displayName,
            String hostLabel,
            String runtimeVersion,
            String buildVersion,
            Set<String> supportedChannels,
            GolemCapabilitySnapshot capabilitySnapshot,
            String enrollmentTokenId) {
        Instant now = Instant.now();
        String golemId = "golem_" + UUID.randomUUID().toString().replace("-", "");
        Golem golem = Golem.builder()
                .id(golemId)
                .displayName(displayName != null && !displayName.isBlank() ? displayName : golemId)
                .hostLabel(hostLabel)
                .runtimeVersion(runtimeVersion)
                .buildVersion(buildVersion)
                .controlChannelUrl(settings.controlChannelUrl())
                .state(GolemState.PENDING_ENROLLMENT)
                .createdAt(now)
                .updatedAt(now)
                .registeredAt(now)
                .lastStateChangeAt(now)
                .enrollmentTokenId(enrollmentTokenId)
                .heartbeatIntervalSeconds(settings.heartbeatIntervalSeconds())
                .missedHeartbeatCount(0)
                .supportedChannels(
                        supportedChannels != null ? new LinkedHashSet<>(supportedChannels) : new LinkedHashSet<>())
                .capabilitySnapshot(capabilitySnapshot)
                .build();
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.registered")
                .severity("INFO")
                .actorType("GOLEM")
                .actorId(golem.getId())
                .actorName(golem.getDisplayName())
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Golem registered")
                .details("Enrollment token " + enrollmentTokenId)
                .build());
        return golem;
    }

    @Override
    public Optional<Golem> findGolem(String golemId) {
        return golemRepository.findById(golemId).map(this::refreshPresenceState);
    }

    @Override
    public List<Golem> listGolems(String query, String state, String roleSlug) {
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        return golemRepository.list().stream()
                .map(this::refreshPresenceState)
                .filter(golem -> normalizedQuery.isBlank() || matchesQuery(golem, normalizedQuery))
                .filter(golem -> state == null || state.isBlank() || golem.getState().name().equalsIgnoreCase(state))
                .filter(golem -> roleSlug == null || roleSlug.isBlank() || golem.getRoleBindings().stream()
                        .map(GolemRoleBinding::getRoleSlug)
                        .anyMatch(roleSlug::equals))
                .sorted(Comparator.comparing(Golem::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Golem::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Golem updateHeartbeat(String golemId, HeartbeatPing heartbeatPing) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        if (golem.getState() == GolemState.REVOKED) {
            throw new IllegalStateException("Revoked golems cannot send heartbeats");
        }
        Instant now = heartbeatPing.getReceivedAt();
        golem.setLastHeartbeat(heartbeatPing);
        golem.setLastHeartbeatAt(now);
        golem.setLastSeenAt(now);
        golem.setUpdatedAt(now);
        golem.setMissedHeartbeatCount(0);
        if (golem.getState() != GolemState.PAUSED) {
            golem.setState(GolemState.ONLINE);
        }
        if (golem.getCapabilitySnapshot() != null && heartbeatPing.getCapabilitySnapshotHash() != null) {
            golem.getCapabilitySnapshot().setSnapshotHash(heartbeatPing.getCapabilitySnapshotHash());
        }
        heartbeatRepository.save(heartbeatPing);
        golemRepository.save(golem);
        return golem;
    }

    @Override
    public Golem pauseGolem(String golemId, String reason) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        golem.setState(GolemState.PAUSED);
        golem.setPauseReason(reason);
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.paused")
                .severity("WARN")
                .actorType("SYSTEM")
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Golem paused")
                .details(reason)
                .build());
        return golem;
    }

    @Override
    public Golem resumeGolem(String golemId) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        if (golem.getState() == GolemState.REVOKED) {
            throw new IllegalStateException("Revoked golems cannot be resumed");
        }
        Instant now = Instant.now();
        golem.setPauseReason(null);
        golem.setState(resolveOperationalState(golem, now));
        golem.setMissedHeartbeatCount(calculateMissedHeartbeats(golem, now));
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.resumed")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Golem resumed")
                .build());
        return golem;
    }

    @Override
    public Golem revokeGolem(String golemId, String reason) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        golem.setState(GolemState.REVOKED);
        golem.setRevokeReason(reason);
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.revoked")
                .severity("WARN")
                .actorType("SYSTEM")
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Golem revoked")
                .details(reason)
                .build());
        return golem;
    }

    @Override
    public List<GolemRole> listRoles() {
        return golemRoleRepository.list().stream()
                .sorted(Comparator.comparing(GolemRole::getSlug))
                .toList();
    }

    @Override
    public Optional<GolemRole> findRole(String slug) {
        return golemRoleRepository.findBySlug(slug);
    }

    @Override
    public GolemRole createRole(String slug, String name, String description, Set<String> capabilityTags) {
        validateRoleSlug(slug);
        if (golemRoleRepository.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("Role already exists: " + slug);
        }
        Instant now = Instant.now();
        GolemRole role = GolemRole.builder()
                .slug(slug)
                .name(name != null && !name.isBlank() ? name : slug)
                .description(description)
                .capabilityTags(capabilityTags != null ? new LinkedHashSet<>(capabilityTags) : new LinkedHashSet<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
        golemRoleRepository.save(role);
        auditPort.record(AuditEvent.builder()
                .eventType("golem_role.created")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("ROLE")
                .targetId(role.getSlug())
                .summary("Golem role created")
                .details(role.getName())
                .build());
        return role;
    }

    @Override
    public GolemRole updateRole(String slug, String name, String description, Set<String> capabilityTags) {
        GolemRole existing = golemRoleRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + slug));
        existing.setName(name != null && !name.isBlank() ? name : existing.getName());
        existing.setDescription(description);
        existing.setCapabilityTags(
                capabilityTags != null ? new LinkedHashSet<>(capabilityTags) : new LinkedHashSet<>());
        existing.setUpdatedAt(Instant.now());
        golemRoleRepository.save(existing);
        auditPort.record(AuditEvent.builder()
                .eventType("golem_role.updated")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("ROLE")
                .targetId(existing.getSlug())
                .summary("Golem role updated")
                .details(existing.getName())
                .build());
        return existing;
    }

    @Override
    public Golem assignRoles(String golemId, List<String> roleSlugs, ActorContext actor) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        List<GolemRoleBinding> bindings = new ArrayList<>(golem.getRoleBindings());
        for (String roleSlug : roleSlugs) {
            GolemRole role = golemRoleRepository.findBySlug(roleSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleSlug));
            boolean exists = bindings.stream().map(GolemRoleBinding::getRoleSlug).anyMatch(role.getSlug()::equals);
            if (!exists) {
                bindings.add(GolemRoleBinding.builder()
                        .roleSlug(role.getSlug())
                        .assignedAt(now)
                        .assignedByOperatorId(actor.subjectId())
                        .assignedByOperatorUsername(actor.name())
                        .build());
            }
        }
        golem.setRoleBindings(bindings);
        golem.setUpdatedAt(now);
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.roles_assigned")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actor.subjectId())
                .actorName(actor.name())
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Roles assigned to golem")
                .details(String.join(", ", roleSlugs))
                .build());
        return golem;
    }

    @Override
    public Golem unassignRoles(String golemId, List<String> roleSlugs) {
        Golem golem = golemRepository.findById(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        List<GolemRoleBinding> bindings = golem.getRoleBindings().stream()
                .filter(binding -> !roleSlugs.contains(binding.getRoleSlug()))
                .toList();
        golem.setRoleBindings(new ArrayList<>(bindings));
        golem.setUpdatedAt(Instant.now());
        golemRepository.save(golem);
        auditPort.record(AuditEvent.builder()
                .eventType("golem.roles_unassigned")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("GOLEM")
                .targetId(golem.getId())
                .golemId(golem.getId())
                .summary("Roles unassigned from golem")
                .details(String.join(", ", roleSlugs))
                .build());
        return golem;
    }

    public Golem updatePolicyBinding(String golemId, GolemPolicyBinding policyBinding) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        golem.setPolicyBinding(policyBinding);
        golem.setUpdatedAt(Instant.now());
        golemRepository.save(golem);
        return golem;
    }

    public Golem clearPolicyBinding(String golemId) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        golem.setPolicyBinding(null);
        golem.setUpdatedAt(Instant.now());
        golemRepository.save(golem);
        return golem;
    }

    @Override
    public void evaluatePresence() {
        Instant now = Instant.now();
        for (Golem golem : golemRepository.list()) {
            GolemState resolvedState = resolveState(golem, now);
            int missedHeartbeats = calculateMissedHeartbeats(golem, now);
            if (golem.getState() != resolvedState || golem.getMissedHeartbeatCount() != missedHeartbeats) {
                GolemState previousState = golem.getState();
                golem.setState(resolvedState);
                golem.setMissedHeartbeatCount(missedHeartbeats);
                if (golem.getLastStateChangeAt() == null || resolvedState != GolemState.PAUSED) {
                    golem.setLastStateChangeAt(now);
                }
                golemRepository.save(golem);
                if (previousState != resolvedState) {
                    auditPort.record(AuditEvent.builder()
                            .eventType("golem.state_changed")
                            .severity(resolvedState == GolemState.OFFLINE ? "WARN" : "INFO")
                            .actorType("SYSTEM")
                            .targetType("GOLEM")
                            .targetId(golem.getId())
                            .golemId(golem.getId())
                            .summary("Golem state changed to " + resolvedState)
                            .details("Previous state " + previousState)
                            .build());
                    if (resolvedState == GolemState.OFFLINE && notificationPort.isGolemOfflineEnabled()) {
                        notificationPort.create(NotificationEvent.builder()
                                .type("GOLEM_OFFLINE")
                                .severity(NotificationSeverity.CRITICAL)
                                .title("Golem offline")
                                .message(golem.getDisplayName() + " is offline")
                                .golemId(golem.getId())
                                .build());
                    }
                }
            }
        }
    }

    public GolemState resolveState(Golem golem, Instant now) {
        if (golem.getState() == GolemState.REVOKED) {
            return GolemState.REVOKED;
        }
        if (golem.getState() == GolemState.PAUSED) {
            return GolemState.PAUSED;
        }
        return resolveOperationalState(golem, now);
    }

    public GolemState resolveOperationalState(Golem golem, Instant now) {
        if (golem.getLastHeartbeatAt() == null) {
            return GolemState.PENDING_ENROLLMENT;
        }
        int missedHeartbeats = calculateMissedHeartbeats(golem, now);
        if (missedHeartbeats >= settings.offlineAfterMisses()) {
            return GolemState.OFFLINE;
        }
        if (missedHeartbeats >= settings.degradedAfterMisses()) {
            return GolemState.DEGRADED;
        }
        return GolemState.ONLINE;
    }

    public int calculateMissedHeartbeats(Golem golem, Instant now) {
        if (golem.getLastHeartbeatAt() == null) {
            return 0;
        }
        int heartbeatInterval = Math.max(golem.getHeartbeatIntervalSeconds(), settings.heartbeatIntervalSeconds());
        long secondsSinceHeartbeat = Duration.between(golem.getLastHeartbeatAt(), now).getSeconds();
        if (secondsSinceHeartbeat <= 0) {
            return 0;
        }
        return (int) (secondsSinceHeartbeat / heartbeatInterval);
    }

    private Golem refreshPresenceState(Golem golem) {
        Instant now = Instant.now();
        golem.setMissedHeartbeatCount(calculateMissedHeartbeats(golem, now));
        golem.setState(resolveState(golem, now));
        return golem;
    }

    private boolean matchesQuery(Golem golem, String normalizedQuery) {
        return containsIgnoreCase(golem.getId(), normalizedQuery)
                || containsIgnoreCase(golem.getDisplayName(), normalizedQuery)
                || containsIgnoreCase(golem.getHostLabel(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void validateRoleSlug(String slug) {
        if (slug == null || !ROLE_SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Role slug must match ^[a-z0-9][a-z0-9._-]{0,62}$");
        }
    }
}
