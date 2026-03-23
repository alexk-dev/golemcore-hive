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

package me.golemcore.hive.adapter.inbound.web.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.adapter.inbound.web.dto.threads.DirectThreadResponse;
import me.golemcore.hive.domain.model.Golem;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.domain.service.GolemRegistryService;
import me.golemcore.hive.domain.service.ThreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/dm/threads")
@RequiredArgsConstructor
public class DmThreadsController {

    private final ThreadService threadService;
    private final GolemRegistryService golemRegistryService;

    @GetMapping
    public Mono<ResponseEntity<List<DirectThreadResponse>>> listDmThreads(Principal principal,
            @RequestParam(defaultValue = "10") int limit) {
        return Mono.fromCallable(() -> {
            ControllerActorSupport.requireOperatorActor(principal);
            List<ThreadRecord> threads = threadService.listDirectThreads();
            List<DirectThreadResponse> response = new ArrayList<>();
            for (ThreadRecord thread : threads) {
                if (response.size() >= Math.min(limit, 50)) {
                    break;
                }
                String golemId = thread.getAssignedGolemId();
                if (golemId == null) {
                    continue;
                }
                Golem golem = golemRegistryService.findGolem(golemId).orElse(null);
                if (golem == null) {
                    continue;
                }
                response.add(new DirectThreadResponse(thread.getId(), golem.getId(), golem.getDisplayName(),
                        golem.getState().name(), thread.getTitle(), thread.getCreatedAt(), thread.getUpdatedAt(),
                        thread.getLastMessageAt(), thread.getLastCommandAt()));
            }
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
