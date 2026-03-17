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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ThreadService {

    private static final String THREADS_DIR = "threads";
    private static final String THREAD_MESSAGES_DIR = "thread-messages";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final CardService cardService;

    public ThreadRecord getThread(String threadId) {
        return findThread(threadId).orElseThrow(() -> new IllegalArgumentException("Unknown thread: " + threadId));
    }

    public Optional<ThreadRecord> findThread(String threadId) {
        String content = storagePort.getText(THREADS_DIR, threadId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, ThreadRecord.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread " + threadId, exception);
        }
    }

    public ThreadRecord getThreadByCardId(String cardId) {
        Card card = cardService.getCard(cardId);
        return getOrCreateThreadForCard(card);
    }

    public List<ThreadMessage> listMessages(String threadId) {
        List<ThreadMessage> messages = new ArrayList<>();
        for (String path : storagePort.listObjects(THREAD_MESSAGES_DIR, "")) {
            Optional<ThreadMessage> messageOptional = loadMessage(path);
            if (messageOptional.isEmpty()) {
                continue;
            }
            ThreadMessage message = messageOptional.get();
            if (threadId.equals(message.getThreadId())) {
                messages.add(message);
            }
        }
        messages.sort(Comparator.comparing(ThreadMessage::getCreatedAt).thenComparing(ThreadMessage::getId));
        return messages;
    }

    public ThreadMessage postOperatorMessage(String threadId, String body, String operatorId, String operatorName) {
        ThreadRecord thread = getThread(threadId);
        return appendMessage(thread, null, null, null, ThreadMessageType.NOTE, ThreadParticipantType.OPERATOR,
                operatorId, operatorName, body, Instant.now());
    }

    public ThreadMessage appendMessage(ThreadRecord thread,
                                       String commandId,
                                       String runId,
                                       String signalId,
                                       ThreadMessageType type,
                                       ThreadParticipantType participantType,
                                       String authorId,
                                       String authorName,
                                       String body,
                                       Instant createdAt) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Thread message body is required");
        }
        Instant now = createdAt != null ? createdAt : Instant.now();
        ThreadMessage message = ThreadMessage.builder()
                .id("msg_" + UUID.randomUUID().toString().replace("-", ""))
                .threadId(thread.getId())
                .cardId(thread.getCardId())
                .commandId(commandId)
                .runId(runId)
                .signalId(signalId)
                .type(type)
                .participantType(participantType)
                .authorId(authorId)
                .authorName(authorName)
                .body(body)
                .createdAt(now)
                .build();
        saveMessage(message);
        thread.setUpdatedAt(now);
        thread.setLastMessageAt(now);
        saveThread(thread);
        return message;
    }

    public void syncThreadWithCard(Card card) {
        ThreadRecord thread = getOrCreateThreadForCard(card);
        thread.setTitle(card.getTitle());
        thread.setAssignedGolemId(card.getAssigneeGolemId());
        thread.setUpdatedAt(card.getUpdatedAt() != null ? card.getUpdatedAt() : Instant.now());
        saveThread(thread);
    }

    public void markCommandCreated(ThreadRecord thread, String assignedGolemId, Instant createdAt) {
        Instant now = createdAt != null ? createdAt : Instant.now();
        thread.setAssignedGolemId(assignedGolemId);
        thread.setLastCommandAt(now);
        thread.setUpdatedAt(now);
        saveThread(thread);
    }

    private ThreadRecord getOrCreateThreadForCard(Card card) {
        Optional<ThreadRecord> existing = findThread(card.getThreadId());
        if (existing.isPresent()) {
            ThreadRecord thread = existing.get();
            boolean changed = false;
            if (!java.util.Objects.equals(thread.getTitle(), card.getTitle())) {
                thread.setTitle(card.getTitle());
                changed = true;
            }
            if (!java.util.Objects.equals(thread.getAssignedGolemId(), card.getAssigneeGolemId())) {
                thread.setAssignedGolemId(card.getAssigneeGolemId());
                changed = true;
            }
            if (changed) {
                thread.setUpdatedAt(card.getUpdatedAt() != null ? card.getUpdatedAt() : Instant.now());
                saveThread(thread);
            }
            return thread;
        }

        Instant now = card.getCreatedAt() != null ? card.getCreatedAt() : Instant.now();
        ThreadRecord thread = ThreadRecord.builder()
                .id(card.getThreadId())
                .boardId(card.getBoardId())
                .cardId(card.getId())
                .title(card.getTitle())
                .assignedGolemId(card.getAssigneeGolemId())
                .createdAt(now)
                .updatedAt(card.getUpdatedAt() != null ? card.getUpdatedAt() : now)
                .build();
        saveThread(thread);
        return thread;
    }

    private Optional<ThreadMessage> loadMessage(String path) {
        String content = storagePort.getText(THREAD_MESSAGES_DIR, path);
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, ThreadMessage.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread message " + path, exception);
        }
    }

    private void saveThread(ThreadRecord thread) {
        try {
            storagePort.putTextAtomic(THREADS_DIR, thread.getId() + ".json", objectMapper.writeValueAsString(thread));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread " + thread.getId(), exception);
        }
    }

    private void saveMessage(ThreadMessage message) {
        try {
            storagePort.putTextAtomic(THREAD_MESSAGES_DIR, message.getId() + ".json", objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread message " + message.getId(), exception);
        }
    }
}
