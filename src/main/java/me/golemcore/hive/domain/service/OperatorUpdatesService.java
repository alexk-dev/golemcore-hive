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
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.OperatorUpdate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
public class OperatorUpdatesService {

    private final ObjectMapper objectMapper;

    private final Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();

    public Flux<String> updates() {
        return sink.asFlux();
    }

    public void publish(OperatorUpdate update) {
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(update));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize operator update", exception);
        }
    }
}
