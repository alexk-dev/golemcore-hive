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
import me.golemcore.hive.adapter.inbound.web.dto.approvals.ApprovalDecisionRequest;
import me.golemcore.hive.adapter.inbound.web.dto.approvals.ApprovalRequestResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.domain.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalsController {

    private final ApprovalService approvalService;

    @GetMapping
    public Mono<ResponseEntity<List<ApprovalRequestResponse>>> listApprovals(
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String boardId,
            @RequestParam(required = false) String cardId,
            @RequestParam(required = false) String golemId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<ApprovalRequestResponse> response = approvalService.listApprovals(status, boardId, cardId, golemId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{approvalId}")
    public Mono<ResponseEntity<ApprovalRequestResponse>> getApproval(Principal principal,
            @PathVariable String approvalId) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            return ResponseEntity.ok(toResponse(approvalService.getApproval(approvalId)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{approvalId}:approve")
    public Mono<ResponseEntity<ApprovalRequestResponse>> approve(
            Principal principal,
            @PathVariable String approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            ApprovalRequest approval = approvalService.approve(
                    approvalId,
                    actor.getSubjectId(),
                    actor.getName(),
                    request != null ? request.comment() : null);
            return ResponseEntity.ok(toResponse(approval));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{approvalId}:reject")
    public Mono<ResponseEntity<ApprovalRequestResponse>> reject(
            Principal principal,
            @PathVariable String approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = ControllerActorSupport.requirePrivilegedOperator(principal);
            ApprovalRequest approval = approvalService.reject(
                    approvalId,
                    actor.getSubjectId(),
                    actor.getName(),
                    request != null ? request.comment() : null);
            return ResponseEntity.ok(toResponse(approval));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest approval) {
        return new ApprovalRequestResponse(
                approval.getId(),
                approval.getSubjectType() != null ? approval.getSubjectType().name() : null,
                approval.getCommandId(),
                approval.getRunId(),
                approval.getThreadId(),
                approval.getBoardId(),
                approval.getCardId(),
                approval.getGolemId(),
                approval.getRequestedByActorId(),
                approval.getRequestedByActorName(),
                approval.getRiskLevel() != null ? approval.getRiskLevel().name() : null,
                approval.getReason(),
                approval.getEstimatedCostMicros(),
                approval.getCommandBody(),
                approval.getStatus().name(),
                approval.getRequestedAt(),
                approval.getUpdatedAt(),
                approval.getDecidedAt(),
                approval.getDecidedByActorId(),
                approval.getDecidedByActorName(),
                approval.getDecisionComment(),
                approval.getPromotionContext());
    }
}
