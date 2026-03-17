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
class AuthControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    private ApplicationContext applicationContext;

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
    }

    @Test
    void shouldLoginRefreshLoadCurrentOperatorAndLogout() {
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

        String refreshCookieHeader = loginResult.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String accessToken = loginResult.getResponseBody();

        Assertions.assertNotNull(refreshCookieHeader);
        Assertions.assertNotNull(accessToken);

        webTestClient.get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, extractBearerToken(loginResult))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin");

        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .cookie("hive_refresh_token", cookieValue(refreshCookieHeader))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.operator.username").isEqualTo("admin");

        webTestClient.post()
                .uri("/api/v1/auth/logout")
                .cookie("hive_refresh_token", cookieValue(refreshCookieHeader))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.SET_COOKIE, ".*Max-Age=0.*");
    }

    @Test
    void shouldRejectInvalidPassword() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("""
                        {"username":"admin","password":"wrong-password"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String extractBearerToken(EntityExchangeResult<String> loginResult) {
        String body = loginResult.getResponseBody();
        int accessTokenIndex = body.indexOf("\"accessToken\":\"");
        int start = accessTokenIndex + "\"accessToken\":\"".length();
        int end = body.indexOf('"', start);
        String token = body.substring(start, end);
        return "Bearer " + token;
    }

    private String cookieValue(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        return pair.split("=", 2)[1];
    }
}
