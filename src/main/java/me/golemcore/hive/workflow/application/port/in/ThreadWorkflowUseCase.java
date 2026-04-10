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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.workflow.application.ThreadMessagePage;

public interface ThreadWorkflowUseCase {

    ThreadRecord getThread(String threadId);

    Optional<ThreadRecord> findThread(String threadId);

    ThreadRecord getThreadByCardId(String cardId);

    ThreadRecord getOrCreateDirectThread(String golemId, String golemDisplayName);

    List<ThreadRecord> listDirectThreads();

    List<ThreadMessage> listMessages(String threadId);

    ThreadMessagePage listMessagesPaginated(String threadId, int limit, Instant before);

    ThreadMessage postOperatorMessage(String threadId, String body, String operatorId, String operatorName);

    ThreadMessage appendMessage(
            ThreadRecord thread,
            String commandId,
            String runId,
            String signalId,
            ThreadMessageType type,
            ThreadParticipantType participantType,
            String authorId,
            String authorName,
            String body,
            Instant createdAt);

    void syncThreadWithCard(Card card);

    void markCommandCreated(ThreadRecord thread, String assignedGolemId, Instant createdAt);
}
