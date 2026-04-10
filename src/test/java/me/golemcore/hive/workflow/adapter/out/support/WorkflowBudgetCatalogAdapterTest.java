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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.shared.budget.BudgetBoardProjection;
import me.golemcore.hive.shared.budget.BudgetCardProjection;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import org.junit.jupiter.api.Test;

class WorkflowBudgetCatalogAdapterTest {

    @Test
    void shouldListAllCardsWithoutBoardByBoardScanning() {
        BoardRepository boardRepository = mock(BoardRepository.class);
        CardRepository cardRepository = mock(CardRepository.class);
        WorkflowBudgetProjectionAdapter adapter = new WorkflowBudgetProjectionAdapter(boardRepository, cardRepository);
        when(boardRepository.list()).thenReturn(List.of(Board.builder()
                .id("board-1")
                .name("Board 1")
                .build()));
        when(cardRepository.list()).thenReturn(List.of(
                Card.builder().id("card-1").boardId("board-1").title("Card 1").build(),
                Card.builder().id("card-2").boardId("missing-board").title("Card 2").build()));

        List<BudgetBoardProjection> boards = adapter.listBoards();
        List<BudgetCardProjection> cards = adapter.listCards();

        assertEquals(1, boards.size());
        assertEquals(2, cards.size());
        assertEquals("missing-board", cards.get(1).boardId());
        verify(boardRepository).list();
        verify(cardRepository).list();
    }
}
