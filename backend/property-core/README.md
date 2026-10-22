# Property Core

## PostgreSQL and Flyway

Start PostgreSQL using the repository's Compose configuration from the project root:

```bash
docker compose up -d postgres
```

Then start the service from this directory:

```bash
./mvnw spring-boot:run
```

Flyway automatically applies versioned SQL from
`src/main/resources/db/migration` at application startup. The initial migration,
`V1__create_core_schema.sql`, creates the organization-scoped property, leasing,
billing, payment, vendor, and maintenance schema. Hibernate validates the schema
and does not create or alter database objects.

The default connection matches the local Compose service (`admin` / `admin`) and
is for local development only. Configure other connections with:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/property_db` |
| `DB_USERNAME` | `admin` |
| `DB_PASSWORD` | `admin` |
