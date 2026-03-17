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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class AtomicJsonFileStore {

    private final Path basePath;

    public AtomicJsonFileStore(Path basePath) {
        this.basePath = basePath.toAbsolutePath().normalize();
    }

    public void ensureDirectory(String directory) throws IOException {
        Files.createDirectories(resolve(directory, ""));
    }

    public void writeStringAtomic(String directory, String path, String content) throws IOException {
        Path targetPath = resolve(directory, path);
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
        try {
            Files.move(tempPath, targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public String readString(String directory, String path) throws IOException {
        Path filePath = resolve(directory, path);
        if (!Files.exists(filePath)) {
            return null;
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    public boolean exists(String directory, String path) {
        return Files.exists(resolve(directory, path));
    }

    public void delete(String directory, String path) throws IOException {
        Files.deleteIfExists(resolve(directory, path));
    }

    public List<String> list(String directory, String prefix) throws IOException {
        Path directoryPath = resolve(directory, "");
        if (!Files.exists(directoryPath)) {
            return Collections.emptyList();
        }

        Path prefixPath = prefix == null || prefix.isBlank()
                ? directoryPath
                : resolve(directory, prefix);
        if (!Files.exists(prefixPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.walk(prefixPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> directoryPath.relativize(path).toString())
                    .sorted()
                    .toList();
        }
    }

    Path resolve(String directory, String path) {
        Path resolved = basePath.resolve(directory).resolve(path).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Path traversal attempt blocked: " + directory + "/" + path);
        }
        return resolved;
    }
}
