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

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class LocalJsonStorageAdapter implements StoragePort {

    private final HiveProperties properties;

    private AtomicJsonFileStore fileStore;

    @PostConstruct
    public void init() {
        Path basePath = Paths.get(properties.getStorage().getBasePath()).toAbsolutePath().normalize();
        this.fileStore = new AtomicJsonFileStore(basePath);
        try {
            fileStore.ensureDirectory("");
            fileStore.ensureDirectory("operators");
            fileStore.ensureDirectory("auth/refresh-sessions");
            fileStore.ensureDirectory("auth/golem-refresh-sessions");
            fileStore.ensureDirectory("golems");
            fileStore.ensureDirectory("golem-roles");
            fileStore.ensureDirectory("enrollment-tokens");
            fileStore.ensureDirectory("heartbeats");
            fileStore.ensureDirectory("meta");
            log.info("[Storage] Local JSON storage initialized at {}", basePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize storage directories", exception);
        }
    }

    @Override
    public void ensureDirectory(String directory) {
        try {
            fileStore.ensureDirectory(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directory: " + directory, exception);
        }
    }

    @Override
    public void putTextAtomic(String directory, String path, String content) {
        try {
            fileStore.writeStringAtomic(directory, path, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file: " + directory + "/" + path, exception);
        }
    }

    @Override
    public String getText(String directory, String path) {
        try {
            return fileStore.readString(directory, path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + directory + "/" + path, exception);
        }
    }

    @Override
    public boolean exists(String directory, String path) {
        return fileStore.exists(directory, path);
    }

    @Override
    public void delete(String directory, String path) {
        try {
            fileStore.delete(directory, path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete file: " + directory + "/" + path, exception);
        }
    }

    @Override
    public List<String> listObjects(String directory, String prefix) {
        try {
            return fileStore.list(directory, prefix);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list files: " + directory + "/" + prefix, exception);
        }
    }
}
