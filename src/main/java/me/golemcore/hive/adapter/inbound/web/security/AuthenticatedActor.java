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

package me.golemcore.hive.adapter.inbound.web.security;

import java.security.Principal;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthenticatedActor implements Principal {

    private final SubjectType subjectType;
    private final String subjectId;
    private final String name;
    private final List<String> roles;
    private final List<String> scopes;
    private final String sessionId;

    public boolean isOperator() {
        return subjectType == SubjectType.OPERATOR;
    }

    public boolean isGolem() {
        return subjectType == SubjectType.GOLEM;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(List<String> requiredRoles) {
        return requiredRoles.stream().anyMatch(roles::contains);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
