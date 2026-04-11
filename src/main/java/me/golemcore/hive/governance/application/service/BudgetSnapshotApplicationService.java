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
import java.util.Locale;
import java.util.Map;
import me.golemcore.hive.domain.model.BudgetScopeType;
import me.golemcore.hive.domain.model.BudgetSnapshot;
import me.golemcore.hive.governance.application.BudgetCommandProjectionStatus;
import me.golemcore.hive.governance.application.BudgetProjectionData;
import me.golemcore.hive.governance.application.port.in.BudgetSnapshotUseCase;
import me.golemcore.hive.governance.application.port.out.BudgetProjectionSourcePort;
import me.golemcore.hive.governance.application.port.out.BudgetSnapshotRepositoryPort;

public class BudgetSnapshotApplicationService implements BudgetSnapshotUseCase {

    private final BudgetSnapshotRepositoryPort budgetSnapshotRepositoryPort;
    private final BudgetProjectionSourcePort budgetProjectionSourcePort;

    public BudgetSnapshotApplicationService(
            BudgetSnapshotRepositoryPort budgetSnapshotRepositoryPort,
            BudgetProjectionSourcePort budgetProjectionSourcePort) {
        this.budgetSnapshotRepositoryPort = budgetSnapshotRepositoryPort;
        this.budgetProjectionSourcePort = budgetProjectionSourcePort;
    }

