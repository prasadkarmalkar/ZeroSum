# ADR 0004: Open accounts from a synchronous "user registered" event

- **Status:** Accepted

## Context

Every user needs an account, and a user without one must be impossible, even if something fails halfway.
But `auth` shouldn't know accounts exist (module rules: `auth` depends only on `common`), and `account`
shouldn't depend on `auth`'s internals.

## Decision

`AuthService` publishes a `UserRegisteredEvent(userId)` after saving the user. The `account` module's
`UserRegisteredListener` handles it and calls `AccountService.openUserAccount`.

- **The event lives in `common.event`,** so the listener depends only on `common`, not on `auth`. It
  carries just the id, not the `User` entity.
- **It's a plain `@EventListener`, not `@TransactionalEventListener`.** It runs synchronously, in the same
  thread and the **same transaction** as the registration. If opening the account fails, the user is rolled
  back too.
- The seeded admin is created through the same path, so the admin gets an account as well.

## Consequences

- Atomic: `RegistrationAtomicityTest` makes account creation fail and checks that no user is left behind.
- `auth` stays unaware of accounts. New reactions to registration (a welcome email, say) are new
  listeners, not changes to `AuthService`.
- Because it's synchronous, a slow or failing listener slows or fails registration. That's intended for
  account creation, which must happen. Anything optional, like email, should instead use
  `@TransactionalEventListener` (after commit) or run asynchronously.
- An in-process event isn't a message queue: if a second service ever needs to react, this becomes an
  outbox or broker.
