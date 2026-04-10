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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import me.golemcore.hive.config.JacksonConfig;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.junit.jupiter.api.Test;

class JsonThreadRepositoryTest {

    @Test
    void shouldIgnoreUnknownFieldsWhenReadingStoredThreadJson() {
        StoragePort storagePort = mock(StoragePort.class);
        JsonThreadRepository repository = new JsonThreadRepository(storagePort, new JacksonConfig().objectMapper());
        when(storagePort.getText("threads", "thread-1.json")).thenReturn("""
                {
                  "id": "thread-1",
                  "boardId": "board-1",
                  "cardId": "card-1",
                  "title": "Thread 1",
                  "createdAt": "2026-04-09T00:00:00Z",
                  "updatedAt": "2026-04-09T00:05:00Z",
                  "legacyField": "ignored"
                }
                """);

        ThreadRecord threadRecord = repository.findThread("thread-1").orElseThrow();

        assertEquals("thread-1", threadRecord.getId());
        assertEquals("board-1", threadRecord.getBoardId());
        assertEquals("card-1", threadRecord.getCardId());
        assertEquals("Thread 1", threadRecord.getTitle());
        assertEquals(Instant.parse("2026-04-09T00:00:00Z"), threadRecord.getCreatedAt());
        assertTrue(repository.findThread("missing").isEmpty());
    }
}
