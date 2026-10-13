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

## Project Structure

```
src/
├── main/java/com/zerosum/zerosum/   # application code
├── main/resources/                  # configuration
└── test/java/com/zerosum/zerosum/   # tests
```

## Contributing

_TODO_

## License

_TODO_
