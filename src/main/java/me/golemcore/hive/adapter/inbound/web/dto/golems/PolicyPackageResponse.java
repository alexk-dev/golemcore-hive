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

package me.golemcore.hive.adapter.inbound.web.dto.golems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PolicyPackageResponse(String policyGroupId,int targetVersion,String checksum,Map<String,PolicyProviderConfigResponse>llmProviders,PolicyModelRouterResponse modelRouter,PolicyModelCatalogResponse modelCatalog,PolicyToolsConfigResponse tools,PolicyMemoryConfigResponse memory,PolicyMcpConfigResponse mcp,PolicyAutonomyConfigResponse autonomy,PolicySdlcConfigResponse sdlc){

public PolicyPackageResponse{llmProviders=llmProviders!=null?llmProviders:new LinkedHashMap<>();}

public record PolicyProviderConfigResponse(String apiKey,String baseUrl,Integer requestTimeoutSeconds,String apiType,Boolean legacyApi){}

public record PolicyModelRouterResponse(Double temperature,PolicyTierBindingResponse routing,Map<String,PolicyTierBindingResponse>tiers,Boolean dynamicTierEnabled){

public PolicyModelRouterResponse{tiers=tiers!=null?tiers:new LinkedHashMap<>();}}

public record PolicyTierBindingResponse(String model,String reasoning){}

public record PolicyModelCatalogResponse(String defaultModel,Map<String,PolicyModelConfigResponse>models){

public PolicyModelCatalogResponse{models=models!=null?models:new LinkedHashMap<>();}}

public record PolicyModelConfigResponse(String provider,String displayName,Boolean supportsVision,Boolean supportsTemperature,Integer maxInputTokens){}

public record PolicyToolsConfigResponse(Boolean filesystemEnabled,Boolean shellEnabled,Boolean skillManagementEnabled,Boolean skillTransitionEnabled,Boolean tierEnabled,Boolean goalManagementEnabled,List<PolicyEnvironmentVariableResponse>shellEnvironmentVariables){

public PolicyToolsConfigResponse{shellEnvironmentVariables=shellEnvironmentVariables!=null?shellEnvironmentVariables:new ArrayList<>();}}

public record PolicyEnvironmentVariableResponse(String name,String value){}

public record PolicyMemoryConfigResponse(Integer version,Boolean enabled,Integer softPromptBudgetTokens,Integer maxPromptBudgetTokens,Integer workingTopK,Integer episodicTopK,Integer semanticTopK,Integer proceduralTopK,Boolean promotionEnabled,Double promotionMinConfidence,Boolean decayEnabled,Integer decayDays,Integer retrievalLookbackDays,Boolean codeAwareExtractionEnabled,PolicyMemoryDisclosureResponse disclosure,PolicyMemoryRerankingResponse reranking,PolicyMemoryDiagnosticsResponse diagnostics){}

public record PolicyMemoryDisclosureResponse(String mode,String promptStyle,Boolean toolExpansionEnabled,Boolean disclosureHintsEnabled,Double detailMinScore){}

public record PolicyMemoryRerankingResponse(Boolean enabled,String profile){}

public record PolicyMemoryDiagnosticsResponse(String verbosity){}

public record PolicyMcpConfigResponse(Boolean enabled,Integer defaultStartupTimeout,Integer defaultIdleTimeout,List<PolicyMcpCatalogEntryResponse>catalog){

public PolicyMcpConfigResponse{catalog=catalog!=null?catalog:new ArrayList<>();}}

public record PolicyMcpCatalogEntryResponse(String name,String description,String command,Map<String,String>env,Integer startupTimeoutSeconds,Integer idleTimeoutMinutes,Boolean enabled){

public PolicyMcpCatalogEntryResponse{env=env!=null?env:new LinkedHashMap<>();}}

public record PolicyAutonomyConfigResponse(Boolean enabled,Integer tickIntervalSeconds,Integer taskTimeLimitMinutes,Boolean autoStart,Integer maxGoals,String modelTier,Boolean reflectionEnabled,Integer reflectionFailureThreshold,String reflectionModelTier,Boolean reflectionTierPriority,Boolean notifyMilestones){}

public record PolicySdlcConfigResponse(Boolean taskCreationEnabled,Boolean autoApplyDecompositionEnabled,Integer maxDecompositionFanOut,Boolean assignmentEnabled,Boolean reviewerAssignmentEnabled,Boolean requireReviewerSeparationOfDuties,Integer approvalRequiredAboveFanOut,List<String>allowedCardKinds){

public PolicySdlcConfigResponse{allowedCardKinds=allowedCardKinds!=null?allowedCardKinds:new ArrayList<>();}}}
