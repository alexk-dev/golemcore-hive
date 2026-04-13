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

package me.golemcore.hive.auth.application.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.auth.application.OAuth2AuthorizationCode;
import me.golemcore.hive.auth.application.OperatorAuthResult;
import me.golemcore.hive.auth.application.port.out.OAuth2AuthorizationCodeRepository;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;

@RequiredArgsConstructor
public class OAuth2AuthorizationApplicationService {

    private static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(2);
    private static final int CODE_BYTE_LENGTH = 32;
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("golem_[A-Za-z0-9_-]+|golem-[A-Za-z0-9_-]+");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuth2AuthorizationCodeRepository authorizationCodeRepository;
    private final OperatorAuthApplicationService operatorAuthApplicationService;
    private final GolemDirectoryUseCase golemDirectoryUseCase;

    public String authorize(String operatorId, String clientId, String redirectUri, String state,
            String codeChallenge, String codeChallengeMethod) {
        Golem golem = resolveEnabledSsoGolem(clientId);
        validateRedirectUri(golem, redirectUri);
        validateCodeChallenge(codeChallenge, codeChallengeMethod);
        String code = generateCode();
        authorizationCodeRepository.save(new OAuth2AuthorizationCode(
                code,
                golem.getId(),
                operatorId,
                redirectUri,
                codeChallenge,
                normalizeCodeChallengeMethod(codeChallengeMethod),
                Instant.now().plus(AUTHORIZATION_CODE_TTL)));
        String separator = redirectUri.contains("?") ? "&" : "?";
        StringBuilder result = new StringBuilder(redirectUri)
                .append(separator)
                .append("code=")
                .append(code);
        if (state != null && !state.isBlank()) {
            result.append("&state=").append(urlEncode(state));
        }
        return result.toString();
    }

    public Optional<OperatorAuthResult> exchange(String code, String clientId, String redirectUri,
            String codeVerifier) {
        OAuth2AuthorizationCode authorizationCode = authorizationCodeRepository.consume(code).orElse(null);
        if (authorizationCode == null || authorizationCode.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!authorizationCode.golemId().equals(clientId) || !authorizationCode.redirectUri().equals(redirectUri)) {
            return Optional.empty();
        }
        if (!isPkceVerifierAccepted(authorizationCode, codeVerifier)) {
            return Optional.empty();
        }
        Golem golem = resolveEnabledSsoGolem(clientId);
        validateRedirectUri(golem, redirectUri);
        return operatorAuthApplicationService.issueSsoTokens(authorizationCode.operatorId(), clientId);
    }

    private Golem resolveEnabledSsoGolem(String clientId) {
        validateClientId(clientId);
        Golem golem = golemDirectoryUseCase.findGolem(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown OAuth2 client: " + clientId));
        if (!golem.isDashboardSsoEnabled()) {
            throw new IllegalStateException("Dashboard SSO is disabled for golem " + clientId);
        }
        return golem;
    }

    private void validateClientId(String clientId) {
        if (clientId == null || !CLIENT_ID_PATTERN.matcher(clientId).matches()) {
            throw new IllegalArgumentException("client_id must reference a registered golem id");
        }
    }

    private void validateRedirectUri(Golem golem, String redirectUri) {
        URI requested = parseUri(redirectUri, "redirect_uri");
        String dashboardBaseUrl = resolveDashboardBaseUrl(golem);
        if (dashboardBaseUrl == null) {
            throw new IllegalStateException("Golem dashboard URL is not available for SSO");
        }
        URI allowedBaseUri = parseUri(dashboardBaseUrl, "golem dashboard URL");
        if (!sameOrigin(requested, allowedBaseUri) || !isDashboardCallbackPath(allowedBaseUri, requested)) {
            throw new IllegalArgumentException("redirect_uri is not registered for this golem");
        }
    }

    private String resolveDashboardBaseUrl(Golem golem) {
        if (golem.getLastHeartbeat() == null || golem.getLastHeartbeat().getDashboardBaseUrl() == null
                || golem.getLastHeartbeat().getDashboardBaseUrl().isBlank()) {
            return null;
        }
        return golem.getLastHeartbeat().getDashboardBaseUrl();
    }

    private URI parseUri(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException(fieldName + " must be an absolute URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid URI", exception);
        }
    }

    private boolean isDashboardCallbackPath(URI allowedBaseUri, URI requested) {
        String allowedPath = allowedBaseUri.getPath();
        if (allowedPath == null || allowedPath.isBlank() || "/".equals(allowedPath)) {
            return "/api/auth/hive/callback".equals(requested.getPath());
        }
        String normalizedAllowedPath = allowedPath.endsWith("/")
                ? allowedPath.substring(0, allowedPath.length() - 1)
                : allowedPath;
        return (normalizedAllowedPath + "/api/auth/hive/callback").equals(requested.getPath());
    }

    private boolean sameOrigin(URI requested, URI allowedBaseUri) {
        return requested.getScheme().equalsIgnoreCase(allowedBaseUri.getScheme())
                && requested.getHost().equalsIgnoreCase(allowedBaseUri.getHost())
                && requested.getPort() == allowedBaseUri.getPort();
    }

    private void validateCodeChallenge(String codeChallenge, String codeChallengeMethod) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return;
        }
        String normalizedMethod = normalizeCodeChallengeMethod(codeChallengeMethod);
        if (!"plain".equals(normalizedMethod)) {
            throw new IllegalArgumentException("Only plain PKCE code challenge method is supported");
        }
    }

    private String normalizeCodeChallengeMethod(String codeChallengeMethod) {
        return codeChallengeMethod != null && !codeChallengeMethod.isBlank()
                ? codeChallengeMethod.trim().toLowerCase(Locale.ROOT)
                : "plain";
    }

    private boolean isPkceVerifierAccepted(OAuth2AuthorizationCode authorizationCode, String codeVerifier) {
        String expectedChallenge = authorizationCode.codeChallenge();
        if (expectedChallenge == null || expectedChallenge.isBlank()) {
            return true;
        }
        if (!"plain".equals(authorizationCode.codeChallengeMethod())) {
            return false;
        }
        return expectedChallenge.equals(codeVerifier);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
