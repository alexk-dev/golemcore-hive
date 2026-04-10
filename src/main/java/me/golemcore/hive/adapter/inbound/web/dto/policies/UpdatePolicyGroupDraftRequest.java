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

package me.golemcore.hive.adapter.inbound.web.dto.policies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UpdatePolicyGroupDraftRequest(Integer schemaVersion,Map<String,PolicyProviderConfigRequest>llmProviders,PolicyModelRouterRequest modelRouter,PolicyModelCatalogRequest modelCatalog,PolicyToolsConfigRequest tools,PolicyMemoryConfigRequest memory,PolicyMcpConfigRequest mcp,PolicyAutonomyConfigRequest autonomy){

public UpdatePolicyGroupDraftRequest{llmProviders=llmProviders!=null?llmProviders:new LinkedHashMap<>();}

public record PolicyProviderConfigRequest(String apiKey,String baseUrl,Integer requestTimeoutSeconds,String apiType,Boolean legacyApi){}

public record PolicyModelRouterRequest(Double temperature,PolicyTierBindingRequest routing,Map<String,PolicyTierBindingRequest>tiers,Boolean dynamicTierEnabled){

public PolicyModelRouterRequest{tiers=tiers!=null?tiers:new LinkedHashMap<>();}}

public record PolicyTierBindingRequest(String model,String reasoning){}

public record PolicyModelCatalogRequest(String defaultModel,Map<String,PolicyModelConfigRequest>models){

public PolicyModelCatalogRequest{models=models!=null?models:new LinkedHashMap<>();}}

public record PolicyModelConfigRequest(String provider,String displayName,Boolean supportsVision,Boolean supportsTemperature,Integer maxInputTokens){}

public record PolicyToolsConfigRequest(Boolean filesystemEnabled,Boolean shellEnabled,Boolean skillManagementEnabled,Boolean skillTransitionEnabled,Boolean tierEnabled,Boolean goalManagementEnabled,List<PolicyEnvironmentVariableRequest>shellEnvironmentVariables){

public PolicyToolsConfigRequest{shellEnvironmentVariables=shellEnvironmentVariables!=null?shellEnvironmentVariables:new ArrayList<>();}}

public record PolicyEnvironmentVariableRequest(String name,String value){}

public record PolicyMemoryConfigRequest(Integer version,Boolean enabled,Integer softPromptBudgetTokens,Integer maxPromptBudgetTokens,Integer workingTopK,Integer episodicTopK,Integer semanticTopK,Integer proceduralTopK,Boolean promotionEnabled,Double promotionMinConfidence,Boolean decayEnabled,Integer decayDays,Integer retrievalLookbackDays,Boolean codeAwareExtractionEnabled,PolicyMemoryDisclosureRequest disclosure,PolicyMemoryRerankingRequest reranking,PolicyMemoryDiagnosticsRequest diagnostics){}

public record PolicyMemoryDisclosureRequest(String mode,String promptStyle,Boolean toolExpansionEnabled,Boolean disclosureHintsEnabled,Double detailMinScore){}

public record PolicyMemoryRerankingRequest(Boolean enabled,String profile){}

public record PolicyMemoryDiagnosticsRequest(String verbosity){}

public record PolicyMcpConfigRequest(Boolean enabled,Integer defaultStartupTimeout,Integer defaultIdleTimeout,List<PolicyMcpCatalogEntryRequest>catalog){

public PolicyMcpConfigRequest{catalog=catalog!=null?catalog:new ArrayList<>();}}

public record PolicyMcpCatalogEntryRequest(String name,String description,String command,Map<String,String>env,Integer startupTimeoutSeconds,Integer idleTimeoutMinutes,Boolean enabled){}

public record PolicyAutonomyConfigRequest(Boolean enabled,Integer tickIntervalSeconds,Integer taskTimeLimitMinutes,Boolean autoStart,Integer maxGoals,String modelTier,Boolean reflectionEnabled,Integer reflectionFailureThreshold,String reflectionModelTier,Boolean reflectionTierPriority,Boolean notifyMilestones){}}
