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

package me.golemcore.hive.workflow.application;

import me.golemcore.hive.domain.model.ActorType;

public record WorkflowActor(ActorType type,String id,String name){

public static WorkflowActor operator(String id,String name){return new WorkflowActor(ActorType.OPERATOR,id,name);}

public static WorkflowActor golem(String id,String name){return new WorkflowActor(ActorType.GOLEM,id,name);}

public static WorkflowActor system(String id,String name){return new WorkflowActor(ActorType.SYSTEM,id,name);}

public String auditType(){return type!=null?type.name():ActorType.OPERATOR.name();}}
