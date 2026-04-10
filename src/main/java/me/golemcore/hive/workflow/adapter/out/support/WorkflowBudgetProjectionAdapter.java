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

package me.golemcore.hive.workflow.adapter.out.support;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.shared.budget.BudgetBoardProjection;
import me.golemcore.hive.shared.budget.BudgetCardProjection;
import me.golemcore.hive.shared.budget.BudgetWorkflowProjectionPort;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowBudgetProjectionAdapter implements BudgetWorkflowProjectionPort {

    private final BoardRepository boardRepository;
    private final CardRepository cardRepository;

    @Override
    public List<BudgetBoardProjection> listBoards() {
        return boardRepository.list().stream()
                .map(board -> new BudgetBoardProjection(board.getId(), board.getName()))
                .toList();
    }

    @Override
    public List<BudgetCardProjection> listCards() {
        return cardRepository.list().stream()
                .map(card -> new BudgetCardProjection(card.getId(), card.getBoardId(), card.getTitle()))
                .toList();
    }
}
