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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfEvolvingArtifactCompareProjection {

    @Builder.Default
    private int schemaVersion = 1;

    private String artifactStreamId;
    private String leftGolemId;
    private String rightGolemId;
    private String leftRevisionId;
    private String rightRevisionId;
    private String leftNormalizedHash;
    private String rightNormalizedHash;
    private Boolean sameContent;
    private Boolean leftStale;
    private Boolean rightStale;
    private String summary;
    private Integer normalizationSchemaVersion;
    private Instant projectedAt;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
