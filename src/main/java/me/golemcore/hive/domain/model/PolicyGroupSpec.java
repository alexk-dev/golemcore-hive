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
import java.util.LinkedHashMap;
import java.util.List;
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

    @Builder.Default
    private PolicyToolsConfig tools = new PolicyToolsConfig();

    @Builder.Default
    private PolicyMemoryConfig memory = new PolicyMemoryConfig();

    @Builder.Default
    private PolicyMcpConfig mcp = new PolicyMcpConfig();

    @Builder.Default
    private PolicyAutonomyConfig autonomy = new PolicyAutonomyConfig();

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyToolsConfig {
        private Boolean filesystemEnabled;
        private Boolean shellEnabled;
        private Boolean skillManagementEnabled;
        private Boolean skillTransitionEnabled;
        private Boolean tierEnabled;
        private Boolean goalManagementEnabled;

        @Builder.Default
        private List<PolicyEnvironmentVariable> shellEnvironmentVariables = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyEnvironmentVariable {
        private String name;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMemoryConfig {
        private Integer version;
        private Boolean enabled;
        private Integer softPromptBudgetTokens;
        private Integer maxPromptBudgetTokens;
        private Integer workingTopK;
        private Integer episodicTopK;
        private Integer semanticTopK;
        private Integer proceduralTopK;
        private Boolean promotionEnabled;
        private Double promotionMinConfidence;
        private Boolean decayEnabled;
        private Integer decayDays;
        private Integer retrievalLookbackDays;
        private Boolean codeAwareExtractionEnabled;

        @Builder.Default
        private PolicyMemoryDisclosureConfig disclosure = new PolicyMemoryDisclosureConfig();

        @Builder.Default
        private PolicyMemoryRerankingConfig reranking = new PolicyMemoryRerankingConfig();

        @Builder.Default
        private PolicyMemoryDiagnosticsConfig diagnostics = new PolicyMemoryDiagnosticsConfig();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMemoryDisclosureConfig {
        private String mode;
        private String promptStyle;
        private Boolean toolExpansionEnabled;
        private Boolean disclosureHintsEnabled;
        private Double detailMinScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMemoryRerankingConfig {
        private Boolean enabled;
        private String profile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMemoryDiagnosticsConfig {
        private String verbosity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMcpConfig {
        private Boolean enabled;
        private Integer defaultStartupTimeout;
        private Integer defaultIdleTimeout;

        @Builder.Default
        private List<PolicyMcpCatalogEntry> catalog = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyMcpCatalogEntry {
        private String name;
        private String description;
        private String command;

        @Builder.Default
        private Map<String, String> env = new LinkedHashMap<>();

        private Integer startupTimeoutSeconds;
        private Integer idleTimeoutMinutes;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyAutonomyConfig {
        private Boolean enabled;
        private Integer tickIntervalSeconds;
        private Integer taskTimeLimitMinutes;
        private Boolean autoStart;
        private Integer maxGoals;
        private String modelTier;
        private Boolean reflectionEnabled;
        private Integer reflectionFailureThreshold;
        private String reflectionModelTier;
        private Boolean reflectionTierPriority;
        private Boolean notifyMilestones;
    }
}
