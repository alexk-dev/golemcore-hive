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

package me.golemcore.hive.execution.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.execution.application.port.out.CardLifecycleSignalRepository;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonCardLifecycleSignalRepository implements CardLifecycleSignalRepository {

    private static final String LIFECYCLE_SIGNALS_DIR = "lifecycle-signals";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<CardLifecycleSignal> listByThreadId(String threadId) {
        List<CardLifecycleSignal> signals = new ArrayList<>();
        for (String path : storagePort.listObjects(LIFECYCLE_SIGNALS_DIR, "")) {
            String content = storagePort.getText(LIFECYCLE_SIGNALS_DIR, path);
            if (content == null) {
                continue;
            }
            CardLifecycleSignal signal = readSignal(content, path);
            if (threadId.equals(signal.getThreadId())) {
                signals.add(signal);
            }
        }
        signals.sort(Comparator.comparing(CardLifecycleSignal::getCreatedAt).thenComparing(CardLifecycleSignal::getId));
        return signals;
    }

    @Override
    public void save(CardLifecycleSignal signal) {
        try {
            storagePort.putTextAtomic(
                    LIFECYCLE_SIGNALS_DIR,
                    signal.getId() + ".json",
                    objectMapper.writeValueAsString(signal));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize lifecycle signal " + signal.getId(), exception);
        }
    }

    private CardLifecycleSignal readSignal(String content, String signalRef) {
        try {
            return objectMapper.readValue(content, CardLifecycleSignal.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize lifecycle signal " + signalRef, exception);
        }
    }
}
