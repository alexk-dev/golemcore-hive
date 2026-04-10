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

package me.golemcore.hive.governance.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.PolicyGroup;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.domain.model.PolicyGroupVersion;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase;
import me.golemcore.hive.governance.application.port.out.PolicyBindingRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicyGolemPort;
import me.golemcore.hive.governance.application.port.out.PolicyGroupRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicyGroupVersionRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicySpecCodecPort;

public class PolicyGroupAdministrationApplicationService implements PolicyGroupAdministrationUseCase {

    private final PolicyGroupRepositoryPort policyGroupRepositoryPort;
    private final PolicyGroupVersionRepositoryPort policyGroupVersionRepositoryPort;
    private final PolicyBindingRepositoryPort policyBindingRepositoryPort;
    private final PolicyGolemPort policyGolemPort;
    private final PolicySpecCodecPort policySpecCodecPort;
    private final AuditLogUseCase auditLogUseCase;

    public PolicyGroupAdministrationApplicationService(
            PolicyGroupRepositoryPort policyGroupRepositoryPort,
            PolicyGroupVersionRepositoryPort policyGroupVersionRepositoryPort,
            PolicyBindingRepositoryPort policyBindingRepositoryPort,
            PolicyGolemPort policyGolemPort,
            PolicySpecCodecPort policySpecCodecPort,
            AuditLogUseCase auditLogUseCase) {
        this.policyGroupRepositoryPort = policyGroupRepositoryPort;
        this.policyGroupVersionRepositoryPort = policyGroupVersionRepositoryPort;
        this.policyBindingRepositoryPort = policyBindingRepositoryPort;
        this.policyGolemPort = policyGolemPort;
        this.policySpecCodecPort = policySpecCodecPort;
        this.auditLogUseCase = auditLogUseCase;
    }

