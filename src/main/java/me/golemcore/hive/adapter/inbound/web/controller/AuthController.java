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
import me.golemcore.hive.auth.application.OperatorAuthResult;
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.AuditEvent;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.governance.application.port.in.GovernanceOperationsUseCase;
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

    private final OperatorAuthApplicationService operatorAuthApplicationService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final HiveProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final GovernanceOperationsUseCase governanceOperationsUseCase;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.defer(() -> {
            OperatorAuthResult result = operatorAuthApplicationService
                    .authenticate(request.username(), request.password())
                    .orElse(null);
            if (result == null) {
                recordAuditEvent(AuditEvent.builder()
                        .eventType("auth.login_failed")
                        .severity("WARN")
                        .actorType("OPERATOR")
                        .actorName(request.username())
                        .targetType("SESSION")
                        .summary("Login failed")
                        .details("Invalid credentials")
                        .build());
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
            }
            return Mono.fromCallable(() -> {
                OperatorAccount operator = result.operator();
                recordAuditEvent(AuditEvent.builder()
                        .eventType("auth.login")
                        .severity("INFO")
                        .actorType("OPERATOR")
                        .actorId(operator.getId())
                        .actorName(operator.getUsername())
                        .targetType("SESSION")
                        .targetId(operator.getId())
                        .summary("Operator logged in")
                        .build());
                return ResponseEntity.ok()
                        .header("Set-Cookie", refreshCookieFactory.build(result.refreshToken()).toString())
                        .body(new LoginResponse(result.accessToken(), toOperatorResponse(operator)));
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
            OperatorAuthResult result = operatorAuthApplicationService.refresh(cookie.getValue()).orElse(null);
            if (result == null) {
                recordAuditEvent(AuditEvent.builder()
                        .eventType("auth.refresh_failed")
                        .severity("WARN")
                        .actorType("OPERATOR")
                        .targetType("SESSION")
                        .summary("Refresh token rejected")
                        .build());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
            }
            OperatorAccount operator = result.operator();
            recordAuditEvent(AuditEvent.builder()
                    .eventType("auth.refresh")
                    .severity("INFO")
                    .actorType("OPERATOR")
                    .actorId(operator.getId())
                    .actorName(operator.getUsername())
                    .targetType("SESSION")
                    .targetId(operator.getId())
                    .summary("Access token refreshed")
                    .build());
            return ResponseEntity.ok()
                    .header("Set-Cookie", refreshCookieFactory.build(result.refreshToken()).toString())
                    .body(new LoginResponse(result.accessToken(), toOperatorResponse(operator)));
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
                    recordAuditEvent(AuditEvent.builder()
                            .eventType("auth.logout")
                            .severity("INFO")
                            .actorType("OPERATOR")
                            .actorId(operatorId)
                            .actorName(username)
                            .targetType("SESSION")
                            .targetId(operatorId)
                            .summary("Operator logged out")
                            .build());
                }
                operatorAuthApplicationService.logout(cookie.getValue());
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
            OperatorAccount operator = operatorAuthApplicationService.findOperatorByUsername(principal.getName())
                    .orElse(null);
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

    private void recordAuditEvent(AuditEvent event) {
        governanceOperationsUseCase.recordAuditEvent(event);
    }
}
