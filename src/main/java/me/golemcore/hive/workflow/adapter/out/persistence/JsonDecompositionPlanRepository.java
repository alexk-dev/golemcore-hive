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

package me.golemcore.hive.workflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanLink;
import me.golemcore.hive.domain.model.DecompositionPlanStatus;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.DecompositionPlanRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonDecompositionPlanRepository implements DecompositionPlanRepository {

    private static final String DECOMPOSITION_PLANS_DIR = "decomposition-plans";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<DecompositionPlan> list(String sourceCardId, String epicCardId, DecompositionPlanStatus status) {
        List<DecompositionPlan> plans = new ArrayList<>();
        for (String path : storagePort.listObjects(DECOMPOSITION_PLANS_DIR, "")) {
            String content = storagePort.getText(DECOMPOSITION_PLANS_DIR, path);
            if (content == null) {
                continue;
            }
            DecompositionPlan plan = readPlan(content, path);
            if (sourceCardId != null && !sourceCardId.equals(plan.getSourceCardId())) {
                continue;
            }
            if (epicCardId != null && !epicCardId.equals(plan.getEpicCardId())) {
                continue;
            }
            if (status != null && status != plan.getStatus()) {
                continue;
            }
            plans.add(plan);
        }
        plans.sort(java.util.Comparator.comparing(DecompositionPlan::getUpdatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(DecompositionPlan::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(DecompositionPlan::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return plans;
    }

    @Override
    public Optional<DecompositionPlan> findById(String planId) {
        String content = storagePort.getText(DECOMPOSITION_PLANS_DIR, planId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readPlan(content, planId));
    }

    @Override
    public void save(DecompositionPlan plan) {
        try {
            storagePort.putTextAtomic(
                    DECOMPOSITION_PLANS_DIR, plan.getId() + ".json", objectMapper.writeValueAsString(plan));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize decomposition plan " + plan.getId(), exception);
        }
    }

    private DecompositionPlan readPlan(String content, String planRef) {
        try {
            DecompositionPlan plan = objectMapper.readValue(content, DecompositionPlan.class);
            if (plan.getItems() == null) {
                plan.setItems(new ArrayList<>());
            }
            for (DecompositionPlanItem item : plan.getItems()) {
                if (item.getAcceptanceCriteria() == null) {
                    item.setAcceptanceCriteria(new ArrayList<>());
                }
            }
            if (plan.getLinks() == null) {
                plan.setLinks(new ArrayList<>());
            }
            for (DecompositionPlanLink link : plan.getLinks()) {
                if (link.getType() == null) {
                    throw new IllegalStateException("Failed to deserialize decomposition plan " + planRef
                            + ": link type is missing");
                }
            }
            return plan;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize decomposition plan " + planRef, exception);
        }
    }
}