    @Override
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
        policyGroupRepositoryPort.save(policyGroup);
        recordAudit("policy_group.created", actorId, actorName, policyGroup.getId(), "Policy group created",
                policyGroup.getSlug());
        return policyGroup;
    }

    @Override
    public List<PolicyGroup> listPolicyGroups() {
        List<PolicyGroup> policyGroups = new ArrayList<>(policyGroupRepositoryPort.list());
        policyGroups
                .sort(Comparator.comparing(PolicyGroup::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PolicyGroup::getSlug));
        return policyGroups;
    }

    @Override
    public PolicyGroup getPolicyGroup(String groupId) {
        return policyGroupRepositoryPort.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy group: " + groupId));
    }

    @Override
    public PolicyGroup updateDraft(String groupId, PolicyGroupSpec draftSpec) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        PolicyGroupSpec mergedDraftSpec = mergeSecrets(policyGroup.getDraftSpec(), draftSpec);
        PolicyGroupSpec normalizedDraftSpec = normalizeSpec(mergedDraftSpec);
        policyGroup.setDraftSpec(normalizedDraftSpec);
        policyGroup.setUpdatedAt(Instant.now());
        policyGroupRepositoryPort.save(policyGroup);
        return policyGroup;
    }

    @Override
    public PolicyGroupVersion publish(String groupId, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        if (policyGroup.getDraftSpec() == null) {
            throw new IllegalArgumentException("Policy group draft is required before publish");
        }
        PolicyGroupSpec specSnapshot = normalizeSpec(policySpecCodecPort.copy(policyGroup.getDraftSpec()));
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
        policyGroupVersionRepositoryPort.save(policyGroupVersion);

        policyGroup.setCurrentVersion(version);
        policyGroup.setStatus("ACTIVE");
        policyGroup.setUpdatedAt(now);
        policyGroup.setLastPublishedAt(now);
        policyGroup.setLastPublishedBy(actorId);
        policyGroup.setLastPublishedByName(actorName);
        policyGroupRepositoryPort.save(policyGroup);

        markBoundGolemsSyncPending(groupId, version, now);
        recordAudit("policy_group.published", actorId, actorName, groupId, "Policy group published",
                "v" + version + ": " + normalizeOptional(changeSummary));
        return policyGroupVersion;
    }

    @Override
    public List<PolicyGroupVersion> listVersions(String groupId) {
        List<PolicyGroupVersion> versions = new ArrayList<>(policyGroupVersionRepositoryPort.listByGroupId(groupId));
        versions.sort(Comparator.comparingInt(PolicyGroupVersion::getVersion).reversed());
        return versions;
    }

    @Override
    public PolicyGroupVersion getVersion(String groupId, int version) {
        return policyGroupVersionRepositoryPort.findByGroupIdAndVersion(groupId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown policy group version: " + groupId + " v" + version));
    }

    @Override
    public PolicyGroup rollback(String groupId, int version, String changeSummary, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        getVersion(groupId, version);
        Instant now = Instant.now();
        policyGroup.setCurrentVersion(version);
        policyGroup.setUpdatedAt(now);
        policyGroup.setLastPublishedAt(now);
        policyGroup.setLastPublishedBy(actorId);
        policyGroup.setLastPublishedByName(actorName);
        policyGroupRepositoryPort.save(policyGroup);
        markBoundGolemsSyncPending(groupId, version, now);
        recordAudit("policy_group.rolled_back", actorId, actorName, groupId, "Policy group rolled back",
                "v" + version + ": " + normalizeOptional(changeSummary));
        return policyGroup;
    }

    @Override
    public GolemPolicyBinding bindGolem(String golemId, String groupId, String actorId, String actorName) {
        PolicyGroup policyGroup = getPolicyGroup(groupId);
        if (policyGroup.getCurrentVersion() <= 0) {
            throw new IllegalStateException("Policy group must be published before binding golems");
        }
        policyGolemPort.findGolem(golemId)
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
        policyBindingRepositoryPort.save(binding);
        policyGolemPort.updatePolicyBinding(golemId, binding);
        recordAudit("golem.policy_bound", actorId, actorName, golemId, "Golem bound to policy group",
                groupId + " @ v" + binding.getTargetVersion());
        return binding;
    }

    @Override
    public void unbindGolem(String golemId, String actorId, String actorName) {
        if (findBinding(golemId).isEmpty()) {
            return;
        }
        policyBindingRepositoryPort.deleteByGolemId(golemId);
        policyGolemPort.clearPolicyBinding(golemId);
        recordAudit("golem.policy_unbound", actorId, actorName, golemId, "Golem unbound from policy group", null);
    }

    @Override
    public GolemPolicyBinding getBinding(String golemId) {
        return findBinding(golemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown policy binding for golem: " + golemId));
    }

    @Override
    public Optional<GolemPolicyBinding> findBinding(String golemId) {
        return policyBindingRepositoryPort.findByGolemId(golemId);
    }

    @Override
    public List<GolemPolicyBinding> listBindingsForPolicyGroup(String groupId) {
        return policyBindingRepositoryPort.list().stream()
                .filter(binding -> groupId.equals(binding.getPolicyGroupId()))
                .sorted(Comparator.comparing(GolemPolicyBinding::getGolemId))
                .toList();
    }

    @Override
    public int countBindingsForPolicyGroup(String groupId) {
        return listBindingsForPolicyGroup(groupId).size();
    }

    @Override
    public PolicyGroupVersion getTargetVersionForGolem(String golemId) {
        GolemPolicyBinding binding = getBinding(golemId);
        return getVersion(binding.getPolicyGroupId(), binding.getTargetVersion());
    }

    @Override
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

        policyBindingRepositoryPort.save(binding);
        policyGolemPort.updatePolicyBinding(golemId, binding);
        return binding;
    }

    @Override
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
            policyBindingRepositoryPort.save(binding);
            policyGolemPort.updatePolicyBinding(binding.getGolemId(), binding);
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

    private int nextVersion(String groupId) {
        return listVersions(groupId).stream()
                .map(PolicyGroupVersion::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private PolicyGroupSpec normalizeSpec(PolicyGroupSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Policy group spec is required");
        }
        PolicyGroupSpec copy = policySpecCodecPort.copy(spec);
        if (copy.getLlmProviders() == null) {
            copy.setLlmProviders(new LinkedHashMap<>());
        }
        if (copy.getModelRouter() == null) {
            copy.setModelRouter(new PolicyGroupSpec.PolicyModelRouter());
        }
        if (copy.getModelRouter().getTiers() == null) {
            copy.getModelRouter().setTiers(new LinkedHashMap<>());
        }
        if (copy.getModelCatalog() == null) {
            copy.setModelCatalog(new PolicyGroupSpec.PolicyModelCatalog());
        }
        if (copy.getModelCatalog().getModels() == null) {
            copy.getModelCatalog().setModels(new LinkedHashMap<>());
        }
        if (copy.getTools() == null) {
            copy.setTools(new PolicyGroupSpec.PolicyToolsConfig());
        }
        if (copy.getTools().getShellEnvironmentVariables() == null) {
            copy.getTools().setShellEnvironmentVariables(new ArrayList<>());
        }
        if (copy.getMemory() == null) {
            copy.setMemory(new PolicyGroupSpec.PolicyMemoryConfig());
        }
        if (copy.getMemory().getDisclosure() == null) {
            copy.getMemory().setDisclosure(new PolicyGroupSpec.PolicyMemoryDisclosureConfig());
        }
        if (copy.getMemory().getReranking() == null) {
            copy.getMemory().setReranking(new PolicyGroupSpec.PolicyMemoryRerankingConfig());
        }
        if (copy.getMemory().getDiagnostics() == null) {
            copy.getMemory().setDiagnostics(new PolicyGroupSpec.PolicyMemoryDiagnosticsConfig());
        }
        if (copy.getMcp() == null) {
            copy.setMcp(new PolicyGroupSpec.PolicyMcpConfig());
        }
        if (copy.getMcp().getCatalog() == null) {
            copy.getMcp().setCatalog(new ArrayList<>());
        }
        for (PolicyGroupSpec.PolicyMcpCatalogEntry entry : copy.getMcp().getCatalog()) {
            if (entry != null && entry.getEnv() == null) {
                entry.setEnv(new LinkedHashMap<>());
            }
        }
        if (copy.getAutonomy() == null) {
            copy.setAutonomy(new PolicyGroupSpec.PolicyAutonomyConfig());
        }
        if (copy.getSdlc() == null) {
            copy.setSdlc(new PolicyGroupSpec.PolicySdlcConfig());
        }
        if (copy.getSdlc().getAllowedCardKinds() == null) {
            copy.getSdlc().setAllowedCardKinds(new ArrayList<>());
        }
        copy.setChecksum(policySpecCodecPort.calculateChecksum(copy));
        return copy;
    }

    private PolicyGroupSpec mergeSecrets(PolicyGroupSpec existingSpec, PolicyGroupSpec nextSpec) {
        if (nextSpec == null) {
            throw new IllegalArgumentException("Policy group spec is required");
        }

        PolicyGroupSpec mergedSpec = policySpecCodecPort.copy(nextSpec);
        if (existingSpec == null) {
            return mergedSpec;
        }

        mergeProviderSecrets(existingSpec, mergedSpec);
        mergeToolSecrets(existingSpec, mergedSpec);
        mergeMcpSecrets(existingSpec, mergedSpec);
        return mergedSpec;
    }

    private void mergeProviderSecrets(PolicyGroupSpec existingSpec, PolicyGroupSpec mergedSpec) {
        if (mergedSpec.getLlmProviders() == null || existingSpec.getLlmProviders() == null) {
            return;
        }

        for (Map.Entry<String, PolicyGroupSpec.PolicyProviderConfig> entry : mergedSpec.getLlmProviders().entrySet()) {
            PolicyGroupSpec.PolicyProviderConfig incomingProvider = entry.getValue();
            PolicyGroupSpec.PolicyProviderConfig existingProvider = existingSpec.getLlmProviders().get(entry.getKey());
            if (incomingProvider != null
                    && existingProvider != null
                    && isBlank(incomingProvider.getApiKey())
                    && !isBlank(existingProvider.getApiKey())) {
                incomingProvider.setApiKey(existingProvider.getApiKey());
            }
        }
    }

    private void mergeToolSecrets(PolicyGroupSpec existingSpec, PolicyGroupSpec mergedSpec) {
        if (mergedSpec.getTools() == null
                || mergedSpec.getTools().getShellEnvironmentVariables() == null
                || existingSpec.getTools() == null
                || existingSpec.getTools().getShellEnvironmentVariables() == null) {
            return;
        }

        Map<String, PolicyGroupSpec.PolicyEnvironmentVariable> existingVariables = new LinkedHashMap<>();
        for (PolicyGroupSpec.PolicyEnvironmentVariable variable : existingSpec.getTools()
                .getShellEnvironmentVariables()) {
            if (variable != null && variable.getName() != null) {
                existingVariables.put(variable.getName(), variable);
            }
        }
        for (PolicyGroupSpec.PolicyEnvironmentVariable variable : mergedSpec.getTools()
                .getShellEnvironmentVariables()) {
            if (variable == null || variable.getName() == null || !isBlank(variable.getValue())) {
                continue;
            }
            PolicyGroupSpec.PolicyEnvironmentVariable existingVariable = existingVariables.get(variable.getName());
            if (existingVariable != null && !isBlank(existingVariable.getValue())) {
                variable.setValue(existingVariable.getValue());
            }
        }
    }

    private void mergeMcpSecrets(PolicyGroupSpec existingSpec, PolicyGroupSpec mergedSpec) {
        if (mergedSpec.getMcp() == null
                || mergedSpec.getMcp().getCatalog() == null
                || existingSpec.getMcp() == null
                || existingSpec.getMcp().getCatalog() == null) {
            return;
        }

        Map<String, PolicyGroupSpec.PolicyMcpCatalogEntry> existingEntries = new LinkedHashMap<>();
        for (PolicyGroupSpec.PolicyMcpCatalogEntry entry : existingSpec.getMcp().getCatalog()) {
            if (entry != null && entry.getName() != null) {
                existingEntries.put(entry.getName(), entry);
            }
        }
        for (PolicyGroupSpec.PolicyMcpCatalogEntry entry : mergedSpec.getMcp().getCatalog()) {
            if (entry == null || entry.getName() == null) {
                continue;
            }
            mergeMcpEntrySecrets(existingEntries.get(entry.getName()), entry);
        }
    }

    private void mergeMcpEntrySecrets(
            PolicyGroupSpec.PolicyMcpCatalogEntry existingEntry,
            PolicyGroupSpec.PolicyMcpCatalogEntry incomingEntry) {
        if (existingEntry == null || existingEntry.getEnv() == null) {
            return;
        }
        if (incomingEntry.getEnv() == null) {
            incomingEntry.setEnv(new LinkedHashMap<>(existingEntry.getEnv()));
            return;
        }

        for (Map.Entry<String, String> envEntry : incomingEntry.getEnv().entrySet()) {
            String existingValue = existingEntry.getEnv().get(envEntry.getKey());
            if (isBlank(envEntry.getValue()) && !isBlank(existingValue)) {
                envEntry.setValue(existingValue);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
        auditLogUseCase.record(AuditEvent.builder()
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
