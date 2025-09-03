package com.example.autosuggest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("Autosuggest API")
                        .description("Type-ahead suggestions with pluggable search engines (SQLite LIKE, FTS5, Oracle Text).")
                        .version("v1")
                        .contact(new Contact().name("Maintainers").email("devnull@example.com"))
                        .license(new License().name("Proprietary")))
                .externalDocs(new ExternalDocumentation()
                        .description("Contributor Guide")
                        .url("./AGENTS.md"));
    }
}

