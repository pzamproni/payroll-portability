# Payroll Portability — architecture specification

The monorepo contains independently organized frontend and backend projects. Repository layout does not restrict Hexagonal Architecture or Clean Architecture.

## Decision

Use Hexagonal Architecture for the platform backend. Domain and application code use only Java and inward-facing abstractions. Spring wires dependencies at the composition root. Existing public HTTP fields/statuses remain compatible; the product/repository/artifact/package names become English.

- `domain`: immutable models, business errors and rules; no Spring/JDBC/Jackson/Jakarta dependencies.
- `application.port.inbound`: use-case contracts consumed by HTTP and scheduling adapters.
- `application.port.outbound`: authorization/operation/agenda repositories, Central gateway and transaction boundary.
- `application.service`: orchestration, authorization, eligibility, idempotency, retries and transitions; no SQL or framework annotations.
- `adapter.inbound.web`: validated transport DTOs, controllers and error mapping.
- `adapter.inbound.scheduling`: scheduled entry point, logging and isolation of failed tasks.
- `adapter.outbound.persistence`: JDBC, serialization and persistence conflict translation.
- `adapter.outbound.central`: signed HTTP client implementing the Central gateway.
- `infrastructure`: shared JSON/security details used by adapters and the simulator.
- `bootstrap`: Spring composition root and configuration.
- `simulator`: isolated external-system stub; not a platform business dependency.

## Acceptance

AC-048 enforces framework-independent domain/application dependencies. AC-049 forbids inbound adapters from depending on persistence, remote adapters or application implementations. AC-050 forbids outbound adapters from depending on inbound adapters, use-case implementations or simulator internals. Each is a distinct JUnit/ArchUnit test. AC-056 separately forbids the domain from depending on application code, closing the inward-dependency rule.

Existing Gherkin criteria continue to cover observable behavior. PostgreSQL smoke, restart and browser flows must still pass. Rename must preserve local PostgreSQL storage through an explicit volume reference; fresh clones use English service/database defaults. Do not edit deployed Flyway migrations or rewrite Git history.

## Dependency and import discipline

AC-051 requires explicit imports in all Java production and test sources, including static imports. A JUnit source-convention test enforces this without adding a formatting/checkstyle dependency. The TypeScript build also rejects unused imports/locals/parameters.

Java wildcard imports do not download artifacts or enlarge the packaged application. Maven dependency declarations/scopes control artifact resolution. The aggregate test starter was replaced with the actual Spring test, JUnit API/engine, Mockito, AssertJ, JsonPath and Hamcrest libraries used by this suite. Runtime dependencies remain unchanged; test-only dependencies must not be packaged in the executable JAR.
