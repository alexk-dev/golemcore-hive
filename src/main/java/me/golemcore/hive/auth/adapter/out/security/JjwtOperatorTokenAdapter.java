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

package me.golemcore.hive.auth.adapter.out.security;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.security.JwtTokenProvider;
import me.golemcore.hive.auth.application.OperatorRefreshTokenClaims;
import me.golemcore.hive.auth.application.port.out.OperatorTokenPort;
import me.golemcore.hive.domain.model.OperatorAccount;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JjwtOperatorTokenAdapter implements OperatorTokenPort {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public String issueAccessToken(OperatorAccount operatorAccount) {
        return jwtTokenProvider.generateAccessToken(operatorAccount);
    }

    @Override
    public String issueRefreshToken(OperatorAccount operatorAccount, String sessionId) {
        return jwtTokenProvider.generateRefreshToken(operatorAccount, sessionId);
    }

    @Override
    public Optional<OperatorRefreshTokenClaims> parseRefreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return Optional.empty();
        }
        return Optional.of(new OperatorRefreshTokenClaims(
                jwtTokenProvider.getOperatorId(refreshToken),
                jwtTokenProvider.getUsername(refreshToken),
                jwtTokenProvider.getSessionId(refreshToken)));
    }
}
