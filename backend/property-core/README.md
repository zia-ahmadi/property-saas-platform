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
other application routes require a valid bearer token.

### JWT Secret Configuration

**Required for Production:**
Configure a unique 32-byte-or-longer signing key as Base64 before starting the service:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

**Development Fallback:**
If `JWT_SECRET` is not set, the application will use a development-only fallback secret.
**WARNING:** The fallback is for local development only - DO NOT use in production!

**Environment Variables:**

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | Development fallback (32 bytes) | **Required in production** - Base64-encoded secret, minimum 32 bytes when decoded |
| `JWT_ISSUER` | `https://property-core.local` | JWT issuer claim |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `3600` | Token lifetime in seconds |

**Important:** Do not reuse or commit a JWT secret. User passwords are stored as BCrypt hashes.
