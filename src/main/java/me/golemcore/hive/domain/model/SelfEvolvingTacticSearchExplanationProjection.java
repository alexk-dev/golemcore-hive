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
public class SelfEvolvingTacticSearchExplanationProjection {

    private String searchMode;
    private String degradedReason;
    private Double bm25Score;
    private Double vectorScore;
    private Double rrfScore;
    private Double qualityPrior;
    private Double mmrDiversityAdjustment;
    private Double negativeMemoryPenalty;
    private Double personalizationBoost;
    private String rerankerVerdict;

    @Builder.Default
    private List<String> matchedQueryViews = new ArrayList<>();

    @Builder.Default
    private List<String> matchedTerms = new ArrayList<>();

    private Boolean eligible;
    private String gatingReason;
    private Double finalScore;
}
