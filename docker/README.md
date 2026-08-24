# Local Docker infrastructure

P0-04 provides a local PostgreSQL service for Hippocampus development. It runs
PostgreSQL 18 with the `vector` and `pg_trgm` extensions available in the local
development database.

Start the database:

```powershell
docker compose up -d postgres
```

Stop the database:

```powershell
docker compose stop postgres
```

Local connection defaults:

- Database: `hippocampus`
- User: `hippocampus`
- Password: `hippocampus`
- Host port: `5432`

Override the host port when needed:

```powershell
$env:HIPPOCAMPUS_POSTGRES_PORT = "15432"
docker compose up -d postgres
```

Database files are stored in the named Docker volume
`hippocampus-postgres-data`, so local data persists across container restarts.
The committed credentials are local-development-only values. Pilot uses
separately managed infrastructure and secrets.

Flyway, application schema, and Spring datasource integration are deferred to
their owning implementation tasks.
