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

package me.golemcore.hive.governance.application;

import java.util.List;

public record BudgetProjectionData(List<CustomerProjection>customers,List<ServiceProjection>services,List<TeamProjection>teams,List<ObjectiveProjection>objectives,List<CardProjection>cards,List<CommandProjection>commands,List<RunProjection>runs){

public record CustomerProjection(String id,String name){}

public record ServiceProjection(String id,String name){}

public record TeamProjection(String id,String name){}

public record ObjectiveProjection(String id,String name,String ownerTeamId){}

public record CardProjection(String id,String serviceId,String boardId,String teamId,String objectiveId,String title){}

public record CommandProjection(String id,String cardId,String golemId,BudgetCommandProjectionStatus status,long estimatedCostMicros){}

public record RunProjection(String id,String cardId,String golemId,long inputTokens,long outputTokens,long accumulatedCostMicros){}}
