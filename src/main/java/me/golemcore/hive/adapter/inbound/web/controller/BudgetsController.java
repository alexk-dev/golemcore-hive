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
import me.golemcore.hive.adapter.inbound.web.dto.budgets.BudgetSnapshotResponse;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.domain.service.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetsController {

    private final BudgetService budgetService;

    @GetMapping
    public Mono<ResponseEntity<List<BudgetSnapshotResponse>>> listBudgetSnapshots(
            Principal principal,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String query) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<BudgetSnapshotResponse> response = budgetService.listSnapshots(scopeType, query).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private BudgetSnapshotResponse toResponse(BudgetSnapshot snapshot) {
        return new BudgetSnapshotResponse(
                snapshot.getId(),
                snapshot.getScopeType().name(),
                snapshot.getScopeId(),
                snapshot.getScopeLabel(),
                snapshot.getBoardId(),
                snapshot.getCardId(),
                snapshot.getGolemId(),
                snapshot.getCommandCount(),
                snapshot.getRunCount(),
                snapshot.getInputTokens(),
                snapshot.getOutputTokens(),
                snapshot.getActualCostMicros(),
                snapshot.getEstimatedPendingCostMicros(),
                snapshot.getUpdatedAt());
    }
}
