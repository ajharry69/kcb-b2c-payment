package com.github.ajharry69.kcb_b2c_payment;

import com.github.ajharry69.kcb_b2c_payment.payment.PaymentRepository;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentRequest;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentResponse;
import com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;


@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentControllerIntegrationTest extends IntegrationTest {
    @LocalServerPort
    private int port;
    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        paymentRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        RestAssured.reset();
    }

    @Test
    @Order(1)
    @DisplayName("POST /payments - Success (202 Accepted) with valid token")
    void initiatePayment_Success() {
        PaymentRequest request = new PaymentRequest(
                "ITEST-TXN-001",
                "+254722000111",
                new BigDecimal("250.50"),
                "KES"
        );

        RequestSpecification requestSpecification = given()
                .auth().oauth2(getAdminAccessToken())
                .contentType(ContentType.JSON)
                .body(request);
        requestSpecification.log();
        Response response = requestSpecification
                .when()
                .post("/api/v1/payments");
        response.prettyPrint();
        PaymentResponse paymentResponse = response
                .then()
                .log().ifValidationFails()
                .statusCode(202) // Accepted
                .contentType(ContentType.JSON)
                .header("Location", matchesRegex(".*/api/v1/payments/[a-f0-9-]+$"))
                .body("status", is(PaymentStatus.PROCESSING.toString()))
                .body("transactionId", is(request.transactionId()))
                .body("paymentId", notNullValue())
                .extract().as(PaymentResponse.class);

        assertThat(paymentResponse).isNotNull();
        assertThat(paymentResponse.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @Order(2)
    @DisplayName("POST /payments - Forbidden (403) with token lacking scope")
    void initiatePayment_Forbidden_LackingScope() {
        // 'testuser' has 'payment.read' but not 'payment.initiate' scope by default in realm config
        PaymentRequest request = new PaymentRequest(
                "ITEST-TXN-002",
                "+254722000222",
                new BigDecimal("100.00"),
                "KES"
        );

        given()
                .auth().oauth2(getAccessToken()) // Use user token (lacks initiate scope)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/payments")
                .then()
                .log().ifValidationFails()
                .statusCode(403);
    }

    @Test
    @Order(3)
    @DisplayName("POST /payments - Unauthorized (401) with invalid/no token")
    void initiatePayment_Unauthorized_InvalidToken() {
        PaymentRequest request = new PaymentRequest(
                "ITEST-TXN-003",
                "+254722000333",
                new BigDecimal("50.00"),
                "KES"
        );

        // No token
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/payments")
                .then()
                .log().ifValidationFails()
                .statusCode(401); // Unauthorized

        // Invalid token
        given()
                .auth().oauth2("invalid-token-string")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/payments")
                .then()
                .log().ifValidationFails()
                .statusCode(401); // Unauthorized
    }


    @Test
    @Order(4)
    @DisplayName("GET /payments/{id} - Success (200 OK) with valid token")
    void getPaymentById_Success() {
        PaymentRequest createRequest = new PaymentRequest("ITEST-TXN-GET-01", "+254733111000", new BigDecimal("99.99"), "KES");
        RequestSpecification requestSpecification = given()
                .auth().oauth2(getAdminAccessToken())
                .contentType(ContentType.JSON)
                .body(createRequest);
        requestSpecification.log();
        Response response = requestSpecification
                .when()
                .post("/api/v1/payments");
        response.prettyPrint();
        PaymentResponse paymentResponse = response
                .then()
                .statusCode(202)
                .extract()
                .as(PaymentResponse.class);
        UUID createdId = paymentResponse.paymentId();

        given()
                .auth().oauth2(getAccessToken()) // Use user token (has read scope)
                .accept(ContentType.JSON)
                .when()
                .get("/api/v1/payments/{id}", createdId)
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("paymentId", is(createdId.toString()))
                .body("transactionId", is(createRequest.transactionId()))
                .body("status", is(
                        oneOf(
                                PaymentStatus.PROCESSING.toString(),
                                PaymentStatus.SUCCESSFUL.toString(),
                                PaymentStatus.FAILED.toString()
                        )
                ));
    }

    @Test
    @Order(5)
    @DisplayName("GET /payments/{id} - Not Found (404)")
    void getPaymentById_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        RequestSpecification requestSpecification = given()
                .auth().oauth2(getAdminAccessToken()) // Has read scope
                .accept(ContentType.JSON);
        requestSpecification.log();
        Response response = requestSpecification
                .when()
                .get("/api/v1/payments/{id}", nonExistentId);
        response.prettyPrint();
        response
                .then()
                .log().ifValidationFails()
                .statusCode(404);
    }

    @Test
    @Order(6)
    @DisplayName("GET /payments?transactionId={txnId} - Success (200 OK)")
    void getPaymentByTransactionId_Success() {
        PaymentRequest createRequest = new PaymentRequest("ITEST-TXN-GET-02", "+254733222111", new BigDecimal("150.00"), "KES");
        given()
                .auth().oauth2(getAdminAccessToken())
                .contentType(ContentType.JSON)
                .body(createRequest)
                .when()
                .post("/api/v1/payments")
                .then().statusCode(202);

        given()
                .auth().oauth2(getAccessToken()) // Has read scope
                .accept(ContentType.JSON)
                .param("transactionId", createRequest.transactionId())
                .when()
                .get("/api/v1/payments")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("transactionId", is(createRequest.transactionId()))
                .body("status", is(
                        oneOf(
                                PaymentStatus.PROCESSING.toString(),
                                PaymentStatus.SUCCESSFUL.toString(),
                                PaymentStatus.FAILED.toString()
                        )
                ));
    }

    @Test
    @Order(7)
    @DisplayName("GET /payments?transactionId={txnId} - Not Found (404)")
    void getPaymentByTransactionId_NotFound() {
        String nonExistentTxnId = "TXN_DOES_NOT_EXIST";
        RequestSpecification requestSpecification = given()
                .auth().oauth2(getAdminAccessToken()) // Has read scope
                .accept(ContentType.JSON)
                .param("transactionId", nonExistentTxnId);
        requestSpecification.log();
        Response response = requestSpecification
                .when()
                .get("/api/v1/payments");
        response.prettyPrint();
        response
                .then()
                .log().ifValidationFails()
                .statusCode(404);
    }
}