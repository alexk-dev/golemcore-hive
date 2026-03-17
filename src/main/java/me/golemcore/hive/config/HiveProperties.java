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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hive")
@Validated
@Data
public class HiveProperties {

    private StorageProperties storage = new StorageProperties();
    private SecurityProperties security = new SecurityProperties();
    private BootstrapProperties bootstrap = new BootstrapProperties();

    @Data
    public static class StorageProperties {
        @NotBlank
        private String basePath = "./data/hive";
    }

    @Data
    public static class SecurityProperties {
        private List<String> corsAllowedOrigins = List.of("http://localhost:5173");
        private JwtProperties jwt = new JwtProperties();
        private CookieProperties cookie = new CookieProperties();
    }

    @Data
    public static class JwtProperties {
        @NotBlank
        private String issuer = "golemcore-hive";
        @NotBlank
        private String audience = "golemcore-hive-ui";
        private String secret = "";
        @Min(1)
        private int accessExpirationMinutes = 30;
        @Min(1)
        private int refreshExpirationDays = 7;
    }

    @Data
    public static class CookieProperties {
        @NotBlank
        private String refreshName = "hive_refresh_token";
        private boolean secure;
        @NotBlank
        private String sameSite = "Lax";
        @NotBlank
        private String path = "/api/v1/auth";
    }

    @Data
    public static class BootstrapProperties {
        private AdminProperties admin = new AdminProperties();
    }

    @Data
    public static class AdminProperties {
        private boolean enabled = true;
        @NotBlank
        private String username = "admin";
        @NotBlank
        private String password = "change-me-now";
        @NotBlank
        private String displayName = "Hive Admin";
    }
}
