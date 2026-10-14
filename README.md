# Zerosum

[![CI](https://github.com/prasadkarmalkar/ZeroSum/actions/workflows/ci.yml/badge.svg)](https://github.com/prasadkarmalkar/ZeroSum/actions/workflows/ci.yml)

> **A double-entry money ledger where money is never created or lost, even under concurrent load.**

Users send each other money through a REST API. Every movement is a ledger transaction whose entries sum
to exactly zero. The only way money enters is an admin deposit out of a TREASURY account, which goes
negative by exactly the amount handed out. Row locking, an overdraft check, idempotency keys and a live
trial balance keep the books whole, and a concurrency test suite proves it.

**Stack:** Java 21 · Spring Boot 4.1 (Web MVC, Data JPA, Security / OAuth2 resource server, Validation,
Actuator) · PostgreSQL · springdoc-openapi (Swagger UI) · Lombok · Maven wrapper

## Contents

- [Run it locally](#run-it-locally)
- [API overview](#api-overview)
- [Architecture](#architecture)
- [Design decisions (ADRs)](#design-decisions-adrs)
- [Authentication](#authentication)
- [Idempotent money movement](#idempotent-money-movement)
- [Trial balance](#trial-balance)
- [How correctness is tested](#how-correctness-is-tested)
- [Error codes](#error-codes)
- [Known trade-offs](#known-trade-offs)

## Run it locally

### 1. Prerequisites

- **JDK 21.** Use 21 exactly: Lombok fails on newer JDKs (for example JDK 27, with
  `ExceptionInInitializerError ... EndPosTable`). If several are installed, set `JAVA_HOME`, e.g. on macOS
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **PostgreSQL** 15 or newer, running on `localhost:5432`.
- **OpenSSL**, to generate the JWT key pair (preinstalled on macOS and most Linux distributions).

### 2. Create the databases

One for the app, one for the tests. The tests drop and recreate their schema on every run, so their
database's name must end in `_test`:

```bash
psql -h localhost -U postgres -c "CREATE DATABASE zerosum"
psql -h localhost -U postgres -c "CREATE DATABASE zerosum_test"
```

If `psql` isn't found, it's in PostgreSQL's `bin` directory: with the macOS installer, for example,
`export PATH="/Library/PostgreSQL/18/bin:$PATH"` (adjust the version), or use `createdb` the same way.

The tables are created automatically on first start (Hibernate `ddl-auto`; see
[Known trade-offs](#known-trade-offs)).

### 3. Generate the JWT key pair

Access tokens are signed with an RSA private key and verified with its public key. Generate a pair into
`keys/`, which is gitignored, so the private key is never committed or packaged:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl pkey -in keys/private.pem -pubout -out keys/public.pem
```

The tests don't need this: they use a committed, test-only pair in `src/test/resources/test-keys/`.

### 4. Set environment variables

Only the database password is required:

```bash
export DB_PASSWORD='your-postgres-password'
```

| Variable | Default | Used for |
|---|---|---|
| `DB_PASSWORD` | *(none for the app; `postgres` for tests)* | PostgreSQL password, for the app and the tests |
| `DB_USERNAME` | `postgres` | PostgreSQL user |
| `DB_URL` | `jdbc:postgresql://localhost:5432/zerosum` | App database |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/zerosum_test` | Test database |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | `file:keys/private.pem` / `file:keys/public.pem` | Key pair location (any Spring resource URL) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@zerosum.local` / `admin-dev-Passw0rd` | The admin user seeded on startup |
| `ZEROSUM_ADMIN_ENABLED` | `true` | Set to `false` to skip seeding an admin (e.g. in production) |
| `SERVER_PORT` | `8080` | HTTP port |

### 5. Run the tests

```bash
./mvnw test
```

About 200 tests run against the real `zerosum_test` database, including the concurrency suite; it takes under
a minute. CI runs the same suite on every push, against a PostgreSQL service container
(`.github/workflows/ci.yml`).

### 6. Start the app and try it

```bash
./mvnw spring-boot:run
```

Then open **Swagger UI** at <http://localhost:8080/swagger-ui.html>. Its front page walks you through the
whole API:

1. Log in as the seeded admin (`admin@zerosum.local` / `admin-dev-Passw0rd` unless you changed them), and
   **Authorize** with the token.
2. Register two users.
3. Fund one of them with a deposit.
4. Log in as that user, and transfer money to the other.

On startup the app also creates the TREASURY account. Health check: `GET /actuator/health`.

## API overview

Amounts are decimal strings (`"12.50"`). Errors are `application/problem+json` with a stable `errorCode`.

| Method | Path | Who | What |
|---|---|---|---|
| `POST` | `/auth/register` | anyone | Create a user (role `USER`); an account is opened for them automatically |
| `POST` | `/auth/login` | anyone | Email and password → 1-hour JWT access token |
| `GET` | `/auth/me` | signed in | Who the token belongs to: id, email, role |
| `GET` | `/accounts/me` | signed in | Your account and balance |
| `GET` | `/accounts/me/statement?page=&size=` | signed in | Your entries, newest first, each with a running balance; paged, `size` ≤ 100 |
| `GET` | `/accounts/me/transactions/{id}` | signed in | One of your transactions, with your signed amount; anyone else's gives 404 |
| `POST` | `/transfers` | signed in | Send money to another user by email; requires `Idempotency-Key` |
| `POST` | `/admin/deposits` | admin | Pay money into a user's account out of TREASURY; requires `Idempotency-Key` |
| `GET` | `/admin/trial-balance` | admin | Prove the ledger is whole: entries sum to zero, cached balances match |

Every endpoint has request and response examples and its error responses in Swagger UI, so you can try
the whole API without reading code.

## Architecture

Zerosum is a modular monolith: one Spring Boot application, one top-level package per module under
`com.zerosum.zerosum`.

| Module | Responsibility |
|---|---|
| `common` | Shared building blocks: `Money`, the error format, cross-module events, paging, `@CurrentUserId` |
| `auth` | Users, registration, login, JWT, security rules, admin seeding |
| `account` | Accounts, cached balances, row locking, TREASURY seeding |
| `ledger` | Ledger transactions and entries, transfers, deposits, idempotency, statements, trial balance |

### Module dependency rules

```
ledger ──▶ account ──▶ common
  │          │           ▲
  │          ▼           │
  │        auth ─────────┤   (account → auth only via UserLookup)
  └──────────────────────┘
```

1. **`common` depends on no other module,** and holds no business logic.
2. **`auth` depends only on `common`.**
3. **`account` depends on `common`, and on `auth` only through its public `UserLookup`** (to find a
   recipient's account by email). `auth` never imports `account`.
4. **`ledger` depends only on `account` and `common`.**
5. **Dependencies point one way; there are no cycles.** When a lower module must react to a higher one,
   it uses a Spring application event. Events that cross modules live in `common.event` (e.g.
   `UserRegisteredEvent`), so listeners don't depend on the publisher.
6. **Modules talk only through public APIs** (services, DTOs, read-only views like `AccountView`).
   Repositories and JPA entities stay inside their own module.
7. **The caller's identity comes from the token,** via `@CurrentUserId` in `common.security`, never from
   the request. Business modules don't depend on `auth`'s security classes.

### Project structure

```
src/main/java/com/zerosum/zerosum/
├── common/    Money, ApiException + GlobalExceptionHandler, OpenApiConfig, event/, security/, web/
├── auth/      SecurityConfig, SecurityProblemHandler, service/, web/, repository/, exception/
├── account/   domain/, service/, repository/, listener/, seed/, web/, exception/
└── ledger/    domain/, service/, repository/, web/, exception/
src/test/java/...  unit, repository, MockMvc and concurrency tests (concurrency/)
docs/adr/          architecture decision records
```

## Design decisions (ADRs)

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-money-representation.md) | Money is `long` minor units inside, decimal strings in the API |
| [0002](docs/adr/0002-pessimistic-row-locking.md) | Lock account rows pessimistically, lowest id first |
| [0003](docs/adr/0003-cached-account-balance.md) | Keep a cached balance on each account alongside the entries |
| [0004](docs/adr/0004-event-based-account-creation.md) | Open accounts from a synchronous "user registered" event |
| [0005](docs/adr/0005-jwt-only-authentication.md) | Stateless JWT access tokens only |
| [0006](docs/adr/0006-idempotency-key-storage.md) | Store idempotency keys in their own table, claimed before the work runs |

## Authentication

`POST /auth/login` returns a JWT (RS256, valid 1 hour). Send it as `Authorization: Bearer <token>`; in
Swagger UI, click **Authorize** and paste it. Every path under `/admin` also requires the `ADMIN` role,
otherwise `403 ACCESS_DENIED`. The only admin is the one seeded from configuration; no endpoint can create
another.

A wrong password and an unknown email return the same `401 INVALID_CREDENTIALS`, and take about the same
time (a dummy hash is checked), so login doesn't reveal which emails are registered.
See [ADR 0005](docs/adr/0005-jwt-only-authentication.md) and the trade-offs below.

## Idempotent money movement

A client that sends `POST /transfers` or `POST /admin/deposits` and never sees the response can't know
whether the money moved, so it must be able to **retry safely**. Both endpoints require an
`Idempotency-Key` header: the client picks one per operation (a fresh UUID is ideal, at most 255
characters) and reuses it on every retry.

| Situation | Result |
|---|---|
| New key | The operation runs: `201`, `Idempotent-Replayed: false` |
| Same key, same request | Nothing runs again; the **original** `201` body is returned, `Idempotent-Replayed: true` |
| Same key, different request (or a different operation) | `409 IDEMPOTENCY_KEY_REUSED`; nothing runs |
| Missing, blank or too long key | `400 VALIDATION_FAILED` (field `idempotencyKey`) |
| The request failed (e.g. `422 INSUFFICIENT_FUNDS`) | Nothing is remembered, so the same key can be retried once fixed |

Keys are scoped per user and stored with a fingerprint of the normalized request. Two identical requests
racing on one new key move the money exactly once, and both get the same response.
See [ADR 0006](docs/adr/0006-idempotency-key-storage.md).

## Trial balance

`GET /admin/trial-balance` is the zero-sum promise as a live check:

```json
{ "balanced": true, "entriesTotal": "0.00", "balancesTotal": "0.00",
  "mismatchedAccounts": 0, "transactionCount": 42, "checkedAt": "2026-10-14T15:30:00Z" }
```

- `entriesTotal`: every ledger entry ever written, summed. Must be `0.00`.
- `mismatchedAccounts`: accounts whose cached balance ([ADR 0003](docs/adr/0003-cached-account-balance.md))
  differs from the sum of their entries. Must be `0`.
- `balanced`: true only when both hold. When it isn't, the details are logged at error level.

All figures come from **one database snapshot** (a read-only `REPEATABLE_READ` transaction). Under
PostgreSQL's default `READ COMMITTED`, a transfer committing between two of the queries would report
mismatches that never existed. `TrialBalanceTest` reproduces exactly that when the isolation is lowered.

## How correctness is tested

The promise is that **money is never created or lost, even under concurrent load**. Single-request tests
can't show that, so `concurrency/ConcurrencyTest` fires requests in parallel against a real PostgreSQL
database:

- **No test-level transaction:** every transfer commits or rolls back on its own connection, as in production.
- **The production code path:** requests go through `TransferService`, the code the controller calls,
  with its row locks, overdraft check and idempotency.
- **Real funding:** test users are funded through `DepositService`, out of TREASURY.
- **A true simultaneous start:** `ConcurrentRunner` starts all tasks at the same instant, waits with a
  timeout (a hang fails the test), and collects every result and exception.

| Test | Setup | Must be true afterward | What it proves |
|------|-------|------------------------|----------------|
| **Transfer storm** | 10 users with 1000.00 each; 150 random transfers between them, all at once | Each transfer either succeeds or fails cleanly with `InsufficientFunds`; no balance below zero; users' total still 10000.00 | Under real contention, money only moves between accounts: none created, none lost |
| **Overdraft race** | One user with 100.00; 10 parallel transfers of 60.00 | Exactly 1 succeeds, 9 get `InsufficientFunds`; balance 40.00 | Two transfers can't both spend the same money: the row lock makes the balance check and update atomic |
| **Opposite directions** | 50 A→B of 5.00 and 50 B→A of 3.00, all at once | All 100 complete; A = 900.00, B = 1100.00 | Locking in a fixed order (lowest account id first) prevents deadlocks |
| **Duplicate requests** | The same transfer with the same `Idempotency-Key`, 10 times in parallel | One transaction; all 10 responses carry the same transaction id | A retried or double-sent request moves money once |

**Every test ends with the trial balance:** all entries sum to zero, and every cached balance matches its
entries. So even a test whose own assertions pass fails if the ledger was left inconsistent.

**The tests can fail, and did.** With the row locks from `AccountService.lockAccounts` removed (plain reads
instead of `SELECT ... FOR UPDATE`), three of the four break, and so does every trial balance:

- **Overdraft race:** all 10 transfers of 60.00 "succeeded" out of 100.00.
- **Transfer storm:** users ended with 8971.00 instead of 10000.00.
- **Opposite directions:** A ended at 967.00 instead of 900.00.

Duplicate requests still passes: it's protected by the idempotency key's unique index, not by row locks.

**This suite also found a real bug.** It failed the same way *with* locking enabled. `TransferService`
loaded the sender's account before locking it. Hibernate then returned that already-loaded, stale copy
instead of the row the `FOR UPDATE` had just read, so concurrent transfers all saw the old balance.
Earlier tests missed it because they called the ledger directly, with nothing loaded first.
`lockAccounts` now re-reads each account after locking it, which is guaranteed fresh while the lock is
held. `AccountLockingTest` pins the bug down deterministically.

Beyond the concurrency suite, there are unit tests (`Money`, the posting rules), repository tests against
Postgres (constraints, immutability, locking), and MockMvc tests for every endpoint. `OpenApiDocsTest`
fails the build if an endpoint or error response is left undocumented in Swagger.

## Error codes

Every error is an RFC 9457 problem (`application/problem+json`) with an extra `errorCode`. Branch on
`errorCode`, not on `title` or `detail`, which are for humans and may change.

```json
{
  "type": "about:blank",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Account 42 has 5.00 INR available but 10.00 INR was requested",
  "instance": "/transfers",
  "errorCode": "INSUFFICIENT_FUNDS"
}
```

### Domain errors

Each extends `common.ApiException` and lives in its owning module's `exception` package.

| Code | Status | Exception | Module | Meaning |
|---|---|---|---|---|
| `EMAIL_ALREADY_REGISTERED` | 409 | `EmailAlreadyRegisteredException` | `auth` | Sign-up with an email that already has an account |
| `INVALID_CREDENTIALS` | 401 | `InvalidCredentialsException` | `auth` | Wrong email or password (indistinguishable) |
| `ACCOUNT_NOT_FOUND` | 404 | `AccountNotFoundException` | `account` | No account for that user or recipient email |
| `INSUFFICIENT_FUNDS` | 422 | `InsufficientFundsException` | `ledger` | The sender's balance is lower than the amount |
| `SELF_TRANSFER_NOT_ALLOWED` | 422 | `SelfTransferNotAllowedException` | `ledger` | Sender and recipient are the same account |
| `IDEMPOTENCY_KEY_REUSED` | 409 | `IdempotencyKeyReusedException` | `ledger` | Idempotency key already used for a different request |
| `TRANSACTION_NOT_FOUND` | 404 | `TransactionNotFoundException` | `ledger` | No such transaction, **or** the caller isn't part of it (deliberately indistinguishable) |
| `LEDGER_INVARIANT_VIOLATED` | 500 | `LedgerInvariantViolationException` (incl. `UnbalancedTransactionException`) | `ledger` | A posting broke a ledger rule: a bug on our side; details are logged, not returned |

### Generic errors

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Invalid body, path or query value; `errors` lists each `field` and its `message` |
| `UNAUTHORIZED` | 401 | Missing, malformed, expired or wrongly signed bearer token (also sets `WWW-Authenticate: Bearer …`) |
| `ACCESS_DENIED` | 403 | Valid token, but its role doesn't allow the request |
| `INTERNAL_ERROR` | 500 | Any unexpected exception; the stack trace is logged, never returned |

Spring MVC's own errors (unknown route, unsupported method, malformed JSON) keep Spring's standard 4xx
problem response, without an `errorCode`.

## Known trade-offs

- **No logout or revocation.** Access tokens are stateless and valid for 1 hour. Logging out just means
  discarding the token: a copied token keeps working, and a role change only applies to the next token.
  There are no refresh tokens either, so clients log in again every hour.
  ([ADR 0005](docs/adr/0005-jwt-only-authentication.md))
- **Some endpoints reveal whether an email is registered.** Login doesn't, but `POST /transfers` and
  `POST /admin/deposits` return `404 ACCOUNT_NOT_FOUND` for an unknown recipient email, and
  `POST /auth/register` returns `409` for a taken one. A signed-in user could therefore probe for
  registered emails.
- **Offset pagination.** The statement uses `page` and `size`. Deep pages get slower, because the running
  balance is computed over the account's whole history on every request. A cursor (keyset) would scale
  better.
- **No rate limiting.** Nothing limits login attempts (password guessing), registrations or transfers per
  client.
- **No migrations, so no extra database guarantees.** The schema comes from Hibernate `ddl-auto`, not
  versioned Flyway migrations. So:
  - `accounts.user_id` and `ledger_entries.account_id` have no foreign keys (they're plain columns
    across modules).
  - Raw SQL can still `UPDATE` or `DELETE` ledger rows: immutability is enforced in code and in
    Hibernate, but not by the database.
  - There's no reviewed history of schema changes.
- **One currency.** Every account uses `zerosum.currency` (INR), and request amounts allow at most two
  decimal places. ([ADR 0001](docs/adr/0001-money-representation.md))
- **Idempotency keys are never deleted.** The table grows until a cleanup job is added.
  ([ADR 0006](docs/adr/0006-idempotency-key-storage.md))
- **Hot TREASURY row.** Every deposit locks the TREASURY account, so deposits are serialized.
  ([ADR 0002](docs/adr/0002-pessimistic-row-locking.md))
