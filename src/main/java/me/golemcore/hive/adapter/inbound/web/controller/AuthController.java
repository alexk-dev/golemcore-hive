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

package me.golemcore.hive.adapter.inbound.web.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.LoginRequest;
import me.golemcore.hive.adapter.inbound.web.dto.LoginResponse;
import me.golemcore.hive.adapter.inbound.web.dto.OperatorResponse;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.adapter.inbound.web.security.RefreshCookieFactory;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.domain.service.AuthService;
import me.golemcore.hive.domain.service.AuditService;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final HiveProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.defer(() -> {
            AuthService.TokenPair tokens = authService.authenticate(request.username(), request.password());
            if (tokens == null) {
                auditService.record(AuditEvent.builder()
                        .eventType("auth.login_failed")
                        .severity("WARN")
                        .actorType("OPERATOR")
                        .actorName(request.username())
                        .targetType("SESSION")
                        .summary("Login failed")
                        .details("Invalid credentials"));
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
            }
            return Mono.fromCallable(() -> {
                OperatorAccount operator = authService.getOperatorByUsername(request.username());
                auditService.record(AuditEvent.builder()
                        .eventType("auth.login")
                        .severity("INFO")
                        .actorType("OPERATOR")
                        .actorId(operator.getId())
                        .actorName(operator.getUsername())
                        .targetType("SESSION")
                        .targetId(operator.getId())
                        .summary("Operator logged in"));
                return ResponseEntity.ok()
                        .header("Set-Cookie", refreshCookieFactory.build(tokens.getRefreshToken()).toString())
                        .body(new LoginResponse(tokens.getAccessToken(), toOperatorResponse(operator)));
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<LoginResponse>> refresh(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            HttpCookie cookie = exchange.getRequest().getCookies()
                    .getFirst(properties.getSecurity().getCookie().getRefreshName());
            if (cookie == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
            }
            AuthService.TokenPair tokens = authService.refreshAccessToken(cookie.getValue());
            if (tokens == null) {
                auditService.record(AuditEvent.builder()
                        .eventType("auth.refresh_failed")
                        .severity("WARN")
                        .actorType("OPERATOR")
                        .targetType("SESSION")
                        .summary("Refresh token rejected"));
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
            }
            OperatorAccount operator = authService
                    .getOperatorByUsername(jwtTokenProvider.getUsername(tokens.getAccessToken()));
            auditService.record(AuditEvent.builder()
                    .eventType("auth.refresh")
                    .severity("INFO")
                    .actorType("OPERATOR")
                    .actorId(operator.getId())
                    .actorName(operator.getUsername())
                    .targetType("SESSION")
                    .targetId(operator.getId())
                    .summary("Access token refreshed"));
            return ResponseEntity.ok()
                    .header("Set-Cookie", refreshCookieFactory.build(tokens.getRefreshToken()).toString())
                    .body(new LoginResponse(tokens.getAccessToken(), toOperatorResponse(operator)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
            HttpCookie cookie = exchange.getRequest().getCookies()
                    .getFirst(properties.getSecurity().getCookie().getRefreshName());
            if (cookie != null) {
                String token = cookie.getValue();
                if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isRefreshToken(token)) {
                    String operatorId = jwtTokenProvider.getOperatorId(token);
                    String username = jwtTokenProvider.getUsername(token);
                    auditService.record(AuditEvent.builder()
                            .eventType("auth.logout")
                            .severity("INFO")
                            .actorType("OPERATOR")
                            .actorId(operatorId)
                            .actorName(username)
                            .targetType("SESSION")
                            .targetId(operatorId)
                            .summary("Operator logged out"));
                }
                authService.logout(cookie.getValue());
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.ok()
                        .header("Set-Cookie", refreshCookieFactory.clear().toString())
                        .build());
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<OperatorResponse>> me(Principal principal) {
        return Mono.fromCallable(() -> {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
            }
            OperatorAccount operator = authService.getOperatorByUsername(principal.getName());
            if (operator == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown operator");
            }
            return ResponseEntity.ok(toOperatorResponse(operator));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private OperatorResponse toOperatorResponse(OperatorAccount operator) {
        if (operator == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown operator");
        }
        List<String> roles = operator.getRoles().stream().map(Enum::name).sorted().toList();
        return new OperatorResponse(operator.getId(), operator.getUsername(), operator.getDisplayName(), roles);
    }
}
