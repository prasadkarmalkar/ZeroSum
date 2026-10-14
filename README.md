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
  │          │           ▲
  │          ▼           │
  │        auth ─────────┤   (account → auth only via UserLookup)
  └──────────────────────┘
```

1. **`common` depends on no other module.** It holds only generic code shared
   by all modules; no business logic lives here.
2. **`auth` depends only on `common`.**
3. **`account` depends on `common`, and on `auth` only through its public
   `UserLookup`** (to find a recipient's account by email). `auth` never imports
   `account`, so there is no cycle.
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

Every path under `/admin` (`POST /admin/deposits` and `GET /admin/trial-balance`) also requires the `ADMIN` role; a signed-in
user without it gets `403 ACCESS_DENIED`. The admin user is seeded on startup from `zerosum.admin.*`, and
no endpoint can create another one.

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

## Idempotent transfers

Networks fail. A client that sends `POST /transfers` or `POST /admin/deposits` and never sees the
response can't know whether the money moved, so it must be able to **retry safely**. Every
money-moving request therefore requires an `Idempotency-Key` header: a value the client picks once
per operation (a fresh UUID is ideal, at most 255 characters) and reuses on every retry of it.

```http
POST /transfers
Authorization: Bearer eyJ...
Idempotency-Key: 3f1c2e9a-8b7d-4c1e-9f0a-6d2b5e8c4a71

{"recipientEmail": "bob@example.com", "amount": "100.00", "description": "Rent"}
```

| Situation                                   | Result                                                                                  |
|---------------------------------------------|-----------------------------------------------------------------------------------------|
| New key                                     | The transfer runs: `201`, `Idempotent-Replayed: false`                                   |
| Same key, same request                      | Nothing runs again; the **original** `201` body is returned, `Idempotent-Replayed: true` |
| Same key, different request                 | `409 IDEMPOTENCY_KEY_REUSED`; nothing runs                                               |
| Missing, blank or too long key              | `400 VALIDATION_FAILED` (field `idempotencyKey`)                                         |
| Request failed (e.g. `422 INSUFFICIENT_FUNDS`) | Nothing is remembered, so the same key can be retried once the problem is fixed       |

**How it works**

- The logic lives in one place, `ledger.service.IdempotencyService`, used by both `TransferService` and
  `DepositService`.
- Keys live in the `idempotency_keys` table, owned by the ledger module, with a unique constraint on
  **(user, key)**: keys are scoped per user, so two users can't collide or see each other's results.
- Each row stores a **fingerprint** of the request: a SHA-256 of the operation (`TRANSFER` or `DEPOSIT`),
  the normalized recipient email, the amount in minor units and the description (blank means the default,
  `"Transfer"` or `"Deposit"`). Including the operation means a key used for a transfer can't be replayed
  as a deposit; that's a `409`. Requests that mean the same transfer
  match even if spelled differently (`BOB@Example.com` / `bob@example.com`, `"100"` / `"100.00"`).
  It also stores the transaction id and the original response body to replay.
- The key row is inserted **before** the transfer runs and completed in the **same** database transaction.
  So the key, the ledger entries and the balance changes commit together, or not at all.

**Two identical requests at the same moment**

Both find no key, and both try to insert it. Postgres makes the second insert wait on the unique index
until the first transaction finishes. When the first commits, the second insert fails with a unique
violation, which rolls back the second request's transaction before it has moved any money. The service
catches that one violation and, in a fresh transaction, reads the winner's row and replays its response
(or returns `409` if the two bodies differed). The money moves exactly once, and both callers get the
same `201` body. `TransferIdempotencyTest` races identical requests ten times to prove it.

**Not yet:** keys are kept forever. A cleanup job for keys older than, say, 24 hours can come later.

## Trial balance

`GET /admin/trial-balance` (admin only) is the zero-sum promise as a live check:

```json
{ "balanced": true, "entriesTotal": "0.00", "balancesTotal": "0.00",
  "mismatchedAccounts": 0, "transactionCount": 42, "checkedAt": "2026-10-14T15:30:00Z" }
```

- `entriesTotal`: every ledger entry ever written, summed. Must be `0.00`.
- `mismatchedAccounts`: accounts whose cached balance (ADR 0003) differs from the sum of their entries,
  including entries on an account that no longer exists. Must be `0`.
- `balanced` is true only when both hold; when it isn't, the details are logged at error level.

All figures come from **one database snapshot**: the check runs in a single read-only `REPEATABLE_READ`
transaction. Under PostgreSQL's default (`READ COMMITTED`) each query would see the latest commits, so a
transfer landing between "read the balances" and "read the entries" would report mismatches that never
existed; `TrialBalanceTest` reproduces exactly that when the isolation is lowered.

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
| `TRANSACTION_NOT_FOUND`     | 404    | `TransactionNotFoundException`     | `ledger`  | No such transaction, **or** the caller isn't part of it; deliberately indistinguishable |
| `LEDGER_INVARIANT_VIOLATED` | 500    | `LedgerInvariantViolationException` (and its subclass `UnbalancedTransactionException`) | `ledger`  | A posting broke a ledger rule, e.g. entries don't sum to zero (a bug on our side) |

### Generic errors

| Code                | Status | When                                                                      |
|---------------------|--------|---------------------------------------------------------------------------|
| `VALIDATION_FAILED` | 400    | Bean Validation failed, or a path/query value has the wrong type (e.g. a non-numeric id); `errors` lists each `field` and its `message` |
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
