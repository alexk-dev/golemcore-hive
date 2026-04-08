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
import java.net.URI;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.golems.ActionReasonRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenCreateRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenCreatedResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenResponse;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.fleet.application.ActorContext;
import me.golemcore.hive.fleet.application.CreatedEnrollmentToken;
import me.golemcore.hive.fleet.application.port.in.GolemEnrollmentUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/enrollment-tokens")
@RequiredArgsConstructor
public class EnrollmentController {

    private final GolemEnrollmentUseCase golemEnrollmentUseCase;
    private final HiveProperties properties;

    @PostMapping
    public Mono<ResponseEntity<EnrollmentTokenCreatedResponse>> createEnrollmentToken(
            Principal principal,
            ServerHttpRequest request,
            @Valid @RequestBody(required = false) EnrollmentTokenCreateRequest requestBody) {
        return Mono.fromCallable(() -> {
            ActorContext actor = toActorContext(ControllerActorSupport.requireOperatorActor(principal));
            CreatedEnrollmentToken created = golemEnrollmentUseCase.createEnrollmentToken(
                    actor,
                    requestBody != null ? requestBody.note() : null,
                    requestBody != null ? requestBody.expiresInMinutes() : null);
            EnrollmentToken token = created.token();
            return ResponseEntity.status(HttpStatus.CREATED).body(new EnrollmentTokenCreatedResponse(
                    token.getId(),
                    created.revealedToken(),
                    created.revealedToken() + ":" + resolveJoinBaseUrl(request),
                    token.getTokenPreview(),
                    token.getNote(),
                    token.getCreatedAt(),
                    token.getExpiresAt()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public Mono<ResponseEntity<List<EnrollmentTokenResponse>>> listEnrollmentTokens(Principal principal) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<EnrollmentTokenResponse> tokens = golemEnrollmentUseCase.listEnrollmentTokens().stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(tokens);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{tokenId}:revoke")
    public Mono<ResponseEntity<EnrollmentTokenResponse>> revokeEnrollmentToken(
            Principal principal,
            @PathVariable String tokenId,
            @RequestBody(required = false) ActionReasonRequest request) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            EnrollmentToken token = golemEnrollmentUseCase.revokeEnrollmentToken(tokenId,
                    request != null ? request.reason() : null);
            return ResponseEntity.ok(toResponse(token));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private EnrollmentTokenResponse toResponse(EnrollmentToken token) {
        return new EnrollmentTokenResponse(
                token.getId(),
                token.getTokenPreview(),
                token.getNote(),
                token.getCreatedByOperatorUsername(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getLastUsedAt(),
                token.getRegistrationCount(),
                token.getRevokedAt(),
                token.getLastRegisteredGolemId(),
                token.getRevokeReason(),
                token.isRevoked());
    }

    private String resolveJoinBaseUrl(ServerHttpRequest request) {
        String configuredBaseUrl = properties.getFleet().getPublicBaseUrl();
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return trimTrailingSlash(configuredBaseUrl);
        }
        URI requestUri = request.getURI();
        String scheme = requestUri.getScheme() != null ? requestUri.getScheme() : "http";
        String host = requestUri.getHost() != null ? requestUri.getHost() : "localhost";
        int port = requestUri.getPort();
        String portSuffix = shouldIncludePort(scheme, port) ? ":" + port : "";
        return scheme + "://" + host + portSuffix;
    }

    private boolean shouldIncludePort(String scheme, int port) {
        if (port < 0) {
            return false;
        }
        if ("http".equalsIgnoreCase(scheme) && port == 80) {
            return false;
        }
        return !"https".equalsIgnoreCase(scheme) || port != 443;
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private ActorContext toActorContext(me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor actor) {
        return new ActorContext(actor.getSubjectId(), actor.getName());
    }
}
