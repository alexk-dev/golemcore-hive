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

package me.golemcore.hive.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentToken {

    @Builder.Default
    private int schemaVersion = 2;

    private String id;
    private String secretHash;
    private String tokenPreview;
    private String note;
    private String createdByOperatorId;
    private String createdByOperatorUsername;
    private Instant createdAt;
    private Instant expiresAt;
    @JsonAlias("usedAt")
    private Instant lastUsedAt;
    private Instant revokedAt;
    @Builder.Default
    private long registrationCount = 0L;
    @JsonAlias("registeredGolemId")
    private String lastRegisteredGolemId;
    private String revokeReason;
    private boolean revoked;
}
