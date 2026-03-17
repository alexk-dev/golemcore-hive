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

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.config.HiveProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final HiveProperties properties;

    public ResponseCookie build(String refreshToken) {
        return ResponseCookie.from(properties.getSecurity().getCookie().getRefreshName(), refreshToken)
                .httpOnly(true)
                .secure(properties.getSecurity().getCookie().isSecure())
                .path(properties.getSecurity().getCookie().getPath())
                .maxAge(Duration.ofDays(properties.getSecurity().getJwt().getRefreshExpirationDays()))
                .sameSite(properties.getSecurity().getCookie().getSameSite())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(properties.getSecurity().getCookie().getRefreshName(), "")
                .httpOnly(true)
                .secure(properties.getSecurity().getCookie().isSecure())
                .path(properties.getSecurity().getCookie().getPath())
                .maxAge(Duration.ZERO)
                .sameSite(properties.getSecurity().getCookie().getSameSite())
                .build();
    }
}
