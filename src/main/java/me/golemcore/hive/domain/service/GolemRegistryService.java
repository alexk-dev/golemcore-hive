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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemRole;
import me.golemcore.hive.domain.model.GolemRoleBinding;
import me.golemcore.hive.domain.model.GolemState;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GolemRegistryService {

    private static final String GOLEMS_DIR = "golems";
    private static final String HEARTBEATS_DIR = "heartbeats";
    private static final String GOLEM_ROLES_DIR = "golem-roles";
    private static final Pattern ROLE_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,62}$");

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final HiveProperties properties;
    private final GolemPresenceService presenceService;

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
                .controlChannelUrl(properties.getFleet().getControlChannelUrl())
                .state(GolemState.PENDING_ENROLLMENT)
                .createdAt(now)
                .updatedAt(now)
                .registeredAt(now)
                .lastStateChangeAt(now)
                .enrollmentTokenId(enrollmentTokenId)
                .heartbeatIntervalSeconds(properties.getFleet().getHeartbeatIntervalSeconds())
                .missedHeartbeatCount(0)
                .supportedChannels(supportedChannels != null ? new LinkedHashSet<>(supportedChannels) : new LinkedHashSet<>())
                .capabilitySnapshot(capabilitySnapshot)
                .build();
        saveGolem(golem);
        return golem;
    }

    public Optional<Golem> findGolem(String golemId) {
        String content = storagePort.getText(GOLEMS_DIR, golemId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            Golem golem = objectMapper.readValue(content, Golem.class);
            Instant now = Instant.now();
            golem.setMissedHeartbeatCount(presenceService.calculateMissedHeartbeats(golem, now));
            golem.setState(presenceService.resolveState(golem, now));
            return Optional.of(golem);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize golem " + golemId, exception);
        }
    }

    public List<Golem> listGolems(String query, String state, String roleSlug) {
        String normalizedQuery = query != null ? query.trim().toLowerCase() : "";
        List<String> files = storagePort.listObjects(GOLEMS_DIR, "");
        List<Golem> golems = new ArrayList<>();
        for (String file : files) {
            Optional<Golem> golemOptional = findGolem(stripJsonSuffix(file));
            if (golemOptional.isEmpty()) {
                continue;
            }
            Golem golem = golemOptional.get();
            if (!normalizedQuery.isBlank() && !matchesQuery(golem, normalizedQuery)) {
                continue;
            }
            if (state != null && !state.isBlank() && !golem.getState().name().equalsIgnoreCase(state)) {
                continue;
            }
            if (roleSlug != null && !roleSlug.isBlank() && golem.getRoleBindings().stream()
                    .map(GolemRoleBinding::getRoleSlug)
                    .noneMatch(role -> role.equals(roleSlug))) {
                continue;
            }
            golems.add(golem);
        }
        golems.sort(Comparator.comparing(Golem::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Golem::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return golems;
    }

    public Golem updateHeartbeat(String golemId, HeartbeatPing heartbeatPing) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
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
        saveHeartbeat(heartbeatPing);
        saveGolem(golem);
        return golem;
    }

    public Golem pauseGolem(String golemId, String reason) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        golem.setState(GolemState.PAUSED);
        golem.setPauseReason(reason);
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        saveGolem(golem);
        return golem;
    }

    public Golem resumeGolem(String golemId) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        if (golem.getState() == GolemState.REVOKED) {
            throw new IllegalStateException("Revoked golems cannot be resumed");
        }
        Instant now = Instant.now();
        golem.setPauseReason(null);
        golem.setState(presenceService.resolveOperationalState(golem, now));
        golem.setMissedHeartbeatCount(presenceService.calculateMissedHeartbeats(golem, now));
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        saveGolem(golem);
        return golem;
    }

    public Golem revokeGolem(String golemId, String reason) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        golem.setState(GolemState.REVOKED);
        golem.setRevokeReason(reason);
        golem.setUpdatedAt(now);
        golem.setLastStateChangeAt(now);
        saveGolem(golem);
        return golem;
    }

    public List<GolemRole> listRoles() {
        List<String> files = storagePort.listObjects(GOLEM_ROLES_DIR, "");
        List<GolemRole> roles = new ArrayList<>();
        for (String file : files) {
            Optional<GolemRole> roleOptional = loadRole(stripJsonSuffix(file));
            roleOptional.ifPresent(roles::add);
        }
        roles.sort(Comparator.comparing(GolemRole::getSlug));
        return roles;
    }

    public Optional<GolemRole> findRole(String slug) {
        return loadRole(slug);
    }

    public GolemRole createRole(String slug, String name, String description, Set<String> capabilityTags) {
        validateRoleSlug(slug);
        if (findRole(slug).isPresent()) {
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
        saveRole(role);
        return role;
    }

    public GolemRole updateRole(String slug, String name, String description, Set<String> capabilityTags) {
        GolemRole existing = findRole(slug).orElseThrow(() -> new IllegalArgumentException("Unknown role: " + slug));
        existing.setName(name != null && !name.isBlank() ? name : existing.getName());
        existing.setDescription(description);
        existing.setCapabilityTags(capabilityTags != null ? new LinkedHashSet<>(capabilityTags) : new LinkedHashSet<>());
        existing.setUpdatedAt(Instant.now());
        saveRole(existing);
        return existing;
    }

    public Golem assignRoles(String golemId, List<String> roleSlugs, AuthenticatedActor actor) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        List<GolemRoleBinding> bindings = new ArrayList<>(golem.getRoleBindings());
        for (String roleSlug : roleSlugs) {
            GolemRole role = findRole(roleSlug).orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleSlug));
            boolean exists = bindings.stream().map(GolemRoleBinding::getRoleSlug).anyMatch(role.getSlug()::equals);
            if (!exists) {
                bindings.add(GolemRoleBinding.builder()
                        .roleSlug(role.getSlug())
                        .assignedAt(now)
                        .assignedByOperatorId(actor.getSubjectId())
                        .assignedByOperatorUsername(actor.getName())
                        .build());
            }
        }
        golem.setRoleBindings(bindings);
        golem.setUpdatedAt(now);
        saveGolem(golem);
        return golem;
    }

    public Golem unassignRoles(String golemId, List<String> roleSlugs) {
        Golem golem = findGolem(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        List<GolemRoleBinding> bindings = golem.getRoleBindings().stream()
                .filter(binding -> !roleSlugs.contains(binding.getRoleSlug()))
                .toList();
        golem.setRoleBindings(new ArrayList<>(bindings));
        golem.setUpdatedAt(Instant.now());
        saveGolem(golem);
        return golem;
    }

    private Optional<GolemRole> loadRole(String slug) {
        String content = storagePort.getText(GOLEM_ROLES_DIR, slug + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, GolemRole.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize role " + slug, exception);
        }
    }

    private void saveGolem(Golem golem) {
        try {
            storagePort.putTextAtomic(GOLEMS_DIR, golem.getId() + ".json", objectMapper.writeValueAsString(golem));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize golem " + golem.getId(), exception);
        }
    }

    private void saveHeartbeat(HeartbeatPing heartbeatPing) {
        try {
            storagePort.putTextAtomic(HEARTBEATS_DIR, heartbeatPing.getGolemId() + ".json",
                    objectMapper.writeValueAsString(heartbeatPing));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize heartbeat for " + heartbeatPing.getGolemId(), exception);
        }
    }

    private void saveRole(GolemRole role) {
        try {
            storagePort.putTextAtomic(GOLEM_ROLES_DIR, role.getSlug() + ".json", objectMapper.writeValueAsString(role));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize role " + role.getSlug(), exception);
        }
    }

    private boolean matchesQuery(Golem golem, String normalizedQuery) {
        return containsIgnoreCase(golem.getId(), normalizedQuery)
                || containsIgnoreCase(golem.getDisplayName(), normalizedQuery)
                || containsIgnoreCase(golem.getHostLabel(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private void validateRoleSlug(String slug) {
        if (slug == null || !ROLE_SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Role slug must match ^[a-z0-9][a-z0-9._-]{0,62}$");
        }
    }

    private String stripJsonSuffix(String file) {
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }
}
