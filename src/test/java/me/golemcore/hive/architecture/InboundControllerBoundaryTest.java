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

package me.golemcore.hive.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class InboundControllerBoundaryTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("me.golemcore.hive");

    @Test
    void selectedControllersMustNotDependOnInfrastructureSpis() {
        noClasses()
                .that()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.AuditController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.AuthController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.ApprovalsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.BudgetsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.SystemController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.GolemInspectionController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.GolemEventsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.BoardsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.CardsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.CommandsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.AssignmentsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.ThreadsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.DmThreadsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.DirectMessagesController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.GolemsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.GolemPolicyController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.PolicyGroupsController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.SelfEvolvingController")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.BoardMappingSupport")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.ws.GolemControlChannelHandler")
                .or()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.ws.OperatorUpdatesHandler")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("me.golemcore.hive.domain.service..", "me.golemcore.hive.infrastructure..")
                .because(
                        "selected inbound adapters should talk through application use cases instead of low-level spis")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void apiExceptionHandlerMustNotDependOnLegacyInspectionServiceType() {
        noClasses()
                .that()
                .haveFullyQualifiedName("me.golemcore.hive.adapter.inbound.web.controller.ApiExceptionHandler")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("me.golemcore.hive.domain.service..")
                .because("web exception mapping should depend on application errors, not legacy domain-service classes")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void inboundWebsocketClassesMustNotDependOnInfrastructureSpis() {
        noClasses()
                .that()
                .resideInAnyPackage("me.golemcore.hive.adapter.inbound.ws..")
                .and()
                .areNotInterfaces()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("me.golemcore.hive.infrastructure..")
                .because("inbound websocket adapters should talk through inbound-owned collaboration interfaces")
                .check(IMPORTED_CLASSES);
    }
}
