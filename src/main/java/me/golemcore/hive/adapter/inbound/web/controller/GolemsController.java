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
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.golems.ActionReasonRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemAuthResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemCapabilitySnapshotRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemDetailsResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemPolicyBindingResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemSummaryResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.GolemTokenRotateRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.HeartbeatRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.RegisterGolemRequest;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.domain.service.EnrollmentService;
import me.golemcore.hive.domain.service.GolemRegistryService;
import me.golemcore.hive.domain.service.PolicyGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/golems")
@RequiredArgsConstructor
public class GolemsController {

    private final EnrollmentService enrollmentService;
    private final GolemRegistryService golemRegistryService;
    private final PolicyGroupService policyGroupService;
    private final HiveProperties properties;

    @PostMapping("/register")
    public Mono<ResponseEntity<GolemAuthResponse>> registerGolem(@Valid @RequestBody RegisterGolemRequest request) {
        return Mono.fromCallable(() -> {
            EnrollmentService.RegistrationResult result = enrollmentService.registerGolem(
                    request.enrollmentToken(),
                    request.displayName(),
                    request.hostLabel(),
                    request.runtimeVersion(),
                    request.buildVersion(),
                    request.supportedChannels(),
                    toCapabilitySnapshot(request.capabilities()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toAuthResponse(result.getGolem(), result.getTokens()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}/auth:rotate")
    public Mono<ResponseEntity<GolemAuthResponse>> rotateMachineToken(
            @PathVariable String golemId,
            @Valid @RequestBody GolemTokenRotateRequest request) {
        return Mono.fromCallable(() -> {
            EnrollmentService.MachineTokenPair tokens = enrollmentService.rotateMachineTokens(golemId,
                    request.refreshToken());
            Golem golem = golemRegistryService.findGolem(golemId).orElse(null);
            if (tokens == null || golem == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
            }
            return ResponseEntity.ok(toAuthResponse(golem, tokens));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public Mono<ResponseEntity<List<GolemSummaryResponse>>> listGolems(
            Principal principal,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String role) {
        return Mono.fromCallable(() -> {
            requireOperatorActor(principal);
            List<GolemSummaryResponse> response = golemRegistryService.listGolems(query, state, role).stream()
                    .map(this::toSummaryResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{golemId}")
    public Mono<ResponseEntity<GolemDetailsResponse>> getGolem(Principal principal, @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            requireOperatorActor(principal);
            Golem golem = golemRegistryService.findGolem(golemId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown golem"));
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}/heartbeat")
    public Mono<ResponseEntity<GolemDetailsResponse>> heartbeat(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody(required = false) HeartbeatRequest request) {
        return Mono.fromCallable(() -> {
            requireGolemScope(principal, golemId, "golems:heartbeat");
            HeartbeatPing heartbeatPing = HeartbeatPing.builder()
                    .golemId(golemId)
                    .receivedAt(Instant.now())
                    .status(request != null ? request.status() : null)
                    .currentRunState(request != null ? request.currentRunState() : null)
                    .currentCardId(request != null ? request.currentCardId() : null)
                    .currentThreadId(request != null ? request.currentThreadId() : null)
                    .modelTier(request != null ? request.modelTier() : null)
                    .inputTokens(request != null && request.inputTokens() != null ? request.inputTokens() : 0L)
                    .outputTokens(request != null && request.outputTokens() != null ? request.outputTokens() : 0L)
                    .accumulatedCostMicros(request != null && request.accumulatedCostMicros() != null
                            ? request.accumulatedCostMicros()
                            : 0L)
                    .queueDepth(request != null && request.queueDepth() != null ? request.queueDepth() : 0)
                    .healthSummary(request != null ? request.healthSummary() : null)
                    .lastErrorSummary(request != null ? request.lastErrorSummary() : null)
                    .uptimeSeconds(request != null && request.uptimeSeconds() != null ? request.uptimeSeconds() : 0L)
                    .capabilitySnapshotHash(request != null ? request.capabilitySnapshotHash() : null)
                    .policyGroupId(request != null ? request.policyGroupId() : null)
                    .targetPolicyVersion(request != null ? request.targetPolicyVersion() : null)
                    .appliedPolicyVersion(request != null ? request.appliedPolicyVersion() : null)
                    .syncStatus(request != null ? request.syncStatus() : null)
                    .lastPolicyErrorDigest(request != null ? request.lastPolicyErrorDigest() : null)
                    .build();
            Golem golem = golemRegistryService.updateHeartbeat(golemId, heartbeatPing);
            if (request != null) {
                policyGroupService.recordHeartbeatSyncState(
                        golemId,
                        request.policyGroupId(),
                        request.targetPolicyVersion(),
                        request.appliedPolicyVersion(),
                        parsePolicySyncStatus(request.syncStatus()),
                        request.lastPolicyErrorDigest())
                        .ifPresent(golem::setPolicyBinding);
            }
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}:pause")
    public Mono<ResponseEntity<GolemDetailsResponse>> pauseGolem(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody(required = false) ActionReasonRequest request) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            Golem golem = golemRegistryService.pauseGolem(golemId, request != null ? request.reason() : null);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}:resume")
    public Mono<ResponseEntity<GolemDetailsResponse>> resumeGolem(Principal principal, @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            Golem golem = golemRegistryService.resumeGolem(golemId);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}:revoke")
    public Mono<ResponseEntity<GolemDetailsResponse>> revokeGolem(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody(required = false) ActionReasonRequest request) {
        return Mono.fromCallable(() -> {
            requirePrivilegedOperator(principal);
            Golem golem = golemRegistryService.revokeGolem(golemId, request != null ? request.reason() : null);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private GolemAuthResponse toAuthResponse(Golem golem, EnrollmentService.MachineTokenPair tokens) {
        return new GolemAuthResponse(
                golem.getId(),
                tokens.getAccessToken(),
                tokens.getRefreshToken(),
                tokens.getAccessTokenExpiresAt(),
                tokens.getRefreshTokenExpiresAt(),
                properties.getSecurity().getJwt().getIssuer(),
                properties.getSecurity().getJwt().getGolemAudience(),
                golem.getControlChannelUrl(),
                golem.getHeartbeatIntervalSeconds(),
                tokens.getScopes());
    }

    private GolemSummaryResponse toSummaryResponse(Golem golem) {
        return new GolemSummaryResponse(
                golem.getId(),
                golem.getDisplayName(),
                golem.getHostLabel(),
                golem.getRuntimeVersion(),
                golem.getState().name(),
                golem.getLastHeartbeatAt(),
                golem.getLastSeenAt(),
                golem.getMissedHeartbeatCount(),
                golem.getRoleBindings().stream().map(binding -> binding.getRoleSlug()).sorted().toList(),
                toPolicyBindingResponse(golem.getPolicyBinding()));
    }

    private GolemDetailsResponse toDetailsResponse(Golem golem) {
        return new GolemDetailsResponse(
                golem.getId(),
                golem.getDisplayName(),
                golem.getHostLabel(),
                golem.getRuntimeVersion(),
                golem.getBuildVersion(),
                golem.getState().name(),
                golem.getRegisteredAt(),
                golem.getCreatedAt(),
                golem.getUpdatedAt(),
                golem.getLastHeartbeatAt(),
                golem.getLastSeenAt(),
                golem.getLastStateChangeAt(),
                golem.getHeartbeatIntervalSeconds(),
                golem.getMissedHeartbeatCount(),
                golem.getPauseReason(),
                golem.getRevokeReason(),
                golem.getControlChannelUrl(),
                golem.getSupportedChannels(),
                toCapabilitiesRequest(golem.getCapabilitySnapshot()),
                toHeartbeatRequest(golem.getLastHeartbeat()),
                golem.getRoleBindings().stream().map(binding -> binding.getRoleSlug()).sorted().toList(),
                toPolicyBindingResponse(golem.getPolicyBinding()));
    }

    private GolemCapabilitySnapshot toCapabilitySnapshot(GolemCapabilitySnapshotRequest request) {
        if (request == null) {
            return null;
        }
        return GolemCapabilitySnapshot.builder()
                .providers(
                        request.providers() != null ? new LinkedHashSet<>(request.providers()) : new LinkedHashSet<>())
                .modelFamilies(request.modelFamilies() != null ? new LinkedHashSet<>(request.modelFamilies())
                        : new LinkedHashSet<>())
                .enabledTools(request.enabledTools() != null ? new LinkedHashSet<>(request.enabledTools())
                        : new LinkedHashSet<>())
                .enabledAutonomyFeatures(request.enabledAutonomyFeatures() != null
                        ? new LinkedHashSet<>(request.enabledAutonomyFeatures())
                        : new LinkedHashSet<>())
                .capabilityTags(request.capabilityTags() != null ? new LinkedHashSet<>(request.capabilityTags())
                        : new LinkedHashSet<>())
                .supportedChannels(
                        request.supportedChannels() != null ? new LinkedHashSet<>(request.supportedChannels())
                                : new LinkedHashSet<>())
                .snapshotHash(request.snapshotHash())
                .defaultModel(request.defaultModel())
                .build();
    }

    private GolemCapabilitySnapshotRequest toCapabilitiesRequest(GolemCapabilitySnapshot capabilitySnapshot) {
        if (capabilitySnapshot == null) {
            return null;
        }
        return new GolemCapabilitySnapshotRequest(
                capabilitySnapshot.getProviders(),
                capabilitySnapshot.getModelFamilies(),
                capabilitySnapshot.getEnabledTools(),
                capabilitySnapshot.getEnabledAutonomyFeatures(),
                capabilitySnapshot.getCapabilityTags(),
                capabilitySnapshot.getSupportedChannels(),
                capabilitySnapshot.getSnapshotHash(),
                capabilitySnapshot.getDefaultModel());
    }

    private HeartbeatRequest toHeartbeatRequest(HeartbeatPing heartbeatPing) {
        if (heartbeatPing == null) {
            return null;
        }
        return new HeartbeatRequest(
                heartbeatPing.getStatus(),
                heartbeatPing.getCurrentRunState(),
                heartbeatPing.getCurrentCardId(),
                heartbeatPing.getCurrentThreadId(),
                heartbeatPing.getModelTier(),
                heartbeatPing.getInputTokens(),
                heartbeatPing.getOutputTokens(),
                heartbeatPing.getAccumulatedCostMicros(),
                heartbeatPing.getQueueDepth(),
                heartbeatPing.getHealthSummary(),
                heartbeatPing.getLastErrorSummary(),
                heartbeatPing.getUptimeSeconds(),
                heartbeatPing.getCapabilitySnapshotHash(),
                heartbeatPing.getPolicyGroupId(),
                heartbeatPing.getTargetPolicyVersion(),
                heartbeatPing.getAppliedPolicyVersion(),
                heartbeatPing.getSyncStatus(),
                heartbeatPing.getLastPolicyErrorDigest());
    }

    private GolemPolicyBindingResponse toPolicyBindingResponse(GolemPolicyBinding policyBinding) {
        if (policyBinding == null) {
            return null;
        }
        return GolemPolicyController.toBindingResponse(policyBinding);
    }

    private PolicySyncStatus parsePolicySyncStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return PolicySyncStatus.valueOf(rawStatus.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown policy sync status: " + rawStatus);
        }
    }

    private AuthenticatedActor requireOperatorActor(Principal principal) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isOperator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator token required");
        }
        return actor;
    }

    private AuthenticatedActor requirePrivilegedOperator(Principal principal) {
        AuthenticatedActor actor = requireOperatorActor(principal);
        if (!actor.hasAnyRole(List.of("ADMIN", "OPERATOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or operator role required");
        }
        return actor;
    }

    private AuthenticatedActor requireGolemScope(Principal principal, String golemId, String scope) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isGolem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Golem token required");
        }
        if (!golemId.equals(actor.getSubjectId()) || !actor.hasScope(scope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Scope denied");
        }
        return actor;
    }

    private AuthenticatedActor extractActor(Principal principal) {
        if (principal instanceof AuthenticatedActor actor) {
            return actor;
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return actor;
        }
        return null;
    }
}
