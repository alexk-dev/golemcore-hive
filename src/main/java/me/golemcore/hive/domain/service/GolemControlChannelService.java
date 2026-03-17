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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class GolemControlChannelService {

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

    public boolean isConnected(String golemId) {
        return sessions.containsKey(golemId);
    }

    public boolean send(String golemId, String payload) {
        Sinks.Many<String> sink = sessions.get(golemId);
        if (sink == null) {
            return false;
        }
        return sink.tryEmitNext(payload).isSuccess();
    }
}
