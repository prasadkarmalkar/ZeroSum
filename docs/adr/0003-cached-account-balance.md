# ADR 0003: Keep a cached balance on each account alongside the ledger entries

- **Status:** Accepted

## Context

The ledger entries are the source of truth: an account's balance is the sum of its entries. We need that
balance on every read (`GET /accounts/me`) and on every write (the overdraft check). Recomputing it each
time has two problems:

- **It gets slower forever:** entries are never deleted, so the sum covers more rows every day.
- **It's hard to lock:** you can't `SELECT ... FOR UPDATE` an aggregate, and locking the existing entries
  doesn't stop a concurrent insert of a new one.

## Decision

Each account row stores a cached `balance_minor` next to the entries.

- It changes **only** through `LedgerService.post`, via `AccountService.applyBalanceChanges`, in the
  **same database transaction** that inserts the entries. There's no setter, and that method requires an
  existing transaction, so the cache and the entries commit or roll back together.
- The overdraft check reads the cached balance **under the account row lock** (ADR 0002). The account row
  is the one natural thing to lock.
- A USER balance can't go negative, enforced three times: the check in `post` (a clean 422 for the client),
  `Account.applyChange`, and the database CHECK `ck_accounts_user_balance_non_negative`.
- If the cache and the entries ever disagree, the entries are right.

## Consequences

- Reading a balance is one primary-key lookup, however long the history.
- The same fact is stored twice and could drift. Every change goes through one code path, and
  `GET /admin/trial-balance` compares every cached balance with the sum of its entries, so drift is
  detected, not just prevented.
- Each posting also updates one row per account, which serializes heavy traffic on one account (accepted
  in ADR 0002).
