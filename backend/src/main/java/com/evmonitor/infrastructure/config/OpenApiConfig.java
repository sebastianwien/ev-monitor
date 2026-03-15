package com.evmonitor.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EV Monitor Public API")
                        .description("""
                                Externe Upload-API für Ladegänge und manuelle Imports.

                                **API Key erstellen:** Dashboard → Einstellungen → API / Integrationen

                                **Authentifizierung:** `Authorization: Bearer evm_<your-key>`
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("ApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("evm_<key>")
                                .description("EV Monitor API Key (evm_...)")));
    }

    /**
     * Nur öffentliche/externe Endpunkte in der Swagger-Doku anzeigen.
     * Interne Endpunkte (/api/logs, /api/cars, etc.) sind nicht Teil der Public API.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
