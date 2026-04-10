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

package me.golemcore.hive.infrastructure.storage;

import java.util.List;

public interface StoragePort {

    void ensureDirectory(String directory);

    void putTextAtomic(String directory, String path, String content);

    String getText(String directory, String path);

    boolean exists(String directory, String path);

    void delete(String directory, String path);

    List<String> listObjects(String directory, String prefix);
}
