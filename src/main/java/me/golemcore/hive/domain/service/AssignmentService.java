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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AssignmentSuggestion;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardTeamFilter;
import me.golemcore.hive.domain.model.BoardTeamFilterType;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.GolemRoleBinding;
import me.golemcore.hive.domain.model.GolemState;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final GolemRegistryService golemRegistryService;

    public List<AssignmentSuggestion> getTeamCandidates(Board board) {
        if (board.getTeam() == null) {
            return List.of();
        }
        Set<String> explicitIds = board.getTeam().getExplicitGolemIds() != null
                ? board.getTeam().getExplicitGolemIds()
                : Set.of();
        Set<String> includedRoles = board.getTeam().getFilters() != null
                ? board.getTeam().getFilters().stream()
                        .filter(filter -> filter.getType() == BoardTeamFilterType.ROLE_SLUG)
                        .map(BoardTeamFilter::getValue)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                : Set.of();

        List<AssignmentSuggestion> suggestions = new ArrayList<>();
        for (Golem golem : golemRegistryService.listGolems(null, null, null)) {
            List<String> reasons = new ArrayList<>();
            int score = stateScore(golem.getState());
            if (explicitIds.contains(golem.getId())) {
                reasons.add("Explicit board team member");
                score += 100;
            }
            List<String> golemRoles = golem.getRoleBindings().stream().map(GolemRoleBinding::getRoleSlug).sorted()
                    .toList();
            for (String roleSlug : includedRoles) {
                if (golemRoles.contains(roleSlug)) {
                    reasons.add("Matches role filter: " + roleSlug);
                    score += 70;
                }
            }
            if (!reasons.isEmpty()) {
                suggestions.add(AssignmentSuggestion.builder()
                        .golemId(golem.getId())
                        .displayName(golem.getDisplayName())
                        .state(golem.getState())
                        .score(score)
                        .reasons(reasons)
                        .roleSlugs(golemRoles)
                        .inBoardTeam(true)
                        .build());
            }
        }
        suggestions.sort(Comparator.comparing(AssignmentSuggestion::getScore).reversed()
                .thenComparing(AssignmentSuggestion::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return suggestions;
    }

    public List<AssignmentSuggestion> getAllCandidates(Board board) {
        Set<String> teamIds = getTeamCandidates(board).stream().map(AssignmentSuggestion::getGolemId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AssignmentSuggestion> suggestions = new ArrayList<>();
        for (Golem golem : golemRegistryService.listGolems(null, null, null)) {
            List<String> reasons = new ArrayList<>();
            if (teamIds.contains(golem.getId())) {
                reasons.add("Visible in Team");
            } else {
                reasons.add("Available from global fleet");
            }
            int score = stateScore(golem.getState()) + (teamIds.contains(golem.getId()) ? 30 : 0);
            suggestions.add(AssignmentSuggestion.builder()
                    .golemId(golem.getId())
                    .displayName(golem.getDisplayName())
                    .state(golem.getState())
                    .score(score)
                    .reasons(reasons)
                    .roleSlugs(golem.getRoleBindings().stream().map(GolemRoleBinding::getRoleSlug).sorted().toList())
                    .inBoardTeam(teamIds.contains(golem.getId()))
                    .build());
        }
        suggestions.sort(Comparator.comparing(AssignmentSuggestion::getScore).reversed()
                .thenComparing(AssignmentSuggestion::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return suggestions;
    }

    public AssignmentSuggestion suggestDefaultAssignee(Board board) {
        return getAllCandidates(board).stream().findFirst().orElse(null);
    }

    private int stateScore(GolemState state) {
        return switch (state) {
        case ONLINE -> 60;
        case DEGRADED -> 40;
        case PAUSED -> 10;
        case PENDING_ENROLLMENT -> 5;
        case OFFLINE -> 0;
        case REVOKED -> -100;
        };
    }
}
