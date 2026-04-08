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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyGroupService {

    private static final String POLICY_GROUPS_DIR = "policy-groups";
    private static final String POLICY_GROUP_VERSIONS_DIR = "policy-group-versions";
    private static final String GOLEM_POLICY_BINDINGS_DIR = "golem-policy-bindings";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final GolemRegistryService golemRegistryService;

    public PolicyGroup createPolicyGroup(
            String slug,
            String name,
            String description,
            String actorId,
            String actorName) {
        validateSlug(slug);
        ensureSlugAvailable(slug);
        Instant now = Instant.now();
        PolicyGroup policyGroup = PolicyGroup.builder()
                .id("pg_" + UUID.randomUUID().toString().replace("-", ""))
                .slug(slug.trim())
                .name(normalizeName(slug, name))
                .description(normalizeOptional(description))
                .status("DRAFT")
                .currentVersion(0)
                .draftSpec(PolicyGroupSpec.builder().build())
                .createdAt(now)
                .updatedAt(now)
                .build();
        savePolicyGroup(policyGroup);
        recordAudit("policy_group.created", actorId, actorName, policyGroup.getId(), "Policy group created",
                policyGroup.getSlug());
        return policyGroup;
    }

    public List<PolicyGroup> listPolicyGroups() {
        List<PolicyGroup> policyGroups = new ArrayList<>();
        for (String path : storagePort.listObjects(POLICY_GROUPS_DIR, "")) {
            Optional<PolicyGroup> policyGroup = loadPolicyGroup(path);
            policyGroup.ifPresent(policyGroups::add);
        }
        policyGroups
                .sort(Comparator.comparing(PolicyGroup::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PolicyGroup::getSlug));
        return policyGroups;
    }

    public PolicyGroup getPolicyGroup(String groupId) {
        return findPolicyGroup(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy group: " + groupId));
    }

    public Optional<PolicyGroup> findPolicyGroup(String groupId) {
        return loadPolicyGroup(groupId + ".json");
    }

    public PolicyGroup updateDraft(String groupId, PolicyGroupSpec draftSpec) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        PolicyGroupSpec mergedDraftSpec = mergeSecrets(policyGroup.getDraftSpec(), draftSpec);
        PolicyGroupSpec normalizedDraftSpec = normalizeSpec(mergedDraftSpec);
        policyGroup.setDraftSpec(normalizedDraftSpec);
        policyGroup.setUpdatedAt(Instant.now());
        savePolicyGroup(policyGroup);
        return policyGroup;
    }

    public PolicyGroupVersion publish(String groupId, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        if (policyGroup.getDraftSpec() == null) {
            throw new IllegalArgumentException("Policy group draft is required before publish");
        }
        PolicyGroupSpec specSnapshot = copy(normalizeSpec(policyGroup.getDraftSpec()), PolicyGroupSpec.class);
        Instant now = Instant.now();
        int version = nextVersion(groupId);
        PolicyGroupVersion policyGroupVersion = PolicyGroupVersion.builder()
                .policyGroupId(groupId)
                .version(version)
                .specSnapshot(specSnapshot)
                .checksum(specSnapshot.getChecksum())
                .changeSummary(normalizeOptional(changeSummary))
                .publishedAt(now)
                .publishedBy(actorId)
                .publishedByName(actorName)
                .build();
        savePolicyGroupVersion(policyGroupVersion);

        policyGroup.setCurrentVersion(version);
        policyGroup.setStatus("ACTIVE");
        policyGroup.setUpdatedAt(now);
        policyGroup.setLastPublishedAt(now);
        policyGroup.setLastPublishedBy(actorId);
        policyGroup.setLastPublishedByName(actorName);
        savePolicyGroup(policyGroup);

        markBoundGolemsSyncPending(groupId, version, now);
        recordAudit("policy_group.published", actorId, actorName, groupId, "Policy group published",
                "v" + version + ": " + normalizeOptional(changeSummary));
        return policyGroupVersion;
    }

    public List<PolicyGroupVersion> listVersions(String groupId) {
        List<PolicyGroupVersion> versions = new ArrayList<>();
        for (String path : storagePort.listObjects(POLICY_GROUP_VERSIONS_DIR, groupId + "/")) {
            Optional<PolicyGroupVersion> version = loadPolicyGroupVersion(path);
            version.ifPresent(versions::add);
        }
        versions.sort(Comparator.comparingInt(PolicyGroupVersion::getVersion).reversed());
        return versions;
    }

    public PolicyGroupVersion getVersion(String groupId, int version) {
        String path = versionPath(groupId, version);
        return loadPolicyGroupVersion(path)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy group version: " + groupId + " v"
                        + version));
    }

    public PolicyGroup rollback(String groupId, int version, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        getVersion(groupId, version);
        Instant now = Instant.now();
        policyGroup.setCurrentVersion(version);
        policyGroup.setUpdatedAt(now);
        policyGroup.setLastPublishedAt(now);
        policyGroup.setLastPublishedBy(actorId);
        policyGroup.setLastPublishedByName(actorName);
        savePolicyGroup(policyGroup);
        markBoundGolemsSyncPending(groupId, version, now);
        recordAudit("policy_group.rolled_back", actorId, actorName, groupId, "Policy group rolled back",
                "v" + version + ": " + normalizeOptional(changeSummary));
        return policyGroup;
    }

    public GolemPolicyBinding bindGolem(String golemId, String groupId, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        if (policyGroup.getCurrentVersion() <= 0) {
            throw new IllegalStateException("Policy group must be published before binding golems");
        }
        golemRegistryService.findGolem(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown golem: " + golemId));
        Instant now = Instant.now();
        GolemPolicyBinding binding = GolemPolicyBinding.builder()
                .golemId(golemId)
                .policyGroupId(groupId)
                .targetVersion(policyGroup.getCurrentVersion())
                .appliedVersion(null)
                .syncStatus(PolicySyncStatus.SYNC_PENDING)
                .lastSyncRequestedAt(now)
                .driftSince(now)
                .build();
        saveBinding(binding);
        golemRegistryService.updatePolicyBinding(golemId, binding);
        recordAudit("golem.policy_bound", actorId, actorName, golemId, "Golem bound to policy group",
                groupId + " @ v" + binding.getTargetVersion());
        return binding;
    }

    public void unbindGolem(String golemId, String actorId, String actorName) {
        if (findBinding(golemId).isEmpty()) {
            return;
        }
        storagePort.delete(GOLEM_POLICY_BINDINGS_DIR, golemId + ".json");
        golemRegistryService.clearPolicyBinding(golemId);
        recordAudit("golem.policy_unbound", actorId, actorName, golemId, "Golem unbound from policy group", null);
    }

    public GolemPolicyBinding getBinding(String golemId) {
        return findBinding(golemId).orElseThrow(() -> new IllegalArgumentException("Unknown policy binding for golem: "
                + golemId));
    }

    public Optional<GolemPolicyBinding> findBinding(String golemId) {
        String content = storagePort.getText(GOLEM_POLICY_BINDINGS_DIR, golemId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, GolemPolicyBinding.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy binding for golem " + golemId, exception);
        }
    }

    public List<GolemPolicyBinding> listBindingsForPolicyGroup(String groupId) {
        List<GolemPolicyBinding> bindings = new ArrayList<>();
        for (String path : storagePort.listObjects(GOLEM_POLICY_BINDINGS_DIR, "")) {
            Optional<GolemPolicyBinding> binding = loadBinding(path);
            if (binding.isPresent() && groupId.equals(binding.get().getPolicyGroupId())) {
                bindings.add(binding.get());
            }
        }
        bindings.sort(Comparator.comparing(GolemPolicyBinding::getGolemId));
        return bindings;
    }

    public PolicyGroupVersion getTargetVersionForGolem(String golemId) {
        GolemPolicyBinding binding = getBinding(golemId);
        return getVersion(binding.getPolicyGroupId(), binding.getTargetVersion());
    }

    public GolemPolicyBinding recordApplyResult(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String checksum,
            String errorDigest) {
        GolemPolicyBinding binding = getBinding(golemId);
        if (!binding.getPolicyGroupId().equals(policyGroupId)) {
            throw new IllegalArgumentException("Policy group mismatch for golem: " + golemId);
        }
        if (targetVersion == null || targetVersion <= 0) {
            throw new IllegalArgumentException("Target version is required");
        }

        PolicyGroupVersion reportedVersion = getVersion(policyGroupId, targetVersion);
        if (checksum != null && !checksum.isBlank() && !reportedVersion.getChecksum().equals(checksum)) {
            throw new IllegalArgumentException("Policy package checksum mismatch");
        }
        if (appliedVersion != null && appliedVersion > 0) {
            getVersion(policyGroupId, appliedVersion);
        }

        Instant now = Instant.now();
        PolicySyncStatus normalizedStatus = normalizeReportedStatus(binding, targetVersion, appliedVersion,
                reportedStatus);
        binding.setAppliedVersion(appliedVersion);
        if (normalizedStatus == PolicySyncStatus.IN_SYNC
                && appliedVersion != null
                && appliedVersion == binding.getTargetVersion()) {
            binding.setLastAppliedAt(now);
        }
        binding.setSyncStatus(normalizedStatus);

        String normalizedErrorDigest = normalizeOptional(errorDigest);
        binding.setLastErrorDigest(normalizedErrorDigest);
        binding.setLastErrorAt(normalizedErrorDigest != null ? now : null);

        if (binding.getSyncStatus() == PolicySyncStatus.IN_SYNC) {
            binding.setDriftSince(null);
            binding.setLastErrorDigest(null);
            binding.setLastErrorAt(null);
        } else if (binding.getDriftSince() == null) {
            binding.setDriftSince(now);
        }

        saveBinding(binding);
        golemRegistryService.updatePolicyBinding(golemId, binding);
        return binding;
    }

    public Optional<GolemPolicyBinding> recordHeartbeatSyncState(
            String golemId,
            String policyGroupId,
            Integer targetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus,
            String errorDigest) {
        if (policyGroupId == null || targetVersion == null || targetVersion <= 0) {
            return Optional.empty();
        }
        Optional<GolemPolicyBinding> binding = findBinding(golemId);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        if (!policyGroupId.equals(binding.get().getPolicyGroupId())) {
            return Optional.empty();
        }
        return Optional.of(recordApplyResult(
                golemId,
                policyGroupId,
                targetVersion,
                appliedVersion,
                reportedStatus,
                null,
                errorDigest));
    }

    private void markBoundGolemsSyncPending(String groupId, int targetVersion, Instant requestedAt) {
        List<GolemPolicyBinding> bindings = listBindingsForPolicyGroup(groupId);
        for (GolemPolicyBinding binding : bindings) {
            binding.setTargetVersion(targetVersion);
            binding.setSyncStatus(PolicySyncStatus.SYNC_PENDING);
            binding.setLastSyncRequestedAt(requestedAt);
            binding.setDriftSince(requestedAt);
            saveBinding(binding);
            golemRegistryService.updatePolicyBinding(binding.getGolemId(), binding);
        }
    }

    private PolicySyncStatus normalizeReportedStatus(
            GolemPolicyBinding binding,
            int reportedTargetVersion,
            Integer appliedVersion,
            PolicySyncStatus reportedStatus) {
        PolicySyncStatus status = reportedStatus != null ? reportedStatus : PolicySyncStatus.OUT_OF_SYNC;
        if (status == PolicySyncStatus.IN_SYNC
                && reportedTargetVersion == binding.getTargetVersion()
                && appliedVersion != null
                && appliedVersion == binding.getTargetVersion()) {
            return PolicySyncStatus.IN_SYNC;
        }
        if (status == PolicySyncStatus.APPLYING) {
            return PolicySyncStatus.APPLYING;
        }
        if (status == PolicySyncStatus.APPLY_FAILED) {
            return PolicySyncStatus.APPLY_FAILED;
        }
        return PolicySyncStatus.OUT_OF_SYNC;
    }

    private Optional<PolicyGroup> loadPolicyGroup(String path) {
        String content = storagePort.getText(POLICY_GROUPS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, PolicyGroup.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy group " + path, exception);
        }
    }

    private Optional<PolicyGroupVersion> loadPolicyGroupVersion(String path) {
        String content = storagePort.getText(POLICY_GROUP_VERSIONS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, PolicyGroupVersion.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy group version " + path, exception);
        }
    }

    private Optional<GolemPolicyBinding> loadBinding(String path) {
        String content = storagePort.getText(GOLEM_POLICY_BINDINGS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, GolemPolicyBinding.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize policy binding " + path, exception);
        }
    }

    private void savePolicyGroup(PolicyGroup policyGroup) {
        try {
            storagePort.ensureDirectory(POLICY_GROUPS_DIR);
            storagePort.putTextAtomic(POLICY_GROUPS_DIR, policyGroup.getId() + ".json",
                    objectMapper.writeValueAsString(policyGroup));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group " + policyGroup.getId(), exception);
        }
    }

    private void savePolicyGroupVersion(PolicyGroupVersion policyGroupVersion) {
        try {
            storagePort.ensureDirectory(POLICY_GROUP_VERSIONS_DIR);
            storagePort.putTextAtomic(POLICY_GROUP_VERSIONS_DIR,
                    versionPath(policyGroupVersion.getPolicyGroupId(), policyGroupVersion.getVersion()),
                    objectMapper.writeValueAsString(policyGroupVersion));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group version "
                    + policyGroupVersion.getPolicyGroupId() + " v" + policyGroupVersion.getVersion(), exception);
        }
    }

    private void saveBinding(GolemPolicyBinding binding) {
        try {
            storagePort.ensureDirectory(GOLEM_POLICY_BINDINGS_DIR);
            storagePort.putTextAtomic(GOLEM_POLICY_BINDINGS_DIR, binding.getGolemId() + ".json",
                    objectMapper.writeValueAsString(binding));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy binding for golem " + binding.getGolemId(),
                    exception);
        }
    }

    private int nextVersion(String groupId) {
        return listVersions(groupId).stream()
                .map(PolicyGroupVersion::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String versionPath(String groupId, int version) {
        return groupId + "/v" + version + ".json";
    }

    private PolicyGroupSpec normalizeSpec(PolicyGroupSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Policy group spec is required");
        }
        PolicyGroupSpec copy = copy(spec, PolicyGroupSpec.class);
        if (copy.getLlmProviders() == null) {
            copy.setLlmProviders(new java.util.LinkedHashMap<>());
        }
        if (copy.getModelRouter() == null) {
            copy.setModelRouter(new PolicyGroupSpec.PolicyModelRouter());
        }
        if (copy.getModelRouter().getTiers() == null) {
            copy.getModelRouter().setTiers(new java.util.LinkedHashMap<>());
        }
        if (copy.getModelCatalog() == null) {
            copy.setModelCatalog(new PolicyGroupSpec.PolicyModelCatalog());
        }
        if (copy.getModelCatalog().getModels() == null) {
            copy.getModelCatalog().setModels(new java.util.LinkedHashMap<>());
        }
        copy.setChecksum(calculateChecksum(copy));
        return copy;
    }

    private PolicyGroupSpec mergeSecrets(PolicyGroupSpec existingSpec, PolicyGroupSpec nextSpec) {
        if (nextSpec == null) {
            throw new IllegalArgumentException("Policy group spec is required");
        }

        PolicyGroupSpec mergedSpec = copy(nextSpec, PolicyGroupSpec.class);
        if (mergedSpec.getLlmProviders() == null) {
            return mergedSpec;
        }
        if (existingSpec == null || existingSpec.getLlmProviders() == null) {
            return mergedSpec;
        }

        for (java.util.Map.Entry<String, PolicyGroupSpec.PolicyProviderConfig> entry : mergedSpec.getLlmProviders()
                .entrySet()) {
            PolicyGroupSpec.PolicyProviderConfig incomingProvider = entry.getValue();
            PolicyGroupSpec.PolicyProviderConfig existingProvider = existingSpec.getLlmProviders().get(entry.getKey());
            if (incomingProvider == null || existingProvider == null) {
                continue;
            }
            if ((incomingProvider.getApiKey() == null || incomingProvider.getApiKey().isBlank())
                    && existingProvider.getApiKey() != null
                    && !existingProvider.getApiKey().isBlank()) {
                incomingProvider.setApiKey(existingProvider.getApiKey());
            }
        }
        return mergedSpec;
    }

    private <T> T copy(T source, Class<T> type) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(source), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to copy policy payload", exception);
        }
    }

    private String calculateChecksum(PolicyGroupSpec spec) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(objectMapper.writeValueAsString(spec).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group spec for checksum", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Policy group slug is required");
        }
    }

    private void ensureSlugAvailable(String slug) {
        String normalizedSlug = slug.trim();
        boolean exists = listPolicyGroups().stream()
                .anyMatch(policyGroup -> normalizedSlug.equals(policyGroup.getSlug()));
        if (exists) {
            throw new IllegalArgumentException("Policy group slug already exists: " + normalizedSlug);
        }
    }

    private String normalizeName(String slug, String name) {
        if (name == null || name.isBlank()) {
            return slug.trim();
        }
        return name.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void recordAudit(
            String eventType,
            String actorId,
            String actorName,
            String targetId,
            String summary,
            String details) {
        auditService.record(AuditEvent.builder()
                .eventType(eventType)
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("POLICY")
                .targetId(targetId)
                .summary(summary)
                .details(details));
    }
}
