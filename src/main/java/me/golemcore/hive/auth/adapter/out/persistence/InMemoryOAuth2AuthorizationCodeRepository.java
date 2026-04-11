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

package me.golemcore.hive.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.golemcore.hive.auth.application.OAuth2AuthorizationCode;
import me.golemcore.hive.auth.application.port.out.OAuth2AuthorizationCodeRepository;
import org.springframework.stereotype.Component;

@Component
public class InMemoryOAuth2AuthorizationCodeRepository implements OAuth2AuthorizationCodeRepository {

    private final Map<String, OAuth2AuthorizationCode> codes = new ConcurrentHashMap<>();

    @Override
    public void save(OAuth2AuthorizationCode authorizationCode) {
        pruneExpired();
        codes.put(authorizationCode.code(), authorizationCode);
    }

    @Override
    public Optional<OAuth2AuthorizationCode> consume(String code) {
        pruneExpired();
        return Optional.ofNullable(codes.remove(code));
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        codes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
