package com.englishprep.api.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for Swagger UI. The "Authorize" button appears because of the security scheme
 * below: paste the {@code accessToken} from login and every call from the UI sends it as
 * {@code Authorization: Bearer ...}. All endpoints require it by default; the public ones
 * (register, login, refresh) opt out with {@code @SecurityRequirements} on their method.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "English Prep API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT", description = "The accessToken returned by login (paste it without the word Bearer)")
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
}
