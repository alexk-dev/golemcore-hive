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

package me.golemcore.hive.adapter.outbound.storage;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteReadListAndDeleteFilesAtomically() throws Exception {
        AtomicJsonFileStore fileStore = new AtomicJsonFileStore(tempDir);

        fileStore.ensureDirectory("operators");
        fileStore.writeStringAtomic("operators", "admin.json", "{\"username\":\"admin\"}");

        String content = fileStore.readString("operators", "admin.json");
        List<String> listed = fileStore.list("operators", "");

        Assertions.assertEquals("{\"username\":\"admin\"}", content);
        Assertions.assertEquals(List.of("admin.json"), listed);

        fileStore.delete("operators", "admin.json");

        Assertions.assertFalse(fileStore.exists("operators", "admin.json"));
    }

    @Test
    void shouldRejectPathTraversalAttempt() {
        AtomicJsonFileStore fileStore = new AtomicJsonFileStore(tempDir);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> fileStore.writeStringAtomic("operators", "../../escape.json", "{}"));

        Assertions.assertTrue(exception.getMessage().contains("Path traversal"));
    }
}
