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

import me.golemcore.hive.domain.model.PolicyGroupSpec.PolicyModelCatalog;
import me.golemcore.hive.domain.model.PolicyGroupSpec.PolicyModelRouter;
import me.golemcore.hive.domain.model.PolicyGroupSpec.PolicyProviderConfig;
import java.util.Map;

public record PolicyPackageResponse(String policyGroupId,int targetVersion,String checksum,Map<String,PolicyProviderConfig>llmProviders,PolicyModelRouter modelRouter,PolicyModelCatalog modelCatalog){}
