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

package me.golemcore.hive.governance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.PolicyGroupSpec;
import me.golemcore.hive.governance.application.port.out.PolicySpecCodecPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JacksonPolicySpecCodecAdapter implements PolicySpecCodecPort {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyGroupSpec copy(PolicyGroupSpec source) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(source), PolicyGroupSpec.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to copy policy payload", exception);
        }
    }

    @Override
    public String calculateChecksum(PolicyGroupSpec policyGroupSpec) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest
                    .digest(objectMapper.writeValueAsString(policyGroupSpec).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize policy group spec for checksum", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }
}
