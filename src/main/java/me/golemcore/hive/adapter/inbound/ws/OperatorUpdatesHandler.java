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
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.adapter.inbound.web.security.SubjectType;
import me.golemcore.hive.domain.model.OperatorUpdate;
import me.golemcore.hive.execution.application.port.in.OperatorUpdateStreamUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OperatorUpdatesHandler implements WebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final OperatorUpdateStreamUseCase operatorUpdateStreamUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null
                || !jwtTokenProvider.validateToken(token)
                || !jwtTokenProvider.isAccessToken(token)
                || jwtTokenProvider.getSubjectType(token) != SubjectType.OPERATOR) {
            return session.close();
        }

        Flux<String> serializedUpdates = Flux.from(operatorUpdateStreamUseCase.updates()).map(this::serializeUpdate);
        Mono<Void> output = session.send(serializedUpdates.map(session::textMessage));
        Mono<Void> input = session.receive().then();
        return Mono.when(input, output);
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("access_token=")) {
                    return java.net.URLDecoder.decode(pair.substring("access_token=".length()),
                            java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        String authorizationHeader = session.getHandshakeInfo().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring("Bearer ".length());
        }
        return null;
    }

    private String serializeUpdate(OperatorUpdate update) {
        try {
            return objectMapper.writeValueAsString(update);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize operator update", exception);
        }
    }
}
