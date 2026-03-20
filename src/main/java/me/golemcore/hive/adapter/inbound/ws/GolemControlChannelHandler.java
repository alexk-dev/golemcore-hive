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

import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.adapter.inbound.web.security.SubjectType;
import me.golemcore.hive.domain.model.GolemScope;
import me.golemcore.hive.domain.service.CommandDispatchService;
import me.golemcore.hive.domain.service.GolemControlChannelService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
@RequiredArgsConstructor
public class GolemControlChannelHandler implements WebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final GolemControlChannelService golemControlChannelService;
    private final CommandDispatchService commandDispatchService;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null
                || !jwtTokenProvider.validateToken(token)
                || !jwtTokenProvider.isAccessToken(token)
                || jwtTokenProvider.getSubjectType(token) != SubjectType.GOLEM
                || !jwtTokenProvider.getScopes(token).contains(GolemScope.CONTROL_CONNECT.value())) {
            return session.close();
        }

        String golemId = jwtTokenProvider.getSubjectId(token);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Mono<Void> output = session.send(golemControlChannelService.register(golemId, sink).map(session::textMessage));
        Mono<Void> input = session.receive().then();

        commandDispatchService.dispatchPendingCommands(golemId);
        return Mono.when(input, output)
                .doFinally(signalType -> golemControlChannelService.unregister(golemId, sink));
    }

    private String extractToken(WebSocketSession session) {
        String accessToken = session.getHandshakeInfo().getUri().getQuery() != null
                ? session.getHandshakeInfo().getUri().getQuery()
                : "";
        for (String pair : accessToken.split("&")) {
            if (pair.startsWith("access_token=")) {
                return java.net.URLDecoder.decode(pair.substring("access_token=".length()),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        String authorizationHeader = session.getHandshakeInfo().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring("Bearer ".length());
        }
        return null;
    }
}
