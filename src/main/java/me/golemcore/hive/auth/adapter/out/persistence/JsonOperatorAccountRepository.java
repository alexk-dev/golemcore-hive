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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.auth.application.port.out.OperatorAccountRepository;
import me.golemcore.hive.domain.model.OperatorAccount;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonOperatorAccountRepository implements OperatorAccountRepository {

    private static final String OPERATORS_DIR = "operators";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<OperatorAccount> findById(String operatorId) {
        String content = storagePort.getText(OPERATORS_DIR, operatorId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readOperator(content, operatorId));
    }

    @Override
    public Optional<OperatorAccount> findByUsername(String username) {
        return list().stream()
                .filter(operatorAccount -> operatorAccount.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public List<OperatorAccount> list() {
        List<OperatorAccount> operators = new ArrayList<>();
        for (String path : storagePort.listObjects(OPERATORS_DIR, "")) {
            String content = storagePort.getText(OPERATORS_DIR, path);
            if (content == null) {
                continue;
            }
            operators.add(readOperator(content, path));
        }
        return operators;
    }

    @Override
    public void save(OperatorAccount operatorAccount) {
        try {
            storagePort.putTextAtomic(
                    OPERATORS_DIR,
                    operatorAccount.getId() + ".json",
                    objectMapper.writeValueAsString(operatorAccount));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize operator " + operatorAccount.getId(), exception);
        }
    }

    private OperatorAccount readOperator(String content, String operatorRef) {
        try {
            return objectMapper.readValue(content, OperatorAccount.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize operator " + operatorRef, exception);
        }
    }
}
