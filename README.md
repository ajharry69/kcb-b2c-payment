## Start keycloak for Oauth

```bash
docker compose up -d
```

## Start the server

```bash
./gradlew booTestRun
```

### Test the APIs

Go to: http://localhost:8080/swagger-ui/index.html

#### Oauth2 credentials

1. Client ID: `test-client`.
2. Client Secret: `test-secret`.

##### User credentials

1. Username: `testuser`.
2. Password: `password`.

## Run the tests

```bash
./gradlew clean test
```