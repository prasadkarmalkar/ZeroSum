# ADR 0005: Stateless JWT access tokens only

- **Status:** Accepted

## Context

The API is used by programs and Swagger, not by browser forms, and needs to know who the caller is and
whether they're an admin. Options were server sessions (cookies), opaque tokens checked against a table,
or self-contained signed tokens.

## Decision

`POST /auth/login` returns a **JWT access token** signed with **RS256**. Every other request sends it as
`Authorization: Bearer <token>`, and Spring Security's resource server verifies the signature, expiry
and issuer.

- **Claims:** `sub` (user id), `email`, `roles` (`USER` or `ADMIN`, mapped to `ROLE_*` authorities), plus
  `iss`, `iat`, `exp` and `jti`.
- **Lifetime:** 1 hour (`jwt.access-token-ttl`).
- **Keys:** an RSA key pair. The private key signs and the public key verifies, so verification could move
  to another service without sharing the signing key. Tests use their own committed test-only pair.
- **Stateless:** no sessions, CSRF disabled (no cookies to forge).
- **Identity:** the caller's identity always comes from the token (`@CurrentUserId`), never from the request.

## Consequences

- No database lookup to authenticate a request, and nothing to share between instances.
- **No logout or revocation.** A token stays valid until it expires, even after "logout", a password
  change, or a role change, and `/auth/me` reports what the token says. The 1-hour lifetime bounds that.
- **No refresh tokens,** so clients log in again every hour. The planned fix: short-lived access tokens
  plus refresh tokens stored server-side, which can be revoked.
- Losing the private key means anyone can mint tokens. It lives outside the source tree (`keys/`,
  gitignored) and should come from a secret store in production.
