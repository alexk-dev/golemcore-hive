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

import java.util.LinkedHashMap;
import java.util.Map;

public record UpdatePolicyGroupDraftRequest(Integer schemaVersion,Map<String,PolicyProviderConfigRequest>llmProviders,PolicyModelRouterRequest modelRouter,PolicyModelCatalogRequest modelCatalog){

public UpdatePolicyGroupDraftRequest{llmProviders=llmProviders!=null?llmProviders:new LinkedHashMap<>();}

public record PolicyProviderConfigRequest(String apiKey,String baseUrl,Integer requestTimeoutSeconds,String apiType,Boolean legacyApi){}

public record PolicyModelRouterRequest(Double temperature,PolicyTierBindingRequest routing,Map<String,PolicyTierBindingRequest>tiers,Boolean dynamicTierEnabled){

public PolicyModelRouterRequest{tiers=tiers!=null?tiers:new LinkedHashMap<>();}}

public record PolicyTierBindingRequest(String model,String reasoning){}

public record PolicyModelCatalogRequest(String defaultModel,Map<String,PolicyModelConfigRequest>models){

public PolicyModelCatalogRequest{models=models!=null?models:new LinkedHashMap<>();}}

public record PolicyModelConfigRequest(String provider,String displayName,Boolean supportsVision,Boolean supportsTemperature,Integer maxInputTokens){}}
