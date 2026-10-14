# ADR 0002: Lock account rows pessimistically, lowest id first

- **Status:** Accepted

## Context

Two transfers from the same account can read the same balance at the same moment, both decide there's
enough money, and both succeed: the account is overdrawn, or one update is lost. Some form of concurrency
control around "check the balance, then change it" is mandatory.

## Decision

Every posting (`LedgerService.post`) locks the rows of all accounts involved with
`SELECT ... FOR NO KEY UPDATE` (JPA `PESSIMISTIC_WRITE`) before checking or changing any balance. The lock
is held until the transaction commits, so concurrent postings on the same account take turns.

- **Fixed order:** accounts are always locked **lowest id first** (`AccountService.lockAccounts`).
  A→B and B→A then queue on the same first row, instead of each holding one row and waiting forever
  for the other.
- **Fresh state:** after locking, each account is re-read (`EntityManager.refresh`). Without this,
  Hibernate returns any copy already loaded earlier in the transaction, which may be stale. The
  concurrency tests found exactly this bug.
- **No hanging:** Postgres `lock_timeout` is set per connection (5 s, 2 s in tests), so a stuck lock fails
  instead of hanging.
- **Can't be misused:** lock and balance-update methods use `Propagation.MANDATORY`, since a lock outside
  a transaction would be released immediately.

## Consequences

- Correct by construction, and easy to reason about: one row lock per account.
- Postings on the **same** account are serialized. That's fine at this scale, and exactly what the
  overdraft check needs. TREASURY, touched by every deposit, is the hottest row.
- **Optimistic locking** (a `@Version` column and retry on conflict) was rejected: under contention on one
  account, most attempts would fail and retry, and every caller would need retry logic.
- **`SERIALIZABLE` isolation** was rejected for the same reason: frequent serialization failures to retry,
  and less obvious behaviour.
- `ConcurrencyTest` proves it: with the locks removed, the overdraft race, transfer storm and
  opposite-directions tests all fail.
