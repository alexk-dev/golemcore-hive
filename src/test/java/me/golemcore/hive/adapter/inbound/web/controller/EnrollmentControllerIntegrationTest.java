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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .configureClient()
                .build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("hive.storage.base-path", () -> tempDir.toString());
        registry.add("hive.security.cookie.secure", () -> false);
        registry.add("hive.bootstrap.admin.username", () -> "admin");
        registry.add("hive.bootstrap.admin.password", () -> "change-me-now");
        registry.add("hive.bootstrap.admin.display-name", () -> "Hive Admin");
        registry.add("hive.fleet.public-base-url", () -> "https://hive.example.test/");
    }

    @Test
    void shouldReuseEnrollmentTokenAndExposeJoinCode() throws Exception {
        String operatorToken = loginAsAdmin();

        EntityExchangeResult<String> createResult = webTestClient.post()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {"note":"shared-lab","expiresInMinutes":60}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();

        JsonNode createdPayload = objectMapper.readTree(createResult.getResponseBody());
        String tokenId = createdPayload.get("id").asText();
        String revealedToken = createdPayload.get("token").asText();
        Assertions.assertEquals(revealedToken + ":https://hive.example.test", createdPayload.get("joinCode").asText());

        String firstGolemId = registerGolem(revealedToken, "Builder One", "host-a");
        String secondGolemId = registerGolem(revealedToken, "Builder Two", "host-b");
        Assertions.assertNotEquals(firstGolemId, secondGolemId);

        webTestClient.get()
                .uri("/api/v1/enrollment-tokens")
                .header(HttpHeaders.AUTHORIZATION, operatorToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(tokenId)
                .jsonPath("$[0].registrationCount").isEqualTo(2)
                .jsonPath("$[0].lastUsedAt").exists()
                .jsonPath("$[0].lastRegisteredGolemId").isEqualTo(secondGolemId);
    }

    private String registerGolem(String enrollmentToken, String displayName, String hostLabel) throws Exception {
        EntityExchangeResult<String> registerResult = webTestClient.post()
                .uri("/api/v1/golems/register")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {
                          "enrollmentToken":"%s",
                          "displayName":"%s",
                          "hostLabel":"%s",
                          "runtimeVersion":"bot-1.2.3",
                          "buildVersion":"build-42",
                          "supportedChannels":["control","events"],
                          "capabilities":{
                            "providers":["openai"],
                            "modelFamilies":["gpt"],
                            "enabledTools":["shell","git"],
                            "enabledAutonomyFeatures":["planning"],
                            "capabilityTags":["review","java"],
                            "supportedChannels":["control","events"],
                            "snapshotHash":"abc123",
                            "defaultModel":"gpt-5"
                          }
                        }
                        """.formatted(enrollmentToken, displayName, hostLabel))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult();
        return objectMapper.readTree(registerResult.getResponseBody()).get("golemId").asText();
    }

    private String loginAsAdmin() throws Exception {
        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {"username":"admin","password":"change-me-now"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        JsonNode payload = objectMapper.readTree(loginResult.getResponseBody());
        String accessToken = payload.get("accessToken").asText();
        Assertions.assertNotNull(accessToken);
        return "Bearer " + accessToken;
    }
}
