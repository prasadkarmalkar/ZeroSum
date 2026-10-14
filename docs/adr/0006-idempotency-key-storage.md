# ADR 0006: Store idempotency keys in their own table, claimed before the work runs

- **Status:** Accepted

## Context

A client that times out on `POST /transfers` can't tell whether the money moved. It must be able to retry
safely, with an `Idempotency-Key` header, without ever moving money twice, including when two copies of
the request arrive at the same moment.

## Decision

Keys live in an `idempotency_keys` table, owned by the ledger module, with a **unique constraint on
(user_id, idempotency_key)**. Keys are scoped per user, so two users can't collide.

Each row stores:

- **A request fingerprint:** SHA-256 of the operation (`TRANSFER` or `DEPOSIT`), the normalized recipient
  email, the amount in minor units, and the description. Equivalent requests match (`"100"` and `"100.00"`),
  and one key can't be reused across operations.
- **The transaction id and the original response body,** for replay.

`IdempotencyService` handles every money-moving endpoint:

1. **Known key, same fingerprint:** replay the stored response (`Idempotent-Replayed: true`).
2. **Known key, different fingerprint:** `409 IDEMPOTENCY_KEY_REUSED`.
3. **New key:** insert ("claim") the row **first**, then do the work, then store the response, all in one
   transaction.

**The race:** a second request with the same new key blocks on the unique index until the first transaction
ends. When the first commits, the second insert fails, rolling back the second request's transaction before
it has moved anything. It then replays from the winner's row in a fresh transaction.

## Consequences

- Exactly-once money movement, proven by `TransferIdempotencyTest` and `ConcurrencyTest` (10 identical
  requests at once, one transaction).
- **Failures aren't remembered:** a 4xx rolls back the key too, so the client can fix the problem (e.g. top
  up) and retry with the same key.
- Storing keys on `ledger_transactions` was rejected. A key has to exist before its transaction does (to
  claim it), and future operations may not map one-to-one to a transaction.
- **Keys are never deleted.** The table grows forever until a cleanup job (e.g. after 24 hours) is added.
