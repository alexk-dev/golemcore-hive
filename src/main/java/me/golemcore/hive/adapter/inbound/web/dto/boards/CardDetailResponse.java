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

package me.golemcore.hive.adapter.inbound.web.dto.boards;

import java.time.Instant;
import java.util.List;

public record CardDetailResponse(String id,String boardId,String threadId,String title,String description,String prompt,String columnId,String assigneeGolemId,String assignmentPolicy,Integer position,boolean archived,Instant archivedAt,Instant createdAt,Instant updatedAt,Instant lastTransitionAt,CardControlStateResponse controlState,List<CardTransitionResponse>transitions){}
