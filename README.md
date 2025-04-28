# KCB B2C Payment Service

This is a Spring Boot microservice designed to handle Business-to-Consumer (B2C) mobile money payments in Kenya,
integrating with abstracted Mobile Network Operator (MNO) and SMS gateway services.

## Features

* **REST API:** Exposes endpoints for initiating and querying B2C payments.
* **Asynchronous Processing:** Handles potentially long-running MNO interactions asynchronously.
* **Idempotency:** Prevents duplicate payment processing based on a client-provided `transactionId`.
* **Abstraction:** Integrates with `MobileMoneyService` and `SmsService` interfaces, allowing for different
  implementations (e.g., M-Pesa, Airtel Money, specific SMS providers). Includes mock implementations for development
  and testing.
* **OAuth2 Security:** Secures API endpoints using JWT Bearer token authentication validated against a Keycloak
  instance (configurable via `application.properties`).
* **Database:** Uses H2 in-memory database for persistence (configurable).
* **Validation:** Validates incoming requests.
* **Error Handling:** Provides standardized error responses for common issues.
* **Testing:** Includes unit tests (Mockito) and integration tests (Testcontainers with Keycloak, RestAssured).
* **Containerization:** Includes a `Dockerfile` for building a container image.

## Requirements

* **Java 24 JDK:** Or a compatible JDK version.
* **Gradle 8.x:** Used for building the project.
* **Docker:** Required for running integration tests (Testcontainers) and building the Docker image.
* **(Optional) Keycloak Instance:** For local development/testing outside Testcontainers, a running Keycloak instance is
  needed. Configure `spring.security.oauth2.resourceserver.jwt.issuer-uri` in `application.properties`.

## Setup & Running

1. **Clone the repository:**
   ```bash
   git clone git@github.com:ajharry69/kcb-b2c-payment.git
   cd kcb-b2c-payment
   ```

2. **Start Keycloak Oauth Server:**
   Require Docker to be running to spin up a Keycloak instance.
    ```bash
    docker compose up -d
    ```

3. **Run the application:**
   ```bash
   ./gradlew booTestRun
   ```
   The service will start on `http://localhost:8080`.

4. **Running Tests:**
   ```bash
   ./gradlew clean test
   ```
   *(This runs both unit and integration tests)*

5. **Running with Docker:**
    - Build the Docker image:
   ```bash
   docker build -t kcb-b2c-payment:latest .
   ```
    - Run the container:
   ```bash
   docker run -p 8080:8080 -e OAUTH2_BASE_URL=http://<your-local-ip>:8180 kcb-b2c-payment:latest
   ```
   *(Replace `<your-local-ip>` with the actual IP address of your computer running the Keycloak)*

## API Endpoints

Base Path: `/api/v1/payments`

* **`POST /`**
    * **Description:** Initiates a new B2C payment.
    * **Request Body:** `PaymentRequest` JSON object.
    * **Security:** Requires `SCOPE_payment.initiate`.
    * **Response:**
        * `202 Accepted`: If successfully submitted for processing. Body contains `PaymentResponse` with `PROCESSING`
          status. `Location` header points to the resource.
        * `400 Bad Request`: Invalid request body or validation errors.
        * `401 Unauthorized`: Missing or invalid JWT token.
        * `403 Forbidden`: Token lacks the required scope.
        * `409 Conflict`: Duplicate `transactionId`.
        * `503 Service Unavailable`: Error communicating with MNO during initial submission.

* **`GET /{id}`**
    * **Description:** Retrieves payment status by its internal UUID.
    * **Security:** Requires `SCOPE_payment.read`.
    * **Response:**
        * `200 OK`: Body contains `PaymentResponse`.
        * `401 Unauthorized`: Missing or invalid JWT token.
        * `403 Forbidden`: Token lacks the required scope.
        * `404 Not Found`: Payment with the given ID does not exist.

* **`GET /?transactionId={transactionId}`**
    * **Description:** Retrieves payment status by the client-provided `transactionId`.
    * **Security:** Requires `SCOPE_payment.read`.
    * **Response:**
        * `200 OK`: Body contains `PaymentResponse`.
        * `401 Unauthorized`: Missing or invalid JWT token.
        * `403 Forbidden`: Token lacks the required scope.
        * `404 Not Found`: Payment with the given `transactionId` does not exist.

#### Test available APIs

Go to: http://localhost:8080/swagger-ui/index.html

#### Oauth2 credentials

1. Client ID: `test-client`.
2. Client Secret: `test-secret`.

##### User credentials

##### Regular user

1. Username: `testuser`.
2. Password: `password`.

##### Admin user

1. Username: `adminuser`.
2. Password: `adminpass`.

### DTOs

* **`PaymentRequest`**:
    ```json
    {
      "transactionId": "UNIQUE-CLIENT-TXN-ID-123",
      "recipientPhoneNumber": "+254712345678",
      "amount": 150.75,
      "currency": "KES"
    }
    ```
* **`PaymentResponse`**:
    ```json
    {
      "paymentId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "transactionId": "UNIQUE-CLIENT-TXN-ID-123",
      "recipientPhoneNumber": "+254712345678",
      "amount": 150.75,
      "currency": "KES",
      "status": "SUCCESSFUL", // PENDING, PROCESSING, SUCCESSFUL, FAILED, REJECTED
      "mnoReference": "MNO_REF_XYZ789", // Present on success
      "failureReason": null, // Present on failure/rejection
      "createdAt": "2025-04-28T10:15:30.123456",
      "updatedAt": "2025-04-28T10:16:05.987654"
    }
    ```

## Assumptions

* **Kenyan Phone Numbers:** Basic validation assumes Kenyan format (`+254...` or `07...`).
* **Currency:** Basic validation assumes "KES". This can be adjusted.
* **Asynchronous MNO:** The MNO interaction (`processB2CPayment`) is assumed to be asynchronous, returning a
  `CompletableFuture`. The service handles the callback/completion.
* **SMS Trigger:** SMS notifications are triggered *after* the final status (SUCCESSFUL/FAILED) is confirmed and saved
  to the database.
* **Security:** OAuth2 Resource Server configuration assumes JWT validation against the configured `issuer-uri`. The
  [realm.json](src/test/resources/realm.json) provides a basic Keycloak setup for testing with specific clients, users,
  roles, and scopes (
  `payment.initiate`, `payment.read`).

## Future Improvements

* Implement concrete `MobileMoneyService` and `SmsService` beans for actual providers.
* Implement status polling or callback endpoint for MNOs that require it.
* Add more robust validation rules.
* Consider using a persistent database (e.g., PostgreSQL) for production.
* Refine security scopes and roles based on actual requirements.
