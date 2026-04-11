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

package me.golemcore.hive.adapter.inbound.web.dto.budgets;

import java.time.Instant;

public record BudgetSnapshotResponse(String id,String scopeType,String scopeId,String scopeLabel,String customerId,String teamId,String objectiveId,String serviceId,long commandCount,long runCount,long inputTokens,long outputTokens,long actualCostMicros,long estimatedPendingCostMicros,Instant updatedAt){}
