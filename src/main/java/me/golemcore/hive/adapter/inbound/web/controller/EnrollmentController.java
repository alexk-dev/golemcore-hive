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
import me.golemcore.hive.adapter.inbound.web.dto.golems.ActionReasonRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenCreateRequest;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenCreatedResponse;
import me.golemcore.hive.adapter.inbound.web.dto.golems.EnrollmentTokenResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.service.EnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    private final EnrollmentService enrollmentService;

    @PostMapping
    public Mono<ResponseEntity<EnrollmentTokenCreatedResponse>> createEnrollmentToken(
            Principal principal,
            @Valid @RequestBody(required = false) EnrollmentTokenCreateRequest request) {
        return Mono.fromCallable(() -> {
            AuthenticatedActor actor = requireOperatorActor(principal);
            EnrollmentService.CreatedEnrollmentToken created = enrollmentService.createEnrollmentToken(
                    actor,
                    request != null ? request.note() : null,
                    request != null ? request.expiresInMinutes() : null);
            EnrollmentToken token = created.getToken();
            return ResponseEntity.status(HttpStatus.CREATED).body(new EnrollmentTokenCreatedResponse(
                    token.getId(),
                    created.getRevealedToken(),
                    token.getTokenPreview(),
                    token.getNote(),
                    token.getCreatedAt(),
                    token.getExpiresAt()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public Mono<ResponseEntity<List<EnrollmentTokenResponse>>> listEnrollmentTokens(Principal principal) {
        return Mono.fromCallable(() -> {
            requireOperatorActor(principal);
            List<EnrollmentTokenResponse> tokens = enrollmentService.listEnrollmentTokens().stream()
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
            requireOperatorActor(principal);
            EnrollmentToken token = enrollmentService.revokeEnrollmentToken(tokenId, request != null ? request.reason() : null);
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
                token.getUsedAt(),
                token.getRevokedAt(),
                token.getRegisteredGolemId(),
                token.getRevokeReason(),
                token.isRevoked());
    }

    private AuthenticatedActor requireOperatorActor(Principal principal) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isOperator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator token required");
        }
        return actor;
    }

    private AuthenticatedActor extractActor(Principal principal) {
        if (principal instanceof AuthenticatedActor actor) {
            return actor;
        }
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return actor;
        }
        return null;
    }
}
