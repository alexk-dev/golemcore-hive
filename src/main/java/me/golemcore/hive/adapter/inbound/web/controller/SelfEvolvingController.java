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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingCampaignResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingCandidateResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingLineageResponse;
import me.golemcore.hive.adapter.inbound.web.dto.selfevolving.SelfEvolvingRunResponse;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCatalogProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCompareProjection;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactLineageProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCampaignProjection;
import me.golemcore.hive.domain.model.SelfEvolvingCandidateProjection;
import me.golemcore.hive.domain.model.SelfEvolvingRunProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticSearchExplanationProjection;
import me.golemcore.hive.domain.model.SelfEvolvingTacticSearchStatusProjection;
import me.golemcore.hive.domain.service.SelfEvolvingProjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/self-evolving")
@RequiredArgsConstructor
public class SelfEvolvingController {

    private final SelfEvolvingProjectionService selfEvolvingProjectionService;

    @GetMapping("/golems/{golemId}/runs")
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

    @GetMapping("/golems/{golemId}/runs/{runId}")
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

    @GetMapping("/golems/{golemId}/candidates")
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

    @GetMapping("/golems/{golemId}/benchmarks/campaigns")
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

    @GetMapping("/golems/{golemId}/lineage")
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

    @GetMapping("/golems/{golemId}/artifacts")
    public Mono<ResponseEntity<List<SelfEvolvingArtifactCatalogProjection>>> listArtifacts(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.listArtifacts(golemId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics")
    public Mono<ResponseEntity<List<SelfEvolvingTacticProjection>>> listTactics(
            Principal principal,
            @PathVariable String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.listTactics(golemId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchTactics(
            Principal principal,
            @PathVariable String golemId,
            @RequestParam(required = false) String q) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("query", q);
            payload.put("status", selfEvolvingProjectionService.getTacticSearchStatus(golemId, q).orElse(null));
            payload.put("results", selfEvolvingProjectionService.searchTactics(golemId, q));
            return ResponseEntity.ok(payload);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/search-status")
    public Mono<ResponseEntity<SelfEvolvingTacticSearchStatusProjection>> getTacticSearchStatus(
            Principal principal,
            @PathVariable String golemId,
            @RequestParam(required = false) String q) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingTacticSearchStatusProjection projection = selfEvolvingProjectionService
                    .getTacticSearchStatus(golemId, q)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic search status not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/{tacticId}")
    public Mono<ResponseEntity<SelfEvolvingTacticProjection>> getTactic(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String tacticId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingTacticProjection projection = selfEvolvingProjectionService.findTactic(golemId, tacticId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/{tacticId}/explanation")
    public Mono<ResponseEntity<SelfEvolvingTacticSearchExplanationProjection>> getTacticExplanation(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String tacticId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingTacticProjection projection = selfEvolvingProjectionService.findTactic(golemId, tacticId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic not found"));
            SelfEvolvingTacticSearchExplanationProjection explanation = projection.getExplanation();
            if (explanation == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic explanation not found");
            }
            return ResponseEntity.ok(explanation);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/{tacticId}/lineage")
    public Mono<ResponseEntity<SelfEvolvingArtifactLineageProjection>> getTacticLineage(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String tacticId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingTacticProjection tactic = selfEvolvingProjectionService.findTactic(golemId, tacticId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic not found"));
            SelfEvolvingArtifactLineageProjection projection = selfEvolvingProjectionService
                    .findArtifactLineage(golemId, tactic.getArtifactStreamId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic lineage not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/tactics/{tacticId}/evidence")
    public Mono<ResponseEntity<Map<String, Object>>> getTacticEvidence(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String tacticId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingTacticProjection tactic = selfEvolvingProjectionService.findTactic(golemId, tacticId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic not found"));
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactEvidence(
                            golemId,
                            tactic.getArtifactStreamId(),
                            "revision",
                            tactic.getContentRevisionId(),
                            tactic.getContentRevisionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tactic evidence not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}")
    public Mono<ResponseEntity<SelfEvolvingArtifactCatalogProjection>> getArtifactSummary(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingArtifactCatalogProjection projection = selfEvolvingProjectionService
                    .findArtifactSummary(golemId, artifactStreamId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact summary not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/lineage")
    public Mono<ResponseEntity<SelfEvolvingArtifactLineageProjection>> getArtifactLineage(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingArtifactLineageProjection projection = selfEvolvingProjectionService
                    .findArtifactLineage(golemId, artifactStreamId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact lineage not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/diff")
    public Mono<ResponseEntity<Map<String, Object>>> getArtifactDiff(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId,
            @RequestParam String fromRevisionId,
            @RequestParam String toRevisionId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactDiff(golemId, artifactStreamId, "revision", fromRevisionId, toRevisionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact diff not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/transition-diff")
    public Mono<ResponseEntity<Map<String, Object>>> getArtifactTransitionDiff(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId,
            @RequestParam String fromNodeId,
            @RequestParam String toNodeId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactDiff(golemId, artifactStreamId, "transition", fromNodeId, toNodeId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Artifact transition diff not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/evidence")
    public Mono<ResponseEntity<Map<String, Object>>> getArtifactEvidence(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId,
            @RequestParam String revisionId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactEvidence(golemId, artifactStreamId, "revision", revisionId, revisionId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact evidence not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/compare-evidence")
    public Mono<ResponseEntity<Map<String, Object>>> getArtifactCompareEvidence(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId,
            @RequestParam String fromRevisionId,
            @RequestParam String toRevisionId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactEvidence(golemId, artifactStreamId, "compare", fromRevisionId, toRevisionId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact evidence not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/golems/{golemId}/artifacts/{artifactStreamId}/transition-evidence")
    public Mono<ResponseEntity<Map<String, Object>>> getArtifactTransitionEvidence(
            Principal principal,
            @PathVariable String golemId,
            @PathVariable String artifactStreamId,
            @RequestParam String fromNodeId,
            @RequestParam String toNodeId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            Map<String, Object> projection = selfEvolvingProjectionService
                    .findArtifactEvidence(golemId, artifactStreamId, "transition", fromNodeId, toNodeId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact evidence not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/artifacts/compare")
    public Mono<ResponseEntity<SelfEvolvingArtifactCompareProjection>> compareArtifacts(
            Principal principal,
            @RequestParam String artifactStreamId,
            @RequestParam String leftGolemId,
            @RequestParam String rightGolemId,
            @RequestParam String leftRevisionId,
            @RequestParam String rightRevisionId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            SelfEvolvingArtifactCompareProjection projection = selfEvolvingProjectionService
                    .compareArtifacts(artifactStreamId, leftGolemId, rightGolemId, leftRevisionId, rightRevisionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact compare not found"));
            return ResponseEntity.ok(projection);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/artifacts/search")
    public Mono<ResponseEntity<List<SelfEvolvingArtifactCatalogProjection>>> searchArtifacts(
            Principal principal,
            @RequestParam(required = false) String golemId,
            @RequestParam(required = false) String artifactType,
            @RequestParam(required = false) String artifactSubtype,
            @RequestParam(required = false) Boolean hasRegression,
            @RequestParam(required = false) Boolean hasPendingApproval,
            @RequestParam(required = false) String rolloutStage,
            @RequestParam(required = false, name = "q") String query) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requirePrivilegedOperator(principal);
            return ResponseEntity.ok(selfEvolvingProjectionService.searchArtifacts(
                    golemId,
                    artifactType,
                    artifactSubtype,
                    hasRegression,
                    hasPendingApproval,
                    rolloutStage,
                    query));
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
