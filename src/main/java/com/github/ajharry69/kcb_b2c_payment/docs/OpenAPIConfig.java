package com.github.ajharry69.kcb_b2c_payment.docs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;


@OpenAPIDefinition(
        info = @Info(
                contact = @Contact(
                        name = "Harrison",
                        email = "harrison@xently.co.ke"
                ),
                description = "API documentation for KCB B2C Payment",
                title = "OpenAPI specification - KCB B2C Payment",
                version = "v1"
        ),
        servers = {@Server(
                description = "Development",
                url = "http://localhost:8080"
        )}
)
@SecuritySchemes(
        value = @SecurityScheme(
                name = "OAuth2",
                description = "OAuth2 authentication with OpenID Connect",
                scheme = "Bearer",
                bearerFormat = "JWT",
                in = SecuritySchemeIn.HEADER,
                type = SecuritySchemeType.OPENIDCONNECT,
                openIdConnectUrl = "${spring.security.oauth2.resourceserver.jwt.issuer-uri}/.well-known/openid-configuration"
        )
)
public class OpenAPIConfig {
}