    @Override
    public List<BudgetSnapshot> listSnapshots(String scopeType, String query) {
        refreshSnapshots();
        List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();
        for (BudgetSnapshot budgetSnapshot : budgetSnapshotRepositoryPort.findAll()) {
            if (scopeType != null && !scopeType.isBlank()
                    && !budgetSnapshot.getScopeType().name().equalsIgnoreCase(scopeType)) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String normalizedQuery = query.toLowerCase(Locale.ROOT);
                String scopeLabel = budgetSnapshot.getScopeLabel() != null
                        ? budgetSnapshot.getScopeLabel().toLowerCase(Locale.ROOT)
                        : "";
                String scopeId = budgetSnapshot.getScopeId() != null
                        ? budgetSnapshot.getScopeId().toLowerCase(Locale.ROOT)
                        : "";
                if (!scopeLabel.contains(normalizedQuery) && !scopeId.contains(normalizedQuery)) {
                    continue;
                }
            }
            budgetSnapshots.add(budgetSnapshot);
        }
        budgetSnapshots.sort(Comparator.comparing(BudgetSnapshot::getActualCostMicros).reversed()
                .thenComparing(BudgetSnapshot::getScopeLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return budgetSnapshots;
    }

    @Override
    public void refreshSnapshots() {
        BudgetProjectionData projectionData = budgetProjectionSourcePort.loadProjectionData();
        BudgetProjectionData.CustomerProjection customer = resolveCustomer(projectionData.customers());
        Map<String, BudgetProjectionData.ServiceProjection> servicesById = indexServicesById(projectionData.services());
        Map<String, BudgetProjectionData.TeamProjection> teamsById = indexTeamsById(projectionData.teams());
        Map<String, BudgetProjectionData.ObjectiveProjection> objectivesById = indexObjectivesById(
                projectionData.objectives());
        Map<String, BudgetProjectionData.CardProjection> cardsById = indexCardsById(projectionData.cards());
        Map<String, BudgetProjectionData.CommandProjection> commandsById = indexCommandsById(projectionData.commands());
        Map<String, BudgetProjectionData.RunProjection> runsById = indexRunsById(projectionData.runs());
        Map<String, BudgetAccumulator> accumulators = new LinkedHashMap<>();

        for (BudgetProjectionData.CommandProjection commandProjection : commandsById.values()) {
            BudgetProjectionData.CardProjection card = commandProjection.cardId() != null
                    ? cardsById.get(commandProjection.cardId())
                    : null;
            addCommand(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, null,
                    commandProjection);
            addCommand(accumulators, BudgetScopeType.CUSTOMER, customer.id(), customer.name(), customer.id(), null,
                    null, null, commandProjection);
            OutcomeContext outcomeContext = resolveOutcomeContext(card, servicesById, teamsById, objectivesById);
            addOutcomeCommand(accumulators, outcomeContext, commandProjection);
        }

        for (BudgetProjectionData.RunProjection runProjection : runsById.values()) {
            BudgetProjectionData.CardProjection card = runProjection.cardId() != null
                    ? cardsById.get(runProjection.cardId())
                    : null;
            addRun(accumulators, BudgetScopeType.SYSTEM, "system", "System", null, null, null, null, runProjection);
            addRun(accumulators, BudgetScopeType.CUSTOMER, customer.id(), customer.name(), customer.id(), null, null,
                    null, runProjection);
            OutcomeContext outcomeContext = resolveOutcomeContext(card, servicesById, teamsById, objectivesById);
            addOutcomeRun(accumulators, outcomeContext, runProjection);
        }

        List<BudgetSnapshot> budgetSnapshots = new ArrayList<>();
        for (BudgetAccumulator budgetAccumulator : accumulators.values()) {
            budgetSnapshots.add(budgetAccumulator.toSnapshot());
        }
        budgetSnapshotRepositoryPort.replaceAll(budgetSnapshots);
    }

    private void addCommand(
            Map<String, BudgetAccumulator> accumulators,
            BudgetScopeType scopeType,
            String scopeId,
            String scopeLabel,
            String customerId,
            String teamId,
            String objectiveId,
            String serviceId,
            BudgetProjectionData.CommandProjection commandProjection) {
        BudgetAccumulator budgetAccumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, customerId, teamId, objectiveId,
                        serviceId));
        budgetAccumulator.commandCount = budgetAccumulator.commandCount + 1;
        if (commandProjection.status() == BudgetCommandProjectionStatus.PENDING_APPROVAL
                || commandProjection.status() == BudgetCommandProjectionStatus.QUEUED
                || commandProjection.status() == BudgetCommandProjectionStatus.DELIVERED
                || commandProjection.status() == BudgetCommandProjectionStatus.RUNNING) {
            budgetAccumulator.estimatedPendingCostMicros = budgetAccumulator.estimatedPendingCostMicros
                    + Math.max(commandProjection.estimatedCostMicros(), 0L);
        }
    }

    private void addRun(
            Map<String, BudgetAccumulator> accumulators,
            BudgetScopeType scopeType,
            String scopeId,
            String scopeLabel,
            String customerId,
            String teamId,
            String objectiveId,
            String serviceId,
            BudgetProjectionData.RunProjection runProjection) {
        BudgetAccumulator budgetAccumulator = accumulators.computeIfAbsent(
                key(scopeType, scopeId),
                ignored -> new BudgetAccumulator(scopeType, scopeId, scopeLabel, customerId, teamId, objectiveId,
                        serviceId));
        budgetAccumulator.runCount = budgetAccumulator.runCount + 1;
        budgetAccumulator.inputTokens = budgetAccumulator.inputTokens + Math.max(runProjection.inputTokens(), 0L);
        budgetAccumulator.outputTokens = budgetAccumulator.outputTokens
                + Math.max(runProjection.outputTokens(), 0L);
        budgetAccumulator.actualCostMicros = budgetAccumulator.actualCostMicros
                + Math.max(runProjection.accumulatedCostMicros(), 0L);
    }

    private String key(BudgetScopeType scopeType, String scopeId) {
        return scopeType.name() + ":" + scopeId;
    }

    private void addOutcomeCommand(
            Map<String, BudgetAccumulator> accumulators,
            OutcomeContext outcomeContext,
            BudgetProjectionData.CommandProjection commandProjection) {
        if (outcomeContext == null) {
            return;
        }
        if (hasText(outcomeContext.serviceId())) {
            addCommand(accumulators, BudgetScopeType.SERVICE, outcomeContext.serviceId(),
                    outcomeContext.serviceLabel(), null, null, null, outcomeContext.serviceId(), commandProjection);
        }
        if (hasText(outcomeContext.objectiveId())) {
            addCommand(accumulators, BudgetScopeType.OBJECTIVE, outcomeContext.objectiveId(),
                    outcomeContext.objectiveLabel(), null, null, outcomeContext.objectiveId(), null,
                    commandProjection);
        }
        if (hasText(outcomeContext.teamId())) {
            addCommand(accumulators, BudgetScopeType.TEAM, outcomeContext.teamId(), outcomeContext.teamLabel(), null,
                    outcomeContext.teamId(), null, null, commandProjection);
        }
    }

    private void addOutcomeRun(
            Map<String, BudgetAccumulator> accumulators,
            OutcomeContext outcomeContext,
            BudgetProjectionData.RunProjection runProjection) {
        if (outcomeContext == null) {
            return;
        }
        if (hasText(outcomeContext.serviceId())) {
            addRun(accumulators, BudgetScopeType.SERVICE, outcomeContext.serviceId(), outcomeContext.serviceLabel(),
                    null, null, null, outcomeContext.serviceId(), runProjection);
        }
        if (hasText(outcomeContext.objectiveId())) {
            addRun(accumulators, BudgetScopeType.OBJECTIVE, outcomeContext.objectiveId(),
                    outcomeContext.objectiveLabel(), null, null, outcomeContext.objectiveId(), null, runProjection);
        }
        if (hasText(outcomeContext.teamId())) {
            addRun(accumulators, BudgetScopeType.TEAM, outcomeContext.teamId(), outcomeContext.teamLabel(), null,
                    outcomeContext.teamId(), null, null, runProjection);
        }
    }

    private OutcomeContext resolveOutcomeContext(
            BudgetProjectionData.CardProjection card,
            Map<String, BudgetProjectionData.ServiceProjection> servicesById,
            Map<String, BudgetProjectionData.TeamProjection> teamsById,
            Map<String, BudgetProjectionData.ObjectiveProjection> objectivesById) {
        if (card == null) {
            return null;
        }
        String serviceId = firstNonBlank(card.serviceId(), card.boardId());
        BudgetProjectionData.ServiceProjection service = serviceId != null ? servicesById.get(serviceId) : null;
        String objectiveId = normalizeOptionalId(card.objectiveId());
        BudgetProjectionData.ObjectiveProjection objective = objectiveId != null ? objectivesById.get(objectiveId)
                : null;
        String teamId = firstNonBlank(card.teamId(), objective != null ? objective.ownerTeamId() : null);
        BudgetProjectionData.TeamProjection team = teamId != null ? teamsById.get(teamId) : null;
        return new OutcomeContext(
                serviceId,
                service != null && hasText(service.name()) ? service.name() : serviceId,
                teamId,
                team != null && hasText(team.name()) ? team.name() : teamId,
                objectiveId,
                objective != null && hasText(objective.name()) ? objective.name() : objectiveId);
    }

    private BudgetProjectionData.CustomerProjection resolveCustomer(
            List<BudgetProjectionData.CustomerProjection> customers) {
        for (BudgetProjectionData.CustomerProjection customer : customers) {
            if (hasText(customer.id())) {
                return new BudgetProjectionData.CustomerProjection(
                        customer.id(),
                        hasText(customer.name()) ? customer.name() : customer.id());
            }
        }
        return new BudgetProjectionData.CustomerProjection("customer_default", "Default customer");
    }

    private Map<String, BudgetProjectionData.ServiceProjection> indexServicesById(
            List<BudgetProjectionData.ServiceProjection> services) {
        Map<String, BudgetProjectionData.ServiceProjection> servicesById = new LinkedHashMap<>();
        for (BudgetProjectionData.ServiceProjection service : services) {
            if (hasText(service.id())) {
                servicesById.put(service.id(), service);
            }
        }
        return servicesById;
    }

    private Map<String, BudgetProjectionData.TeamProjection> indexTeamsById(
            List<BudgetProjectionData.TeamProjection> teams) {
        Map<String, BudgetProjectionData.TeamProjection> teamsById = new LinkedHashMap<>();
        for (BudgetProjectionData.TeamProjection team : teams) {
            if (hasText(team.id())) {
                teamsById.put(team.id(), team);
            }
        }
        return teamsById;
    }

    private Map<String, BudgetProjectionData.ObjectiveProjection> indexObjectivesById(
            List<BudgetProjectionData.ObjectiveProjection> objectives) {
        Map<String, BudgetProjectionData.ObjectiveProjection> objectivesById = new LinkedHashMap<>();
        for (BudgetProjectionData.ObjectiveProjection objective : objectives) {
            if (hasText(objective.id())) {
                objectivesById.put(objective.id(), objective);
            }
        }
        return objectivesById;
    }

    private Map<String, BudgetProjectionData.CardProjection> indexCardsById(
            List<BudgetProjectionData.CardProjection> cards) {
        Map<String, BudgetProjectionData.CardProjection> cardsById = new LinkedHashMap<>();
        for (BudgetProjectionData.CardProjection card : cards) {
            if (hasText(card.id())) {
                cardsById.put(card.id(), card);
            }
        }
        return cardsById;
    }

    private Map<String, BudgetProjectionData.CommandProjection> indexCommandsById(
            List<BudgetProjectionData.CommandProjection> commandRecords) {
        Map<String, BudgetProjectionData.CommandProjection> commandsById = new LinkedHashMap<>();
        for (BudgetProjectionData.CommandProjection commandRecord : commandRecords) {
            if (hasText(commandRecord.id())) {
                commandsById.put(commandRecord.id(), commandRecord);
            }
        }
        return commandsById;
    }

    private Map<String, BudgetProjectionData.RunProjection> indexRunsById(
            List<BudgetProjectionData.RunProjection> runProjections) {
        Map<String, BudgetProjectionData.RunProjection> runsById = new LinkedHashMap<>();
        for (BudgetProjectionData.RunProjection runProjection : runProjections) {
            if (hasText(runProjection.id())) {
                runsById.put(runProjection.id(), runProjection);
            }
        }
        return runsById;
    }

    private String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return normalizeOptionalId(second);
    }

    private String normalizeOptionalId(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record OutcomeContext(String serviceId, String serviceLabel, String teamId, String teamLabel,
            String objectiveId, String objectiveLabel) {
    }

    private static class BudgetAccumulator {
        private final BudgetScopeType scopeType;
        private final String scopeId;
        private final String scopeLabel;
        private final String customerId;
        private final String teamId;
        private final String objectiveId;
        private final String serviceId;
        private long commandCount;
        private long runCount;
        private long inputTokens;
        private long outputTokens;
        private long actualCostMicros;
        private long estimatedPendingCostMicros;

        private BudgetAccumulator(
                BudgetScopeType scopeType,
                String scopeId,
                String scopeLabel,
                String customerId,
                String teamId,
                String objectiveId,
                String serviceId) {
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.scopeLabel = scopeLabel;
            this.customerId = customerId;
            this.teamId = teamId;
            this.objectiveId = objectiveId;
            this.serviceId = serviceId;
        }

        private BudgetSnapshot toSnapshot() {
            return BudgetSnapshot.builder()
                    .id(scopeType.name().toLowerCase(Locale.ROOT) + "_" + scopeId)
                    .scopeType(scopeType)
                    .scopeId(scopeId)
                    .scopeLabel(scopeLabel)
                    .customerId(customerId)
                    .teamId(teamId)
                    .objectiveId(objectiveId)
                    .serviceId(serviceId)
                    .commandCount(commandCount)
                    .runCount(runCount)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .actualCostMicros(actualCostMicros)
                    .estimatedPendingCostMicros(estimatedPendingCostMicros)
                    .updatedAt(Instant.now())
                    .build();
        }
    }
}
