# EV Monitor - Backend

The REST API for EV Monitor, built with **Spring Boot 3.5**, **Java 21**, and **Gradle**.

## 🤖 AI Assistant Context & Architectural Rules

This backend strictly adheres to **Clean Architecture** principles. Maintain these layer boundaries when adding or modifying features:

1. **`domain` layer** (`com.evmonitor.domain`)
   - Must remain framework-agnostic — no Spring Boot or JPA imports
   - Contains: Core entities (`User`, `Car`, `EvLog`, `CoinLog`, `VehicleSpecification`, `EmailVerificationToken`), Repository interface ports

2. **`application` layer** (`com.evmonitor.application`)
   - Coordinates domain logic as use cases
   - May use `@Service` and standard Java, but must not depend on HTTP or DB specifics
   - Contains: Application services, DTOs, request/response objects

3. **`infrastructure` layer** (`com.evmonitor.infrastructure`)
   - All framework-specific adapters live here
   - Contains: REST controllers (`web`), JPA persistence (`persistence`), security config, email service

## Prerequisites

- **Java 21** (required)
- **Gradle Wrapper** — use `./gradlew`, no global Gradle installation needed
- **PostgreSQL** running locally (or via Docker — see root `docker-compose.dev.yml`)

## Running Locally

1. Start PostgreSQL:
   ```bash
   docker compose -f ../docker-compose.dev.yml up -d
   ```

2. Start the backend (dev profile includes seed data — 3 test users, 6 cars, ~370 charging logs):
   ```bash
   SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
   ```

The API runs on `http://localhost:8080`.

Alternatively, use `./dev.sh` from the root to start the entire stack at once.

## Testing

```bash
./gradlew test
```

Covers unit tests (Mockito) and WebMvc integration slice tests.

## Build

```bash
./gradlew clean build -x test
```

Produces `build/libs/app.jar` (used by the Docker multi-stage build).

## Key Implementation Notes

- **JWT Auth**: 7-day tokens, validated via `JwtAuthenticationFilter`. Claims include `sub` (userId), `email`, `exp`.
- **Email Verification**: 256-bit `SecureRandom` tokens, 24h TTL. Login requires verified email.
- **Geohashing**: `lat/lon` from the client is converted to a 6-char geohash (~600m precision for private, 7-char ~150m for public) and the coordinates are immediately discarded. Never stored.
- **CarBrand Enum**: 43 brands with nested `CarModel` enum. Stored as `STRING` (not ordinal) for safe migrations.
- **Flyway**: DB migrations in `src/main/resources/db/migration/`.
- **OAuth2**: Infrastructure is prepared (Google/Facebook/Apple). Activate via `SPRING_PROFILES_ACTIVE=prod,oauth` + provider env vars.

## References

- [Root README](../README.md)
- [Frontend README](../frontend/README.md)