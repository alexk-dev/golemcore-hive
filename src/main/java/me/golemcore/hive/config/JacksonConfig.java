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

package me.golemcore.hive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.DecompositionAssignmentSpec;
import me.golemcore.hive.domain.model.DecompositionPlan;
import me.golemcore.hive.domain.model.DecompositionPlanItem;
import me.golemcore.hive.domain.model.DecompositionPlanLink;
import me.golemcore.hive.domain.model.DecompositionReviewSpec;
import me.golemcore.hive.domain.model.EnrollmentToken;
import me.golemcore.hive.domain.model.ThreadRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.addMixIn(Card.class, CardJacksonMixin.class);
        objectMapper.addMixIn(DecompositionAssignmentSpec.class, IgnoreUnknownJacksonMixin.class);
        objectMapper.addMixIn(DecompositionPlan.class, IgnoreUnknownJacksonMixin.class);
        objectMapper.addMixIn(DecompositionPlanItem.class, IgnoreUnknownJacksonMixin.class);
        objectMapper.addMixIn(DecompositionPlanLink.class, IgnoreUnknownJacksonMixin.class);
        objectMapper.addMixIn(DecompositionReviewSpec.class, IgnoreUnknownJacksonMixin.class);
        objectMapper.addMixIn(EnrollmentToken.class, EnrollmentTokenJacksonMixin.class);
        objectMapper.addMixIn(ThreadRecord.class, ThreadRecordJacksonMixin.class);
        return objectMapper;
    }
}
