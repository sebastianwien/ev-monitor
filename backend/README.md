# EV Monitor - Backend

This directory contains the robust REST API powering the EV Monitor application, built utilizing **Spring Boot 3.5**, **Java 21** and **Gradle**.

## 🤖 AI Assistant Context & Architectural Rules
This backend strictly adheres to **Clean Architecture** patterns. It is critical to enforce and maintain these boundaries when modifying or adding business features:

1. **`domain` layer** (`com.evmonitor.domain`)
   - **Rule**: Must remain completely framework-agnostic. No Spring Boot or JPA imports are permitted here.
   - **Contains**: Core business Entities (e.g., `EvLog`), Value Objects (e.g., `DrivingStyle`), and primary Repository Interface Ports.

2. **`application` layer** (`com.evmonitor.application`)
   - **Rule**: Coordinates domain logic acting as the concrete use cases. May utilize standard Java dependencies and minimal Spring dependency injection (`@Service`), but must definitively not have visibility into HTTP request lifecycles, or Database specific entities/table rows.
   - **Contains**: Application Services, mapping logics, and generic Request/Response DTOs.

3. **`infrastructure` layer** (`com.evmonitor.infrastructure`)
   - **Rule**: Acts as the system edges containing all framework-specific adapters required to bridge ports into outer-world dependencies. This is the isolated location where Spring Web (`@RestController`) and Spring Data JPA annotations (`@Entity`, `@Table`) must live.
   - **Contains**: External-facing REST Controllers (`web`), Persistence mapping logic and concrete implementations (`persistence`), Database connections Configuration protocols.

## Prerequisites

- **Java 21** required natively. Note: The `Dockerfile` specifically isolates Eclipse Temurin 21 within an Ubuntu **Jammy** minimal layer due to Gradle JVM compilation edge-cases that cause Segment Faults alongside Alpine architecture.
- **Gradle Wrapper** (The project utilizes `gradlew` wrapper version 8.7+ enabling proper compatibility arrays with Java 21 outputs).

## Running Locally

To run the backend independently of the main orchestrator loop:

1. Ensure a PostgreSQL background instance is functional and matches the declared properties located in `src/main/resources/application.yml`. Alternately, override the required environmental variables globally: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
2. Run the application instance locally:
   ```bash
   ./gradlew bootRun
   ```

The application bindings deploy directly to port `8080`.

## Testing Structure

Execute the enclosed test suites covering isolated Service layers (Unit with Mockito) alongside WebMvc integration mock-request slices using:
```bash
./gradlew test
```

## Compilation Build

To compile a clean state and output a production `app.jar` safely resolving memory structures (skipping runtime tests):
```bash
./gradlew clean build -x test
```

## References
- Go back to the [Root README](../README.md)
- Check the [Frontend Documentation](../frontend/README.md)
