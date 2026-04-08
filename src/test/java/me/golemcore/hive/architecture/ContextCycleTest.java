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

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ContextCycleTest {

    private static final JavaClasses CONTEXT_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                    "me.golemcore.hive.auth",
                    "me.golemcore.hive.fleet",
                    "me.golemcore.hive.workflow",
                    "me.golemcore.hive.execution",
                    "me.golemcore.hive.governance");

    @Test
    void contextPackagesMustStayAcyclic() {
        slices()
                .matching("me.golemcore.hive.(*)..")
                .should()
                .beFreeOfCycles()
                .because("backend contexts should remain independent instead of recreating a shared service knot")
                .check(CONTEXT_CLASSES);
    }
}
