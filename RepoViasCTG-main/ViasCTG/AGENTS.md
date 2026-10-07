# Repository Guidelines

## Project Structure & Module Organization

ViaCTG is a Spring Boot API built with Maven and Java 21. Its MongoDB backend follows this fixed package layout under `src/main/java/com/viactg/`:

- `config/`: Spring Security, CORS and OpenAPI beans.
- `controller/`: REST endpoints; never return Mongo documents directly.
- `service/`: business rules, ownership checks and reference validation.
- `repository/`: Spring Data `MongoRepository` interfaces.
- `model/`: MongoDB documents and embedded types.
- `dto/` and `mapper/`: request/response contracts and document conversion.
- `exception/`: domain exceptions and the central `@ControllerAdvice`.
- `security/`: JWT filter, JWT service and `UserDetailsService`.

`ViactgApplication.java` is the application entry point. Runtime settings are in `src/main/resources/application.properties`; tests mirror the package under `src/test/java`. Use `./mvnw` for all Maven commands.

## Build, Test, and Development Commands

- `./mvnw test` — compile and run the JUnit 5 test suite.
- `./mvnw clean verify` — full clean, compile, test and verify lifecycle.
- `./mvnw spring-boot:run` — start the API; requires `MONGODB_URI` and `JWT_SECRET`.
- `./mvnw clean package` — package the application under `target/`.

Set `MONGODB_URI` and a Base64 JWT secret outside committed source. Do not use a production database when running local tests.

## Coding Style & API Conventions

Use four spaces, same-line braces, lowercase packages and conventional Java names. Keep controllers thin: validate request DTOs, obtain the authenticated principal, and delegate to services. Keep persistence and authorization rules in services.

Mongo IDs are `String`; timestamps use `Instant`. `Calle` and `HistorialEstado` are embedded objects, never repositories or collections. New API input must use a validated request DTO; responses must use response DTOs and must never expose `passwordHash`.

Preserve these business rules: BCrypt password storage, unique email, one confirmation per `(reporteId, usuarioId)`, no physical category deletion, and atomic state/history updates for a report. Administrative endpoints require method-level authorization.

## Testing Guidelines

Use JUnit 5. Name tests with the `Tests` suffix and methods descriptively. Add unit tests for service rules and integration tests for Mongo repositories/controllers when a disposable MongoDB instance is available. Run `./mvnw test` before submitting changes.

## Commit, Security and Configuration Guidelines

Use concise Conventional Commit-style subjects such as `feat: add report state transition` or `test: cover duplicate confirmation`. Do not commit Mongo credentials, JWT secrets, passwords, tokens or production URLs. Review configuration and security changes carefully, especially CORS, endpoint permissions and index definitions.
