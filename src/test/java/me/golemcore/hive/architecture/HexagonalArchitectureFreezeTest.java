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
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureFreezeTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("me.golemcore.hive");

    @Test
    void legacyCoreLeaksAreFrozen() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("me.golemcore.hive..domain..", "me.golemcore.hive..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "me.golemcore.hive..adapter..",
                        "me.golemcore.hive..config..",
                        "me.golemcore.hive.port.outbound..",
                        "org.springframework..",
                        "reactor..",
                        "com.fasterxml.jackson..")
                .because(
                        "core code should not directly depend on adapters, framework types, or the global storage port")
                .as("legacy core package leaks");
        FreezingArchRule.freeze(rule).check(IMPORTED_CLASSES);
    }

    @Test
    void inboundAdaptersMustNotReachLegacyOutboundPortsDirectly() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage("me.golemcore.hive..adapter.inbound..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("me.golemcore.hive.port.outbound..")
                .because("inbound adapters should go through use cases, not directly to outbound infrastructure ports")
                .as("inbound adapters reaching outbound infrastructure");
        FreezingArchRule.freeze(rule).check(IMPORTED_CLASSES);
    }
}
