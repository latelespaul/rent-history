# rentrix-server

Rentrix v1 API (Spring Boot). Design and configuration: `rentrix-docs/architecture/SYSTEM_DESIGN.md` (§17 lists every env var).

## Upgrading from the old app

- v1 needs a fresh, empty database. Its Flyway `V1__baseline_schema.sql` replaces the old `V1__initial_schema.sql`, so the API will not start on a database that ran the old V1.
- Point `DB_URL` at a new database. The old schema and data are not migrated; they are left as they are.
- Do not reuse the old docker-compose `mysql-data` volume. The stack now lives in the root `docker-compose.yml` and keeps SQLite on a new volume, `rentrix-v1-sqlite-data` (the optional `postgres` profile uses `rentrix-v1-postgres-data`).

Env contract changes:

| Old | v1 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | removed; `DB_URL` (full JDBC URL) is required in `prod` |
| `docker` profile | removed; use exactly one of `dev`, `test`, `prod` (`SPRING_PROFILES_ACTIVE` is required) |
| `JWT_ACCESS_TOKEN_EXPIRY_MS`, `JWT_REFRESH_TOKEN_EXPIRY_MS` | removed; no env var (the access token lifetime defaults to 15 minutes) |
| `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_BUCKET`, `AWS_PRIVATE_BUCKET`, `AWS_PUBLIC_URL`, `S3_PUBLIC_BUCKET`, `S3_PRIVATE_BUCKET`, `S3_PUBLIC_BASE_URL` | removed; media storage returns later as `STORAGE_BACKEND` + `S3_*` (SYSTEM_DESIGN §17) |
| CORS origins hard-coded in `application-prod.yaml` | `CORS_ALLOWED_ORIGINS` (comma list), required in `prod` |
| `JWT_SECRET` | still required in `prod`, now at least 32 bytes |
