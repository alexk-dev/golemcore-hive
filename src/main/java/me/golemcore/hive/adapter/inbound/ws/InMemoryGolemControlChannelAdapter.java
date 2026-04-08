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

package me.golemcore.hive.adapter.inbound.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ControlCommandEnvelope;
import me.golemcore.hive.port.outbound.GolemControlDispatchPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@RequiredArgsConstructor
public class InMemoryGolemControlChannelAdapter implements GolemControlDispatchPort {

    private final ObjectMapper objectMapper;
    private final Map<String, Sinks.Many<String>> sessions = new ConcurrentHashMap<>();

    public Flux<String> register(String golemId, Sinks.Many<String> sink) {
        Sinks.Many<String> previous = sessions.put(golemId, sink);
        if (previous != null) {
            previous.tryEmitComplete();
        }
        return sink.asFlux();
    }

    public void unregister(String golemId, Sinks.Many<String> sink) {
        sessions.remove(golemId, sink);
        sink.tryEmitComplete();
    }

    @Override
    public boolean isConnected(String golemId) {
        return sessions.containsKey(golemId);
    }

    @Override
    public boolean send(String golemId, ControlCommandEnvelope envelope) {
        Sinks.Many<String> sink = sessions.get(golemId);
        if (sink == null) {
            return false;
        }
        return sink.tryEmitNext(toJson(envelope)).isSuccess();
    }

    private String toJson(ControlCommandEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize control command envelope", exception);
        }
    }
}
