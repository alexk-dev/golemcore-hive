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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String[] CONTEXT_CORE_PACKAGES = {
            "me.golemcore.hive.domain..",
            "me.golemcore.hive.auth.application..",
            "me.golemcore.hive.auth.domain..",
            "me.golemcore.hive.fleet.application..",
            "me.golemcore.hive.fleet.domain..",
            "me.golemcore.hive.workflow.application..",
            "me.golemcore.hive.workflow.domain..",
            "me.golemcore.hive.execution.application..",
            "me.golemcore.hive.execution.domain..",
            "me.golemcore.hive.governance.application..",
            "me.golemcore.hive.governance.domain..",
            "me.golemcore.hive.selfevolving.application..",
            "me.golemcore.hive.selfevolving.domain.." };

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("me.golemcore.hive");

    @Test
    void contextualCoreMustBeFrameworkFree() {
        noClasses()
                .that()
                .resideInAnyPackage(CONTEXT_CORE_PACKAGES)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "me.golemcore.hive..adapter..",
                        "me.golemcore.hive..config..",
                        "me.golemcore.hive.infrastructure..",
                        "me.golemcore.hive.port..",
                        "org.springframework..",
                        "reactor..",
                        "com.fasterxml.jackson..")
                .because("contextual core packages must stay framework-free and adapter-free")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void outboundPortsMustStayAsInterfaces() {
        classes()
                .that()
                .resideInAnyPackage(
                        "me.golemcore.hive.auth.application.port.out..",
                        "me.golemcore.hive.fleet.application.port.out..",
                        "me.golemcore.hive.workflow.application.port.out..",
                        "me.golemcore.hive.execution.application.port.out..",
                        "me.golemcore.hive.governance.application.port.out..",
                        "me.golemcore.hive.selfevolving.application.port.out..")
                .should()
                .beInterfaces()
                .because("application outbound ports define the hexagonal seam")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void applicationServicesAndSharedDomainMustNotDependOnInfrastructureSpis() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "me.golemcore.hive.domain..",
                        "me.golemcore.hive.auth.application..",
                        "me.golemcore.hive.fleet.application..",
                        "me.golemcore.hive.workflow.application..",
                        "me.golemcore.hive.execution.application..",
                        "me.golemcore.hive.governance.application..",
                        "me.golemcore.hive.selfevolving.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("me.golemcore.hive.infrastructure..")
                .because("core code must depend on contextual business ports instead of low-level infrastructure spis")
                .check(IMPORTED_CLASSES);
    }
}
