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

## Authentication

The `/api/v1/auth/register` and `/api/v1/auth/login` endpoints issue signed
HS256 bearer tokens; `/api/v1/auth/me` returns the authenticated profile. All
other application routes require a valid bearer token. Configure a unique
32-byte-or-longer signing key as Base64 before starting the service:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

`JWT_ISSUER` defaults to `https://property-core.local`; token lifetime defaults
to 3600 seconds and can be changed with `JWT_ACCESS_TOKEN_TTL_SECONDS`. Do not
reuse or commit a JWT secret. User passwords are stored as BCrypt hashes.
