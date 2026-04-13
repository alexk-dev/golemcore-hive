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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.LoginResponse;
import me.golemcore.hive.adapter.inbound.web.dto.OperatorResponse;
import me.golemcore.hive.adapter.inbound.web.dto.oauth2.OAuth2MetadataResponse;
import me.golemcore.hive.adapter.inbound.web.dto.oauth2.OAuth2TokenRequest;
import me.golemcore.hive.adapter.inbound.web.dto.oauth2.OAuth2TokenResponse;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.adapter.inbound.web.security.RefreshCookieFactory;
import me.golemcore.hive.auth.application.OperatorAuthResult;
import me.golemcore.hive.auth.application.service.OAuth2AuthorizationApplicationService;
import me.golemcore.hive.auth.application.service.OperatorAuthApplicationService;
import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.domain.model.OperatorAccount;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2AuthorizationApplicationService authorizationApplicationService;
    private final OperatorAuthApplicationService operatorAuthApplicationService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final HiveProperties properties;

    @GetMapping("/.well-known")
    public Mono<ResponseEntity<OAuth2MetadataResponse>> metadata() {
        OAuth2MetadataResponse response = new OAuth2MetadataResponse(
                properties.getSecurity().getJwt().getIssuer(),
                "/api/v1/oauth2/authorize",
                "/api/v1/oauth2/token",
                "code",
                "authorization_code",
                "plain");
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/authorize")
    public Mono<ResponseEntity<Void>> authorize(
            Principal principal,
            ServerWebExchange exchange,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {
        return Mono.fromCallable(() -> {
            if (responseType != null && !"code".equals(responseType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only authorization code flow is supported");
            }
            OperatorAuthResult refreshed = null;
            String operatorId = resolveOperatorId(principal);
            if (operatorId == null) {
                if (!hasRefreshCookie(exchange)) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(buildLoginRedirect(exchange)))
                            .<Void>build();
                }
                refreshed = refreshOperatorSession(exchange);
                operatorId = refreshed.operator().getId();
            }
            String redirect = authorizationApplicationService.authorize(
                    operatorId,
                    clientId,
                    redirectUri,
                    state,
                    codeChallenge,
                    codeChallengeMethod);
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirect));
            if (refreshed != null && refreshed.refreshToken() != null) {
                responseBuilder.header("Set-Cookie", refreshCookieFactory.build(refreshed.refreshToken()).toString());
            }
            return responseBuilder.<Void>build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/token")
    public Mono<ResponseEntity<OAuth2TokenResponse>> token(@Valid @RequestBody OAuth2TokenRequest request) {
        return Mono.fromCallable(() -> {
            OperatorAuthResult result = authorizationApplicationService.exchange(
                    request.code(),
                    request.clientId(),
                    request.redirectUri(),
                    request.codeVerifier()).orElse(null);
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization code");
            }
            LoginResponse login = new LoginResponse(result.accessToken(), toOperatorResponse(result.operator()));
            return ResponseEntity.ok(new OAuth2TokenResponse(login, request.code()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveOperatorId(Principal principal) {
        try {
            AuthenticatedActor actor = ControllerActorSupport.requireOperatorActor(principal);
            return actor.getSubjectId();
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private boolean hasRefreshCookie(ServerWebExchange exchange) {
        return exchange.getRequest().getCookies()
                .getFirst(properties.getSecurity().getCookie().getRefreshName()) != null;
    }

    private OperatorAuthResult refreshOperatorSession(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies()
                .getFirst(properties.getSecurity().getCookie().getRefreshName());
        if (cookie == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hive operator session required");
        }
        return operatorAuthApplicationService.refresh(cookie.getValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid Hive operator session"));
    }

    private String buildLoginRedirect(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String returnTo = query == null || query.isBlank() ? path : path + "?" + query;
        return "/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }

    private OperatorResponse toOperatorResponse(OperatorAccount operator) {
        return new OperatorResponse(
                operator.getId(),
                operator.getUsername(),
                operator.getDisplayName(),
                operator.getRoles().stream().map(Enum::name).sorted().toList());
    }
}
