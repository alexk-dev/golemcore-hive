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

package me.golemcore.hive.workflow.application.port.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardFlowDefinition;
import me.golemcore.hive.domain.model.BoardTeam;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.workflow.application.FlowRemapPreview;

public interface BoardWorkflowUseCase {

    List<Board> listBoards();

    Optional<Board> findBoard(String boardId);

    Board getBoard(String boardId);

    Board createBoard(String name, String description, String templateKey,
            CardAssignmentPolicy defaultAssignmentPolicy);

    Board updateBoard(String boardId, String name, String description, CardAssignmentPolicy defaultAssignmentPolicy);

    Board updateBoardFlow(
            String boardId,
            BoardFlowDefinition flow,
            Map<String, String> columnRemap,
            String actorId,
            String actorName);

    Board updateBoardTeam(String boardId, BoardTeam team);

    FlowRemapPreview previewFlowRemap(String boardId, BoardFlowDefinition flow);

    void validateFlow(BoardFlowDefinition flow);

    boolean isTransitionAllowed(Board board, String fromColumnId, String toColumnId);

    boolean isTransitionReachable(Board board, String fromColumnId, String toColumnId);
}
