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

package me.golemcore.hive.domain.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.SelfEvolvingArtifactCatalogProjection;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfEvolvingArtifactProjectionRebuildService {

    private final SelfEvolvingProjectionService selfEvolvingProjectionService;

    public RebuildResult rebuildAll() {
        List<SelfEvolvingArtifactCatalogProjection> artifacts = selfEvolvingProjectionService
                .searchArtifacts(null, null, null, null, null, null, null);
        List<String> staleArtifactRefs = new ArrayList<>();
        for (SelfEvolvingArtifactCatalogProjection artifact : artifacts) {
            if (Boolean.TRUE.equals(artifact.getStale())) {
                staleArtifactRefs.add(artifact.getGolemId() + ":" + artifact.getArtifactStreamId());
            }
        }
        return new RebuildResult(artifacts.size(), staleArtifactRefs.size(), staleArtifactRefs);
    }

    public record RebuildResult(int scannedArtifacts, int staleArtifacts, List<String> staleArtifactRefs) {
    }
}
