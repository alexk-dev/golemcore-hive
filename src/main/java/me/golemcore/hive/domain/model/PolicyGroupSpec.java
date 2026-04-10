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

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyGroupSpec {

    @Builder.Default
    private int schemaVersion = 1;

    @Builder.Default
    private Map<String, PolicyProviderConfig> llmProviders = new LinkedHashMap<>();

    @Builder.Default
    private PolicyModelRouter modelRouter = new PolicyModelRouter();

    @Builder.Default
    private PolicyModelCatalog modelCatalog = new PolicyModelCatalog();

    private String checksum;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyProviderConfig {
        private String apiKey;
        private String baseUrl;
        private Integer requestTimeoutSeconds;
        private String apiType;
        private Boolean legacyApi;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyModelRouter {
        private Double temperature;
        private PolicyTierBinding routing;

        @Builder.Default
        private Map<String, PolicyTierBinding> tiers = new LinkedHashMap<>();

        private Boolean dynamicTierEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyTierBinding {
        private String model;
        private String reasoning;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyModelCatalog {
        private String defaultModel;

        @Builder.Default
        private Map<String, PolicyModelConfig> models = new LinkedHashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyModelConfig {
        private String provider;
        private String displayName;
        private Boolean supportsVision;
        private Boolean supportsTemperature;
        private Integer maxInputTokens;
    }
}
