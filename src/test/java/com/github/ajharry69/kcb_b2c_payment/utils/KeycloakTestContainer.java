package com.github.ajharry69.kcb_b2c_payment.utils;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

public final class KeycloakTestContainer {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTestContainer.class);
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:24.0.4";
    private static final String REALM_EXPORT_PATH = "realm.json";
    private static final String REALM_NAME = "test-realm";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CLIENT_SECRET = "test-secret";
    private static KeycloakContainer keycloakContainer;

    private KeycloakTestContainer() {
    }

    public static synchronized KeycloakContainer getInstance() {
        if (keycloakContainer == null) {
            log.info("Starting Keycloak Testcontainer...");
            try {
                keycloakContainer = new KeycloakContainer(KEYCLOAK_IMAGE)
                        .withRealmImportFile(REALM_EXPORT_PATH)
                        .withReuse(true);

                keycloakContainer.start();

                // Optional: Add shutdown hook to stop the container gracefully
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (keycloakContainer != null && keycloakContainer.isRunning()) {
                        log.info("Stopping Keycloak Testcontainer...");
                        keycloakContainer.stop();
                        log.info("Keycloak Testcontainer stopped.");
                    }
                }));

            } catch (Exception e) {
                log.error("!!! Failed to start Keycloak Testcontainer !!!", e);
                throw new RuntimeException("Failed to initialize Keycloak Testcontainer", e);
            }
        }
        return keycloakContainer;
    }

    public static String getIssuerUri() {
        return String.format("%s/realms/%s", getInstance().getAuthServerUrl(), REALM_NAME);
    }

    public static String getAccessToken(String username, String password) {
        String tokenEndpoint = "http://localhost:8180/realms/test-realm/protocol/openid-connect/token";
        log.debug("Requesting access token for user '{}', client '{}' from endpoint: {}", username, TEST_CLIENT_ID, tokenEndpoint);

        try {
            // Use RestAssured to make the POST request for the token
            RequestSpecification requestSpecification = given()
                    .contentType(ContentType.URLENC)
                    .formParam("grant_type", "password")
                    .formParam("client_id", TEST_CLIENT_ID)
                    .formParam("client_secret", TEST_CLIENT_SECRET)
                    .formParam("username", username)
                    .formParam("password", password)
                    .formParam("scope", "openid");
            requestSpecification.log();
            Response response = requestSpecification
                    .when()
                    .post(tokenEndpoint);
            response.prettyPrint();
            String accessToken = response
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .path("access_token");

            if (accessToken == null || accessToken.isBlank()) {
                log.error("Extracted access token is null or blank for user '{}', client '{}'", username, TEST_CLIENT_ID);
                throw new RuntimeException("Extracted access token was null or blank.");
            }

            log.debug("Successfully obtained access token for user '{}'", username);
            return accessToken;
        } catch (Exception e) {
            log.error("Failed to obtain access token for user '{}', client '{}' from {}: {}", username, TEST_CLIENT_ID, tokenEndpoint, e.getMessage(), e);
            throw new RuntimeException("Could not get Keycloak access token using password grant", e);
        }
    }
}
