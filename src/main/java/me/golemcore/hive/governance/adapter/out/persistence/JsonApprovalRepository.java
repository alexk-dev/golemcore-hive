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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.ApprovalRequest;
import me.golemcore.hive.governance.application.port.out.ApprovalRepositoryPort;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonApprovalRepository implements ApprovalRepositoryPort {

    private static final String APPROVALS_DIR = "approvals";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public void save(ApprovalRequest approvalRequest) {
        try {
            storagePort.putTextAtomic(
                    APPROVALS_DIR,
                    approvalRequest.getId() + ".json",
                    objectMapper.writeValueAsString(approvalRequest));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize approval " + approvalRequest.getId(), exception);
        }
    }

    @Override
    public Optional<ApprovalRequest> findById(String approvalId) {
        String content = storagePort.getText(APPROVALS_DIR, approvalId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(content, ApprovalRequest.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize approval " + approvalId, exception);
        }
    }

    @Override
    public List<ApprovalRequest> findAll() {
        List<ApprovalRequest> approvalRequests = new ArrayList<>();
        for (String path : storagePort.listObjects(APPROVALS_DIR, "")) {
            String content = storagePort.getText(APPROVALS_DIR, path);
            if (content == null) {
                continue;
            }
            try {
                approvalRequests.add(objectMapper.readValue(content, ApprovalRequest.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to deserialize approval " + path, exception);
            }
        }
        return approvalRequests;
    }
}
