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
   direct call.
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

## Contributing

_TODO_

## License

_TODO_
