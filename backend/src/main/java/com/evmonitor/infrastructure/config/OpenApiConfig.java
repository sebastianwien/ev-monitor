package com.evmonitor.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"))
                .components(new Components()
                        .addSecuritySchemes("ApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("evm_<key>")
                                .description("EV Monitor API Key (evm_...)")))
                .tags(List.of(
                        new Tag().name("Charging Sessions").description("Upload and manage charging sessions (Wallboxen, Skripte, Home-Automation)"),
                        new Tag().name("Trips").description("Upload and manage driving trips"),
                        new Tag().name("Reference Data").description("Static reference data (charging providers, etc.)")));
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
