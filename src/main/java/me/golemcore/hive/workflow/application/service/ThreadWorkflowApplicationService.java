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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.workflow.application.ThreadMessagePage;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.ThreadRepository;

public class ThreadWorkflowApplicationService implements ThreadWorkflowUseCase {

    private final ThreadRepository threadRepository;
    private final CardRepository cardRepository;

    public ThreadWorkflowApplicationService(ThreadRepository threadRepository, CardRepository cardRepository) {
        this.threadRepository = threadRepository;
        this.cardRepository = cardRepository;
    }

    @Override
    public ThreadRecord getThread(String threadId) {
        return findThread(threadId).orElseThrow(() -> new IllegalArgumentException("Unknown thread: " + threadId));
    }

    @Override
    public Optional<ThreadRecord> findThread(String threadId) {
        return threadRepository.findThread(threadId);
    }

    @Override
    public ThreadRecord getThreadByCardId(String cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown card: " + cardId));
        return getOrCreateThreadForCard(card);
    }

    @Override
    public ThreadRecord getOrCreateDirectThread(String golemId, String golemDisplayName) {
        String threadId = "dm_" + golemId;
        Optional<ThreadRecord> existing = findThread(threadId);
        if (existing.isPresent()) {
            ThreadRecord thread = existing.get();
            if (!java.util.Objects.equals(thread.getTitle(), golemDisplayName)) {
                thread.setTitle(golemDisplayName);
                thread.setUpdatedAt(Instant.now());
                threadRepository.saveThread(thread);
            }
            return thread;
        }
        Instant now = Instant.now();
        ThreadRecord thread = ThreadRecord.builder()
                .id(threadId)
                .title(golemDisplayName)
                .assignedGolemId(golemId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        threadRepository.saveThread(thread);
        return thread;
    }

    @Override
    public List<ThreadRecord> listDirectThreads() {
        List<ThreadRecord> threads = threadRepository.listThreads().stream()
                .filter(thread -> thread.getId() != null && thread.getId().startsWith("dm_"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        threads.sort(Comparator.comparing(
                ThreadRecord::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return threads;
    }

    @Override
    public List<ThreadMessage> listMessages(String threadId) {
        List<ThreadMessage> messages = new ArrayList<>(threadRepository.listMessages(threadId));
        messages.sort(Comparator.comparing(ThreadMessage::getCreatedAt).thenComparing(ThreadMessage::getId));
        return messages;
    }

    @Override
    public ThreadMessagePage listMessagesPaginated(String threadId, int limit, Instant before) {
        List<ThreadMessage> all = listMessages(threadId);
        if (before != null) {
            all = all.stream()
                    .filter(message -> message.getCreatedAt().isBefore(before))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        int total = all.size();
        if (total <= limit) {
            return new ThreadMessagePage(all, false);
        }
        List<ThreadMessage> page = all.subList(total - limit, total);
        return new ThreadMessagePage(new ArrayList<>(page), true);
    }

    @Override
    public ThreadMessage postOperatorMessage(String threadId, String body, String operatorId, String operatorName) {
        ThreadRecord thread = getThread(threadId);
        return appendMessage(
                thread,
                null,
                null,
                null,
                ThreadMessageType.NOTE,
                ThreadParticipantType.OPERATOR,
                operatorId,
                operatorName,
                body,
                Instant.now());
    }

    @Override
    public ThreadMessage appendMessage(
            ThreadRecord thread,
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
        threadRepository.saveMessage(message);
        thread.setUpdatedAt(now);
        thread.setLastMessageAt(now);
        threadRepository.saveThread(thread);
        return message;
    }

    @Override
    public void syncThreadWithCard(Card card) {
        ThreadRecord thread = getOrCreateThreadForCard(card);
        thread.setTitle(card.getTitle());
        thread.setAssignedGolemId(card.getAssigneeGolemId());
        thread.setUpdatedAt(card.getUpdatedAt() != null ? card.getUpdatedAt() : Instant.now());
        threadRepository.saveThread(thread);
    }

    @Override
    public void markCommandCreated(ThreadRecord thread, String assignedGolemId, Instant createdAt) {
        Instant now = createdAt != null ? createdAt : Instant.now();
        thread.setAssignedGolemId(assignedGolemId);
        thread.setLastCommandAt(now);
        thread.setUpdatedAt(now);
        threadRepository.saveThread(thread);
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
                threadRepository.saveThread(thread);
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
        threadRepository.saveThread(thread);
        return thread;
    }
}
