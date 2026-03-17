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

package me.golemcore.hive.adapter.inbound.web.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token == null || !jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
            return chain.filter(exchange);
        }

        SubjectType subjectType = jwtTokenProvider.getSubjectType(token);
        List<SimpleGrantedAuthority> authorities = subjectType == SubjectType.OPERATOR
                ? jwtTokenProvider.getRoles(token).stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
                : jwtTokenProvider.getScopes(token).stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
        AuthenticatedActor actor = new AuthenticatedActor(
                subjectType,
                jwtTokenProvider.getSubjectId(token),
                jwtTokenProvider.getUsername(token),
                jwtTokenProvider.getRoles(token),
                jwtTokenProvider.getScopes(token),
                jwtTokenProvider.getSessionId(token));
        Authentication authentication = new UsernamePasswordAuthenticationToken(actor, null, authorities);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
