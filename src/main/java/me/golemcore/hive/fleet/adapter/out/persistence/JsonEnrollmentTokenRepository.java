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

package me.golemcore.hive.fleet.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.fleet.application.port.out.EnrollmentTokenRepository;
import me.golemcore.hive.port.outbound.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonEnrollmentTokenRepository implements EnrollmentTokenRepository {

    private static final String ENROLLMENT_TOKENS_DIR = "enrollment-tokens";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<EnrollmentToken> findById(String tokenId) {
        String content = storagePort.getText(ENROLLMENT_TOKENS_DIR, tokenId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readEnrollmentToken(content, tokenId));
    }

    @Override
    public List<EnrollmentToken> list() {
        List<EnrollmentToken> tokens = new ArrayList<>();
        for (String path : storagePort.listObjects(ENROLLMENT_TOKENS_DIR, "")) {
            String content = storagePort.getText(ENROLLMENT_TOKENS_DIR, path);
            if (content == null) {
                continue;
            }
            tokens.add(readEnrollmentToken(content, path));
        }
        return tokens;
    }

    @Override
    public void save(EnrollmentToken enrollmentToken) {
        try {
            storagePort.putTextAtomic(ENROLLMENT_TOKENS_DIR, enrollmentToken.getId() + ".json",
                    objectMapper.writeValueAsString(enrollmentToken));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize enrollment token " + enrollmentToken.getId(),
                    exception);
        }
    }

    private EnrollmentToken readEnrollmentToken(String content, String tokenRef) {
        try {
            EnrollmentToken enrollmentToken = objectMapper.readValue(content, EnrollmentToken.class);
            if (enrollmentToken.getSchemaVersion() < 2) {
                enrollmentToken.setSchemaVersion(2);
            }
            if (enrollmentToken.getRegistrationCount() <= 0L && enrollmentToken.getLastUsedAt() != null) {
                enrollmentToken.setRegistrationCount(1L);
            }
            return enrollmentToken;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize enrollment token " + tokenRef, exception);
        }
    }
}
