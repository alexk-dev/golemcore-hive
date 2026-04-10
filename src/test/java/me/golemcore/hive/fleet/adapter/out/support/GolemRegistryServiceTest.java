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

package me.golemcore.hive.fleet.adapter.out.support;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import me.golemcore.hive.fleet.application.service.GolemFleetApplicationService;
import org.junit.jupiter.api.Test;

class GolemRegistryServiceTest {

    @Test
    void shouldExposeOnlyDelegateConstructor() {
        Constructor<?>[] constructors = GolemRegistryService.class.getConstructors();

        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[] { GolemFleetApplicationService.class }, constructors[0].getParameterTypes());
    }
}
