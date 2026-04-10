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

package me.golemcore.hive.fleet.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import me.golemcore.hive.config.JacksonConfig;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.junit.jupiter.api.Test;

class JsonEnrollmentTokenRepositoryTest {

    @Test
    void shouldReadLegacyEnrollmentTokenAliasesFromStoredJson() {
        StoragePort storagePort = mock(StoragePort.class);
        JsonEnrollmentTokenRepository repository = new JsonEnrollmentTokenRepository(
                storagePort,
                new JacksonConfig().objectMapper());
        when(storagePort.getText("enrollment-tokens", "tok-1.json")).thenReturn("""
                {
                  "id": "tok-1",
                  "secretHash": "hash",
                  "tokenPreview": "tok_***",
                  "schemaVersion": 1,
                  "usedAt": "2026-04-09T00:00:00Z",
                  "registeredGolemId": "golem-1",
                  "extraField": "ignored"
                }
                """);

        EnrollmentToken enrollmentToken = repository.findById("tok-1").orElseThrow();

        assertEquals("tok-1", enrollmentToken.getId());
        assertEquals(2, enrollmentToken.getSchemaVersion());
        assertEquals(Instant.parse("2026-04-09T00:00:00Z"), enrollmentToken.getLastUsedAt());
        assertEquals("golem-1", enrollmentToken.getLastRegisteredGolemId());
        assertEquals(1L, enrollmentToken.getRegistrationCount());
        assertTrue(repository.findById("missing").isEmpty());
    }
}
