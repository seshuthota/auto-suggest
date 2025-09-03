# Autosuggest API (Spring Boot)

A fast, pragmatic type-ahead/autosuggest service with pluggable search engines:
- SQLite LIKE (prefix and simple contains)
- SQLite FTS5 (tokenized prefix with ranking via BM25)
- Oracle Text (rich contains, prefix, fuzzy, diacritics)

It exposes a single HTTP endpoint and a unified service (`SuggestService`) that switches engines via config.

## Quick Start

- Build: `./mvnw -q package`
- Run: `./mvnw spring-boot:run`
- Try: `GET http://localhost:8080/suggest?q=micr&limit=10&mode=PREFIX`

Docs: OpenAPI at `http://localhost:8080/v3/api-docs`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

SQLite is preconfigured. Schema and sample data load from `src/main/resources/schema.sql`.

## Configuration

- Engine: set in `src/main/resources/application.yml` (or with `-D` overrides)
  - `suggest.engine`: `sqlite-like` | `sqlite-fts` | `oracle-text`
  - `suggest.cache.enabled`: `true|false` (Caffeine cache)
  - `suggest.defaults.enabled`: return popular defaults for very short queries
- Data source:
  - SQLite (default): `spring.datasource.url=jdbc:sqlite:app.db`
  - In‑memory tests: `jdbc:sqlite:file:memdb1?mode=memory&cache=shared`
  - Oracle: set `spring.datasource.url`, `username`, `password` and provide Oracle Text index on `PEOPLE(NAME)`

## API

- Endpoint: `GET /suggest`
  - Query params:
    - `q` (string, required)
    - `limit` (int, default 10, max 50)
    - `mode` = `PREFIX|CONTAINS|FUZZY` (engine-dependent)
- Response: JSON array of `{ "value": string, "score": number|null }`

- Defaults endpoint (feature-flagged): `GET /suggest/defaults?limit=10`
  - Enabled with `suggest.defaults.enabled=true`
  - Returns popular suggestions ordered by `popularity DESC, length(name), name`

- Popularity tracking: `POST /suggest/track`
  - Body: `{ "id": 123 }` or `{ "value": "Microsoft" }`
  - Increments `people.popularity` to influence ordering (ties only for LIKE; secondary for FTS)

## Project Structure

- `src/main/java/com/example/autosuggest`
  - `api/` REST controller (`/suggest`)
  - `service/` `SuggestService` with engines (LIKE, FTS5, Oracle Text)
  - `model/` small DTOs (e.g., `Suggestion`)
- `src/main/resources` app configuration and DB init (`application.yml`, `schema.sql`)
- `src/test/java` unit/integration tests and parameterized benchmarks

## Database

- Base table: `people(id INTEGER PRIMARY KEY, name TEXT NOT NULL, popularity INTEGER DEFAULT 0)`
- Index: `CREATE INDEX idx_people_name_nocase ON people(name COLLATE NOCASE);`
- FTS5 (optional): `people_fts` (external content); tests rebuild with `INSERT INTO people_fts(people_fts) VALUES('rebuild')`.
- Oracle Text: create a CONTEXT index with a BASIC_LEXER and WORDLIST (see AGENTS.md for pointers).

## Testing & Benchmarks

- Run tests: `./mvnw test`
- Benchmarks (disabled by default):
  - `./mvnw -Dbench=true -Dbench.records=50000 -Dbench.iters=1000 -Dbench.warm=200 test`
  - Engines covered: LIKE and FTS5; caching disabled in benchmarks for fair DB timings.

## Admin & FTS Maintenance

- Ensure triggers (SQLite FTS external-content): `POST /admin/fts/ensure-triggers`
- Rebuild FTS index: `POST /admin/fts/rebuild`
- Optimize FTS index: `POST /admin/fts/optimize`
- Property: `suggest.fts.manage=true` enables these operations. Triggers keep `people_fts` in sync on INSERT/UPDATE/DELETE.

## Notes & Safety

- Inputs are sanitized for FTS5 and Oracle Text; very short queries return empty (or defaults if enabled).
- Configure `spring.jdbc.template.query-timeout` (set to 2s by default) to bound latency.
- Never commit credentials; for Oracle use environment variables/secret stores.

See `AGENTS.md` for contributor guidelines.
