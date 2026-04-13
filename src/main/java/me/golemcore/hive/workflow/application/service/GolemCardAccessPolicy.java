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

package me.golemcore.hive.workflow.application.service;

import java.util.HashSet;
import java.util.Set;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;

public class GolemCardAccessPolicy {

    private final CardWorkflowUseCase cardWorkflowUseCase;

    public GolemCardAccessPolicy(CardWorkflowUseCase cardWorkflowUseCase) {
        this.cardWorkflowUseCase = cardWorkflowUseCase;
    }

    public void requireCanAccessCard(String golemId, String cardId) {
        if (cardId == null || cardId.isBlank()) {
            throw new SecurityException("Card access denied");
        }
        requireCanAccessCard(golemId, cardWorkflowUseCase.getCard(cardId));
    }

    public void requireCanAccessCard(String golemId, Card card) {
        if (!canAccessCard(golemId, card)) {
            throw new SecurityException("Card access denied");
        }
    }

    public boolean canAccessCard(String golemId, Card card) {
        return canAccessCard(golemId, card, new HashSet<>());
    }

    private boolean canAccessCard(String golemId, Card card, Set<String> visitedCardIds) {
        if (golemId == null || golemId.isBlank() || card == null) {
            return false;
        }
        if (golemId.equals(card.getAssigneeGolemId())
                || (card.getReviewerGolemIds() != null && card.getReviewerGolemIds().contains(golemId))) {
            return true;
        }
        if (card.getId() != null && !card.getId().isBlank() && !visitedCardIds.add(card.getId())) {
            return false;
        }
        return canAccessRelatedCard(golemId, card.getParentCardId(), visitedCardIds)
                || canAccessRelatedCard(golemId, card.getReviewOfCardId(), visitedCardIds);
    }

    private boolean canAccessRelatedCard(String golemId, String relatedCardId, Set<String> visitedCardIds) {
        if (relatedCardId == null || relatedCardId.isBlank()) {
            return false;
        }
        try {
            return canAccessCard(golemId, cardWorkflowUseCase.getCard(relatedCardId), visitedCardIds);
        } catch (IllegalArgumentException exception) { // NOSONAR - missing related cards deny access
            return false;
        }
    }
}
