package com.github.ajharry69.kcb_b2c_payment.docs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
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
public class OpenAPIConfig {
}
