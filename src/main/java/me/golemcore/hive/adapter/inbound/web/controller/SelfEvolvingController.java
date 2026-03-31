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

import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingCampaignResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingCandidateResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingLineageResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingRunResponse;
import me.golemcore.hive.domain.model.SelfEvolvingCampaignProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingRunProjection;
import me.golemcore.hive.domain.service.SelfEvolvingProjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/self-evolving/golems/{golemId}")
@RequiredArgsConstructor
public class SelfEvolvingController {

    private final SelfEvolvingProjectionService selfEvolvingProjectionService;

    @GetMapping("/runs")
    public Mono<ResponseEntity<List<SelfEvolvingRunResponse>>> listRuns(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.listRuns(golemId).stream()
                    .map(this::toRunResponse)
                    .toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/runs/{runId}")
    public Mono<ResponseEntity<SelfEvolvingRunResponse>> getRun(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String runId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingRunProjection projection = selfEvolvingProjectionService.findRun(golemId, runId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SelfEvolving run not found"));
            return ResponseEntity.ok(toRunResponse(projection));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/candidates")
    public Mono<ResponseEntity<List<SelfEvolvingCandidateResponse>>> listCandidates(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.listCandidates(golemId).stream()
                    .map(this::toCandidateResponse)
                    .toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/benchmarks/campaigns")
    public Mono<ResponseEntity<List<SelfEvolvingCampaignResponse>>> listCampaigns(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.listCampaigns(golemId).stream()
                    .map(this::toCampaignResponse)
                    .toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/lineage")
    public Mono<ResponseEntity<SelfEvolvingLineageResponse>> getLineage(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(SelfEvolvingLineageResponse.builder()
                    .golemId(golemId)
                    .nodes(selfEvolvingProjectionService.listLineage(golemId))
                    .build());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private SelfEvolvingRunResponse toRunResponse(SelfEvolvingRunProjection projection) {
        return SelfEvolvingRunResponse.builder()
                .id(projection.getId())
                .golemId(projection.getGolemId())
                .sessionId(projection.getSessionId())
                .traceId(projection.getTraceId())
                .artifactBundleId(projection.getArtifactBundleId())
                .artifactBundleStatus(projection.getArtifactBundleStatus())
                .status(projection.getStatus())
                .outcomeStatus(projection.getOutcomeStatus())
                .processStatus(projection.getProcessStatus())
                .promotionRecommendation(projection.getPromotionRecommendation())
                .outcomeSummary(projection.getOutcomeSummary())
                .processSummary(projection.getProcessSummary())
                .confidence(projection.getConfidence())
                .processFindings(projection.getProcessFindings())
                .startedAt(projection.getStartedAt())
                .completedAt(projection.getCompletedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }

    private SelfEvolvingCandidateResponse toCandidateResponse(SelfEvolvingCandidateProjection projection) {
        return SelfEvolvingCandidateResponse.builder()
                .id(projection.getId())
                .golemId(projection.getGolemId())
                .goal(projection.getGoal())
                .artifactType(projection.getArtifactType())
                .status(projection.getStatus())
                .riskLevel(projection.getRiskLevel())
                .expectedImpact(projection.getExpectedImpact())
                .sourceRunIds(projection.getSourceRunIds())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }

    private SelfEvolvingCampaignResponse toCampaignResponse(SelfEvolvingCampaignProjection projection) {
        return SelfEvolvingCampaignResponse.builder()
                .id(projection.getId())
                .golemId(projection.getGolemId())
                .suiteId(projection.getSuiteId())
                .baselineBundleId(projection.getBaselineBundleId())
                .candidateBundleId(projection.getCandidateBundleId())
                .status(projection.getStatus())
                .runIds(projection.getRunIds())
                .startedAt(projection.getStartedAt())
                .completedAt(projection.getCompletedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }
}
