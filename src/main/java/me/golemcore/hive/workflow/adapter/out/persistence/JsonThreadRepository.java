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

package me.golemcore.hive.workflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ThreadMessage;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.ThreadRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonThreadRepository implements ThreadRepository {

    private static final String THREADS_DIR = "threads";
    private static final String THREAD_MESSAGES_DIR = "thread-messages";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<ThreadRecord> listThreads() {
        List<ThreadRecord> threads = new ArrayList<>();
        for (String path : storagePort.listObjects(THREADS_DIR, "")) {
            String content = storagePort.getText(THREADS_DIR, path);
            if (content == null) {
                continue;
            }
            threads.add(readThread(content, path));
        }
        return threads;
    }

    @Override
    public Optional<ThreadRecord> findThread(String threadId) {
        String content = storagePort.getText(THREADS_DIR, threadId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readThread(content, threadId));
    }

    @Override
    public void saveThread(ThreadRecord thread) {
        try {
            storagePort.putTextAtomic(THREADS_DIR, thread.getId() + ".json", objectMapper.writeValueAsString(thread));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread " + thread.getId(), exception);
        }
    }

    @Override
    public List<ThreadMessage> listMessages(String threadId) {
        List<ThreadMessage> messages = new ArrayList<>();
        for (String path : storagePort.listObjects(THREAD_MESSAGES_DIR, "")) {
            String content = storagePort.getText(THREAD_MESSAGES_DIR, path);
            if (content == null) {
                continue;
            }
            ThreadMessage message = readMessage(content, path);
            if (threadId.equals(message.getThreadId())) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void saveMessage(ThreadMessage message) {
        try {
            storagePort.putTextAtomic(
                    THREAD_MESSAGES_DIR,
                    message.getId() + ".json",
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread message " + message.getId(), exception);
        }
    }

    private ThreadRecord readThread(String content, String threadRef) {
        try {
            return objectMapper.readValue(content, ThreadRecord.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread " + threadRef, exception);
        }
    }

    private ThreadMessage readMessage(String content, String messageRef) {
        try {
            return objectMapper.readValue(content, ThreadMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread message " + messageRef, exception);
        }
    }
}
