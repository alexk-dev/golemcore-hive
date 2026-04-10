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
import java.util.Optional;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.workflow.application.CardCreateCommand;
import me.golemcore.hive.workflow.application.CardQuery;
import me.golemcore.hive.workflow.application.CardUpdateCommand;

public interface CardWorkflowUseCase {

    List<Card> listCards(String serviceId, boolean includeArchived);

    List<Card> listCards(CardQuery query);

    Optional<Card> findCard(String cardId);

    Card getCard(String cardId);

    Card createCard(
            String serviceId,
            String title,
            String description,
            String prompt,
            String columnId,
            String teamId,
            String objectiveId,
            String assigneeGolemId,
            CardAssignmentPolicy assignmentPolicy,
            boolean autoAssign,
            String actorId,
            String actorName);

    Card createCard(CardCreateCommand command, String actorId, String actorName);

    Card updateCard(
            String cardId,
            String title,
            String description,
            String prompt,
            String teamId,
            String objectiveId,
            CardAssignmentPolicy assignmentPolicy);

    Card updateCard(String cardId, CardUpdateCommand command);

    Card moveCard(
            String cardId,
            String targetColumnId,
            Integer targetIndex,
            CardTransitionOrigin origin,
            String actorId,
            String actorName,
            String summary);

    Card assignCard(String cardId, String assigneeGolemId);

    Card archiveCard(String cardId);
}
