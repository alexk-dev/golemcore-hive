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
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemCapabilitySnapshot;
import me.golemcore.hive.domain.model.GolemPolicyBinding;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.domain.model.HeartbeatPing;
import me.golemcore.hive.fleet.application.MachineTokenPair;
import me.golemcore.hive.fleet.application.RegistrationResult;
import me.golemcore.hive.fleet.application.port.in.GolemEnrollmentUseCase;
import me.golemcore.hive.fleet.application.port.in.GolemFleetUseCase;
import me.golemcore.hive.domain.model.PolicySyncStatus;
import me.golemcore.hive.domain.service.PolicyGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final GolemEnrollmentUseCase golemEnrollmentUseCase;
    private final GolemFleetUseCase golemFleetUseCase;
    private final PolicyGroupService policyGroupService;
    private final HiveProperties properties;

    @PostMapping("/register")
    public Mono<ResponseEntity<GolemAuthResponse>> registerGolem(@Valid @RequestBody RegisterGolemRequest request) {
        return Mono.fromCallable(() -> {
            RegistrationResult result = golemEnrollmentUseCase.registerGolem(
                    request.enrollmentToken(),
                    request.displayName(),
                    request.hostLabel(),
                    request.runtimeVersion(),
                    request.buildVersion(),
                    request.supportedChannels(),
                    toCapabilitySnapshot(request.capabilities()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toAuthResponse(result.golem(), result.tokens()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}/auth:rotate")
    public Mono<ResponseEntity<GolemAuthResponse>> rotateMachineToken(
            @PathVariable String golemId,
            @Valid @RequestBody GolemTokenRotateRequest request) {
        return Mono.fromCallable(() -> {
            MachineTokenPair tokens = golemEnrollmentUseCase.rotateMachineTokens(golemId,
                    request.refreshToken());
            Golem golem = golemFleetUseCase.findGolem(golemId).orElse(null);
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
            ControllerActorSupport.requireOperatorActor(principal);
            List<GolemSummaryResponse> response = golemFleetUseCase.listGolems(query, state, role).stream()
                    .map(this::toSummaryResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{golemId}")
    public Mono<ResponseEntity<GolemDetailsResponse>> getGolem(Principal principal, @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            Golem golem = golemFleetUseCase.findGolem(golemId)
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
            ControllerActorSupport.requireGolemScope(principal, golemId, GolemScope.HEARTBEAT.value());
            if (request != null && hasPolicySyncState(request)) {
                ControllerActorSupport.requireGolemScope(principal, golemId, GolemScope.POLICY_WRITE.value());
            }
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
            Golem golem = golemFleetUseCase.updateHeartbeat(golemId, heartbeatPing);
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
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Golem golem = golemFleetUseCase.pauseGolem(golemId, request != null ? request.reason() : null);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}:resume")
    public Mono<ResponseEntity<GolemDetailsResponse>> resumeGolem(Principal principal, @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Golem golem = golemFleetUseCase.resumeGolem(golemId);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{golemId}:revoke")
    public Mono<ResponseEntity<GolemDetailsResponse>> revokeGolem(
            Principal principal,
            @PathVariable String golemId,
            @RequestBody(required = false) ActionReasonRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Golem golem = golemFleetUseCase.revokeGolem(golemId, request != null ? request.reason() : null);
            return ResponseEntity.ok(toDetailsResponse(golem));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private GolemAuthResponse toAuthResponse(Golem golem, MachineTokenPair tokens) {
        return new GolemAuthResponse(
                golem.getId(),
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshTokenExpiresAt(),
                properties.getSecurity().getJwt().getIssuer(),
                properties.getSecurity().getJwt().getGolemAudience(),
                golem.getControlChannelUrl(),
                golem.getHeartbeatIntervalSeconds(),
                tokens.scopes());
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

    private boolean hasPolicySyncState(HeartbeatRequest request) {
        return request.policyGroupId() != null
                || request.targetPolicyVersion() != null
                || request.appliedPolicyVersion() != null
                || request.syncStatus() != null
                || request.lastPolicyErrorDigest() != null;
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

}
