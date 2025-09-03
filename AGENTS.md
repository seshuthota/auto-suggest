# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot app code. Key packages: `api/` (controllers), `service/` (SuggestService engines), `model/`.
- `src/main/resources`: config and SQL (`application.yml`, `schema.sql`).
- `src/test/java`: JUnit 5 tests (service, controller, FTS, benchmarks).
- `src/test/resources`: test profile config.
- API: `GET /suggest?q=mic&limit=10&mode=PREFIX|CONTAINS|FUZZY`.

## Build, Test, and Development Commands
- Build: `./mvnw -q package`
- Run app: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`
- Benchmarks (large data): `./mvnw -Dbench=true -Dbench.records=50000 -Dbench.iters=1000 test`
- Change engine: set `suggest.engine=sqlite-like|sqlite-fts|oracle-text` in `application.yml` or via `-D`.

## Coding Style & Naming Conventions
- Java 21, Spring Boot. Indentation: 4 spaces, no tabs.
- Naming: Classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`.
- Keep methods small and cohesive; avoid premature abstractions.
- No linter configured; follow existing style and organize imports.

## Testing Guidelines
- Frameworks: JUnit 5, Spring Boot Test, MockMvc.
- Test names end with `*Test.java`; keep scopes focused (service vs controller).
- Seed data inside tests; for FTS tests call `INSERT INTO people_fts(people_fts) VALUES('rebuild')` after inserts.
- Benchmark tests run only with `-Dbench=true` and print p50/p95; don’t enable in CI by default.

## Commit & Pull Request Guidelines
- Commits: concise, imperative mood (e.g., "Add FTS benchmark"). Group related changes.
- PRs: include context, screenshots/logs when relevant, and steps to verify. Link issues and reference commands.
- Avoid unrelated refactors in feature/bugfix PRs; keep diffs surgical.

## Security & Configuration Tips
- SQLite is default; for Oracle set `spring.datasource.*` via env/secret, not committed files.
- Never commit credentials. Validate inputs; we sanitize FTS/Oracle Text queries.
- Set `suggest.cache.enabled` for hot prefixes; `spring.jdbc.template.query-timeout` limits slow queries.

## Architecture Overview
- Single service (`SuggestService`) with pluggable engines: SQLite LIKE (prefix), SQLite FTS5 (ranked), Oracle Text (rich search).
- Ordering favors exact/starts-with, then shorter names, then popularity.
- Caching via Caffeine; toggle with `suggest.cache.enabled`.
