# Zerosum

> _Short description of the project goes here._

## Tech Stack

- Java 21
- Spring Boot 4.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator)
- PostgreSQL
- springdoc-openapi (Swagger UI)
- Lombok
- Maven (wrapper included)

## Getting Started

### Prerequisites

- JDK 21
- PostgreSQL

### Configuration

_TODO: document required properties / environment variables (datasource, OAuth2 issuer, etc.)._

### Build & Run

```bash
./mvnw spring-boot:run
```

### Tests

```bash
./mvnw test
```

## API Docs

Once running, Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Architecture

Zerosum is a modular monolith: a single Spring Boot application split into
feature modules, one top-level package per module under `com.zerosum.zerosum`.

| Module    | Responsibility                                              |
|-----------|-------------------------------------------------------------|
| `common`  | Shared building blocks: base types, errors, utilities       |
| `auth`    | Authentication and authorization                            |
| `account` | Account management                                          |
| `ledger`  | Ledger entries and transactions                             |

### Module dependency rules

```
ledger ──▶ account ──▶ common
  │                      ▲
  └──────────────────────┤
auth ────────────────────┘
```

1. **`common` depends on no other module.** It holds only generic code shared
   by all modules; no business logic lives here.
2. **`auth` depends only on `common`.**
3. **`account` depends only on `common`.**
4. **`ledger` depends only on `account` and `common`.**
5. **Dependencies point one way only; there are no cycles.** For example,
   `account` must never import from `ledger`. If a lower module needs to react
   to something in a higher one, use Spring application events instead of a
   direct call. Events that cross modules live in `common.event` (e.g.
   `UserRegisteredEvent`), so listeners don't depend on the publishing module.
6. **Modules talk to each other only through public APIs**, such as service
   interfaces and DTOs. Repositories, JPA entities and other internals stay
   inside their own module.
7. **Authentication is applied through Spring Security config and the security
   context.** Business modules do not import `auth` classes directly.

### Project structure

```
src/
├── main/java/com/zerosum/zerosum/
│   ├── common/                      # shared building blocks
│   ├── auth/                        # authentication & authorization
│   ├── account/                     # account management
│   ├── ledger/                      # ledger entries & transactions
│   └── ZerosumApplication.java
├── main/resources/                  # configuration
└── test/java/com/zerosum/zerosum/   # tests
```

## Authentication

1. `POST /auth/register` with `{"email", "password"}` creates a user (role `USER`).
2. `POST /auth/login` with the same credentials returns a short-lived JWT:
   `{"accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600}`.
3. Send it on every other request as `Authorization: Bearer <accessToken>`.
   In Swagger UI, click **Authorize** and paste the token (without `Bearer `).

Tokens are signed with RS256 (`jwt.private-key` / `jwt.public-key`) and valid for
`jwt.access-token-ttl` (1 hour). A wrong password and an unknown email both return the same
`401 INVALID_CREDENTIALS`, so the API never reveals which emails are registered.

### Trade-off: no logout or revocation

Access tokens are stateless: the server checks only the signature, issuer and expiry, and keeps
no record of issued tokens. That keeps every request free of database lookups, but it means:

- **There is no logout.** A client "logs out" by discarding its token; anyone who already copied
  the token can keep using it until it expires.
- **Changes don't apply until the token expires.** A demoted, disabled or deleted user keeps their
  old role and access for up to 1 hour, and `/auth/me` reports what the token says.

The 1-hour lifetime is the compromise between how often clients must log in again and how long a
leaked or outdated token stays useful. Planned fix: short access tokens plus refresh tokens stored
server-side (the reserved `INVALID_REFRESH_TOKEN` code), which can be revoked on logout.

## Error codes

Every error response is an RFC 9457 `ProblemDetail` with an extra `errorCode` field.
Clients should branch on `errorCode`, not on `title` or `detail`, which are for humans and may change.

```json
{
  "type": "about:blank",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Account 42 has 5.00 INR available but 10.00 INR was requested",
  "errorCode": "INSUFFICIENT_FUNDS"
}
```

### Domain errors

Each exception extends `common.ApiException` and lives in its owning module's `exception` package.

| Code                        | Status | Exception                          | Module    | Meaning                                                    |
|-----------------------------|--------|------------------------------------|-----------|------------------------------------------------------------|
| `EMAIL_ALREADY_REGISTERED`  | 409    | `EmailAlreadyRegisteredException`  | `auth`    | Sign-up with an email that already has an account          |
| `INVALID_CREDENTIALS`       | 401    | `InvalidCredentialsException`      | `auth`    | Wrong email or password                                    |
| `INVALID_REFRESH_TOKEN`     | 401    | `InvalidRefreshTokenException`     | `auth`    | Refresh token is invalid, expired or revoked               |
| `ACCOUNT_NOT_FOUND`         | 404    | `AccountNotFoundException`         | `account` | No account with the given id                               |
| `INSUFFICIENT_FUNDS`        | 422    | `InsufficientFundsException`       | `ledger`  | Source account balance is lower than the requested amount  |
| `SELF_TRANSFER_NOT_ALLOWED` | 422    | `SelfTransferNotAllowedException`  | `ledger`  | Source and destination account are the same                |
| `IDEMPOTENCY_KEY_REUSED`    | 409    | `IdempotencyKeyReusedException`    | `ledger`  | Idempotency key already used with a different request body |
| `LEDGER_INVARIANT_VIOLATED` | 500    | `LedgerInvariantViolationException` (and its subclass `UnbalancedTransactionException`) | `ledger`  | A posting broke a ledger rule, e.g. entries don't sum to zero (a bug on our side) |

### Generic errors

| Code                | Status | When                                                                      |
|---------------------|--------|---------------------------------------------------------------------------|
| `VALIDATION_FAILED` | 400    | Bean Validation failed; `errors` lists each `field` and its `message`    |
| `UNAUTHORIZED`      | 401    | Missing, malformed, expired or wrongly signed bearer token                |
| `ACCESS_DENIED`     | 403    | Valid token, but its role doesn't allow the request                       |
| `INTERNAL_ERROR`    | 500    | Any unexpected exception; the stack trace is logged, never returned       |

Spring MVC's own errors (unknown route, unsupported method, malformed JSON, …) keep Spring's
standard 4xx `ProblemDetail` without an `errorCode`. The 401 and 403 responses come from
Spring Security (via `auth.SecurityProblemHandler`), not from the exception handler, and also
carry the standard `WWW-Authenticate: Bearer …` header.

## Contributing

_TODO_

## License

_TODO_
