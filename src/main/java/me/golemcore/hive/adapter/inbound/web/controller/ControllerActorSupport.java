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
import java.util.List;
import me.golemcore.hive.adapter.inbound.web.security.AuthenticatedActor;
import me.golemcore.hive.domain.model.GolemScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

final class ControllerActorSupport {

    private ControllerActorSupport() {
    }

    static AuthenticatedActor requireOperatorActor(Principal principal) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isOperator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator token required");
        }
        return actor;
    }

    static AuthenticatedActor requirePrivilegedOperator(Principal principal) {
        AuthenticatedActor actor = requireOperatorActor(principal);
        if (!actor.hasAnyRole(List.of("ADMIN", "OPERATOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or operator role required");
        }
        return actor;
    }

    static AuthenticatedActor requireGolemScope(Principal principal, String golemId, String scope) {
        AuthenticatedActor actor = extractActor(principal);
        if (actor == null || !actor.isGolem()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Golem token required");
        }
        if (!golemId.equals(actor.getSubjectId()) || !actor.hasScope(scope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Scope denied");
        }
        return actor;
    }

    private static AuthenticatedActor extractActor(Principal principal) {
        if (principal instanceof AuthenticatedActor actor) {
            return actor;
        }
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof AuthenticatedActor actor) {
            return actor;
        }
        return null;
    }
}
