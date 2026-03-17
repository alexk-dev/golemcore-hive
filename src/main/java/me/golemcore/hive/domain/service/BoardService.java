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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardColumn;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.BoardSignalDecision;
import me.golemcore.hive.domain.model.BoardSignalMapping;
import me.golemcore.hive.domain.model.BoardTeam;
import me.golemcore.hive.domain.model.BoardTeamFilter;
import me.golemcore.hive.domain.model.BoardTeamFilterType;
import me.golemcore.hive.domain.model.BoardTransitionRule;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final String BOARDS_DIR = "boards";
    private static final Set<String> ALLOWED_TEMPLATES = Set.of("engineering", "content", "support", "research");

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final FlowRemapService flowRemapService;
    private final AuditService auditService;

    public List<Board> listBoards() {
        List<Board> boards = new ArrayList<>();
        for (String path : storagePort.listObjects(BOARDS_DIR, "")) {
            loadBoardByPath(path).ifPresent(boards::add);
        }
        boards.sort(Comparator.comparing(Board::getUpdatedAt).reversed());
        return boards;
    }

    public Optional<Board> findBoard(String boardId) {
        String content = storagePort.getText(BOARDS_DIR, boardId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Board.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize board " + boardId, exception);
        }
    }

    public Board createBoard(String name,
                             String description,
                             String templateKey,
                             CardAssignmentPolicy defaultAssignmentPolicy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Board name is required");
        }
        String normalizedTemplateKey = templateKey == null || templateKey.isBlank() ? "engineering" : templateKey;
        if (!ALLOWED_TEMPLATES.contains(normalizedTemplateKey)) {
            throw new IllegalArgumentException("Unknown board template: " + normalizedTemplateKey);
        }

        Instant now = Instant.now();
        String boardId = "board_" + UUID.randomUUID().toString().replace("-", "");
        Board board = Board.builder()
                .id(boardId)
                .slug(buildUniqueSlug(name))
                .name(name)
                .description(description)
                .templateKey(normalizedTemplateKey)
                .defaultAssignmentPolicy(defaultAssignmentPolicy != null ? defaultAssignmentPolicy : CardAssignmentPolicy.MANUAL)
                .flow(buildTemplate(normalizedTemplateKey))
                .team(BoardTeam.builder().build())
                .createdAt(now)
                .updatedAt(now)
                .build();
        saveBoard(board);
        auditService.record(AuditEvent.builder()
                .eventType("board.created")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("BOARD")
                .targetId(board.getId())
                .boardId(board.getId())
                .summary("Board created")
                .details(board.getName()));
        return board;
    }

    public Board updateBoard(String boardId, String name, String description, CardAssignmentPolicy defaultAssignmentPolicy) {
        Board board = getBoard(boardId);
        if (name != null && !name.isBlank()) {
            board.setName(name);
        }
        if (description != null) {
            board.setDescription(description);
        }
        if (defaultAssignmentPolicy != null) {
            board.setDefaultAssignmentPolicy(defaultAssignmentPolicy);
        }
        board.setUpdatedAt(Instant.now());
        saveBoard(board);
        auditService.record(AuditEvent.builder()
                .eventType("board.updated")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("BOARD")
                .targetId(board.getId())
                .boardId(board.getId())
                .summary("Board updated")
                .details(board.getName()));
        return board;
    }

    public Board updateBoardFlow(String boardId,
                                 BoardFlowDefinition flow,
                                 Map<String, String> columnRemap,
                                 String actorId,
                                 String actorName) {
        Board board = getBoard(boardId);
        validateFlow(flow);
        flowRemapService.apply(board, flow, columnRemap, actorId, actorName);
        board.setFlow(flow);
        board.setUpdatedAt(Instant.now());
        saveBoard(board);
        auditService.record(AuditEvent.builder()
                .eventType("board.flow_updated")
                .severity("INFO")
                .actorType("OPERATOR")
                .actorId(actorId)
                .actorName(actorName)
                .targetType("BOARD")
                .targetId(board.getId())
                .boardId(board.getId())
                .summary("Board flow updated")
                .details(board.getName()));
        return board;
    }

    public Board updateBoardTeam(String boardId, BoardTeam team) {
        Board board = getBoard(boardId);
        validateTeam(team);
        board.setTeam(team != null ? team : BoardTeam.builder().build());
        board.setUpdatedAt(Instant.now());
        saveBoard(board);
        auditService.record(AuditEvent.builder()
                .eventType("board.team_updated")
                .severity("INFO")
                .actorType("SYSTEM")
                .targetType("BOARD")
                .targetId(board.getId())
                .boardId(board.getId())
                .summary("Board team updated")
                .details(board.getName()));
        return board;
    }

    public FlowRemapService.RemapPreview previewFlowRemap(String boardId, BoardFlowDefinition flow) {
        Board board = getBoard(boardId);
        validateFlow(flow);
        return flowRemapService.preview(board, flow);
    }

    public Board getBoard(String boardId) {
        return findBoard(boardId).orElseThrow(() -> new IllegalArgumentException("Unknown board: " + boardId));
    }

    public void validateFlow(BoardFlowDefinition flow) {
        if (flow == null) {
            throw new IllegalArgumentException("Board flow is required");
        }
        if (flow.getColumns() == null || flow.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Board flow must define at least one column");
        }
        Set<String> columnIds = new LinkedHashSet<>();
        for (BoardColumn column : flow.getColumns()) {
            if (column.getId() == null || column.getId().isBlank()) {
                throw new IllegalArgumentException("Board column id is required");
            }
            if (column.getName() == null || column.getName().isBlank()) {
                throw new IllegalArgumentException("Board column name is required");
            }
            if (!columnIds.add(column.getId())) {
                throw new IllegalArgumentException("Board column ids must be unique");
            }
        }
        if (flow.getDefaultColumnId() == null || !columnIds.contains(flow.getDefaultColumnId())) {
            throw new IllegalArgumentException("Default column must exist in board flow");
        }
        for (BoardTransitionRule transition : flow.getTransitions()) {
            if (!columnIds.contains(transition.getFromColumnId()) || !columnIds.contains(transition.getToColumnId())) {
                throw new IllegalArgumentException("Board transition references unknown column");
            }
        }
        for (BoardSignalMapping signalMapping : flow.getSignalMappings()) {
            if (signalMapping.getSignalType() == null || signalMapping.getSignalType().isBlank()) {
                throw new IllegalArgumentException("Signal mapping type is required");
            }
            if (signalMapping.getDecision() == null) {
                throw new IllegalArgumentException("Signal mapping decision is required");
            }
            if (signalMapping.getDecision() != BoardSignalDecision.IGNORE
                    && (signalMapping.getTargetColumnId() == null || !columnIds.contains(signalMapping.getTargetColumnId()))) {
                throw new IllegalArgumentException("Signal mapping target column must exist");
            }
        }
    }

    public boolean isTransitionAllowed(Board board, String fromColumnId, String toColumnId) {
        if (fromColumnId.equals(toColumnId)) {
            return true;
        }
        List<BoardTransitionRule> transitions = board.getFlow().getTransitions();
        if (transitions == null || transitions.isEmpty()) {
            return true;
        }
        return transitions.stream().anyMatch(transition ->
                transition.getFromColumnId().equals(fromColumnId) && transition.getToColumnId().equals(toColumnId));
    }

    private Optional<Board> loadBoardByPath(String path) {
        String content = storagePort.getText(BOARDS_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, Board.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize board " + path, exception);
        }
    }

    private void saveBoard(Board board) {
        try {
            storagePort.putTextAtomic(BOARDS_DIR, board.getId() + ".json", objectMapper.writeValueAsString(board));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize board " + board.getId(), exception);
        }
    }

    private void validateTeam(BoardTeam team) {
        if (team == null) {
            return;
        }
        if (team.getFilters() == null) {
            team.setFilters(new ArrayList<>());
        }
        if (team.getExplicitGolemIds() == null) {
            team.setExplicitGolemIds(new LinkedHashSet<>());
        }
        for (BoardTeamFilter filter : team.getFilters()) {
            if (filter.getType() == null || filter.getValue() == null || filter.getValue().isBlank()) {
                throw new IllegalArgumentException("Board team filters require type and value");
            }
            if (filter.getType() != BoardTeamFilterType.ROLE_SLUG) {
                throw new IllegalArgumentException("Unsupported board team filter type: " + filter.getType());
            }
        }
    }

    private String buildUniqueSlug(String name) {
        String baseSlug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (baseSlug.isBlank()) {
            baseSlug = "board";
        }
        Set<String> existingSlugs = listBoards().stream().map(Board::getSlug).collect(java.util.stream.Collectors.toSet());
        if (!existingSlugs.contains(baseSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (existingSlugs.contains(baseSlug + "-" + suffix)) {
            suffix++;
        }
        return baseSlug + "-" + suffix;
    }

    private BoardFlowDefinition buildTemplate(String templateKey) {
        return switch (templateKey) {
            case "content" -> contentTemplate();
            case "support" -> supportTemplate();
            case "research" -> researchTemplate();
            case "engineering" -> engineeringTemplate();
            default -> throw new IllegalArgumentException("Unknown board template: " + templateKey);
        };
    }

    private BoardFlowDefinition engineeringTemplate() {
        return BoardFlowDefinition.builder()
                .flowId("engineering-default")
                .name("Engineering")
                .defaultColumnId("inbox")
                .columns(List.of(
                        BoardColumn.builder().id("inbox").name("Inbox").description("Fresh requests").build(),
                        BoardColumn.builder().id("ready").name("Ready").description("Ready to start").build(),
                        BoardColumn.builder().id("in_progress").name("In Progress").description("Active work").build(),
                        BoardColumn.builder().id("blocked").name("Blocked").description("Waiting on unblock").build(),
                        BoardColumn.builder().id("review").name("Review").description("Needs review").build(),
                        BoardColumn.builder().id("done").name("Done").description("Finished").terminal(true).build()))
                .transitions(List.of(
                        transition("inbox", "ready"),
                        transition("ready", "in_progress"),
                        transition("in_progress", "blocked"),
                        transition("blocked", "in_progress"),
                        transition("in_progress", "review"),
                        transition("review", "in_progress"),
                        transition("review", "done")))
                .signalMappings(List.of(
                        signalMapping("WORK_STARTED", BoardSignalDecision.AUTO_APPLY, "in_progress"),
                        signalMapping("BLOCKER_RAISED", BoardSignalDecision.AUTO_APPLY, "blocked"),
                        signalMapping("BLOCKER_CLEARED", BoardSignalDecision.SUGGEST_ONLY, "in_progress"),
                        signalMapping("REVIEW_REQUESTED", BoardSignalDecision.AUTO_APPLY, "review"),
                        signalMapping("WORK_COMPLETED", BoardSignalDecision.AUTO_APPLY, "review"),
                        signalMapping("PROGRESS_REPORTED", BoardSignalDecision.IGNORE, null)))
                .build();
    }

    private BoardFlowDefinition contentTemplate() {
        return BoardFlowDefinition.builder()
                .flowId("content-default")
                .name("Content")
                .defaultColumnId("ideas")
                .columns(List.of(
                        BoardColumn.builder().id("ideas").name("Ideas").build(),
                        BoardColumn.builder().id("drafting").name("Drafting").build(),
                        BoardColumn.builder().id("review").name("Review").build(),
                        BoardColumn.builder().id("blocked").name("Blocked").build(),
                        BoardColumn.builder().id("published").name("Published").terminal(true).build()))
                .transitions(List.of(
                        transition("ideas", "drafting"),
                        transition("drafting", "review"),
                        transition("drafting", "blocked"),
                        transition("blocked", "drafting"),
                        transition("review", "drafting"),
                        transition("review", "published")))
                .signalMappings(List.of(
                        signalMapping("WORK_STARTED", BoardSignalDecision.AUTO_APPLY, "drafting"),
                        signalMapping("BLOCKER_RAISED", BoardSignalDecision.AUTO_APPLY, "blocked"),
                        signalMapping("REVIEW_REQUESTED", BoardSignalDecision.AUTO_APPLY, "review"),
                        signalMapping("WORK_COMPLETED", BoardSignalDecision.AUTO_APPLY, "published")))
                .build();
    }

    private BoardFlowDefinition supportTemplate() {
        return BoardFlowDefinition.builder()
                .flowId("support-default")
                .name("Support")
                .defaultColumnId("new")
                .columns(List.of(
                        BoardColumn.builder().id("new").name("New").build(),
                        BoardColumn.builder().id("triage").name("Triage").build(),
                        BoardColumn.builder().id("in_progress").name("In Progress").build(),
                        BoardColumn.builder().id("waiting").name("Waiting").build(),
                        BoardColumn.builder().id("done").name("Done").terminal(true).build()))
                .transitions(List.of(
                        transition("new", "triage"),
                        transition("triage", "in_progress"),
                        transition("in_progress", "waiting"),
                        transition("waiting", "in_progress"),
                        transition("in_progress", "done"),
                        transition("triage", "done")))
                .signalMappings(List.of(
                        signalMapping("WORK_STARTED", BoardSignalDecision.AUTO_APPLY, "in_progress"),
                        signalMapping("BLOCKER_RAISED", BoardSignalDecision.AUTO_APPLY, "waiting"),
                        signalMapping("WORK_COMPLETED", BoardSignalDecision.AUTO_APPLY, "done")))
                .build();
    }

    private BoardFlowDefinition researchTemplate() {
        return BoardFlowDefinition.builder()
                .flowId("research-default")
                .name("Research")
                .defaultColumnId("backlog")
                .columns(List.of(
                        BoardColumn.builder().id("backlog").name("Backlog").build(),
                        BoardColumn.builder().id("active").name("Active").build(),
                        BoardColumn.builder().id("blocked").name("Blocked").build(),
                        BoardColumn.builder().id("decision").name("Decision").build(),
                        BoardColumn.builder().id("done").name("Done").terminal(true).build()))
                .transitions(List.of(
                        transition("backlog", "active"),
                        transition("active", "blocked"),
                        transition("blocked", "active"),
                        transition("active", "decision"),
                        transition("decision", "active"),
                        transition("decision", "done")))
                .signalMappings(List.of(
                        signalMapping("WORK_STARTED", BoardSignalDecision.AUTO_APPLY, "active"),
                        signalMapping("BLOCKER_RAISED", BoardSignalDecision.AUTO_APPLY, "blocked"),
                        signalMapping("WORK_COMPLETED", BoardSignalDecision.AUTO_APPLY, "decision")))
                .build();
    }

    private BoardTransitionRule transition(String fromColumnId, String toColumnId) {
        return BoardTransitionRule.builder()
                .fromColumnId(fromColumnId)
                .toColumnId(toColumnId)
                .build();
    }

    private BoardSignalMapping signalMapping(String signalType, BoardSignalDecision decision, String targetColumnId) {
        return BoardSignalMapping.builder()
                .signalType(signalType)
                .decision(decision)
                .targetColumnId(targetColumnId)
                .build();
    }
}
