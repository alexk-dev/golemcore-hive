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

import java.util.LinkedHashMap;
import java.util.Map;

public record PolicyPackageResponse(String policyGroupId,int targetVersion,String checksum,Map<String,PolicyProviderConfigResponse>llmProviders,PolicyModelRouterResponse modelRouter,PolicyModelCatalogResponse modelCatalog){

public PolicyPackageResponse{llmProviders=llmProviders!=null?llmProviders:new LinkedHashMap<>();}

public record PolicyProviderConfigResponse(String apiKey,String baseUrl,Integer requestTimeoutSeconds,String apiType,Boolean legacyApi){}

public record PolicyModelRouterResponse(Double temperature,PolicyTierBindingResponse routing,Map<String,PolicyTierBindingResponse>tiers,Boolean dynamicTierEnabled){

public PolicyModelRouterResponse{tiers=tiers!=null?tiers:new LinkedHashMap<>();}}

public record PolicyTierBindingResponse(String model,String reasoning){}

public record PolicyModelCatalogResponse(String defaultModel,Map<String,PolicyModelConfigResponse>models){

public PolicyModelCatalogResponse{models=models!=null?models:new LinkedHashMap<>();}}

public record PolicyModelConfigResponse(String provider,String displayName,Boolean supportsVision,Boolean supportsTemperature,Integer maxInputTokens){}}
