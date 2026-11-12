# Local Development

CareerPilot AI uses PostgreSQL 17 for structured persistence. Run the database, backend, and frontend as separate local services.

## Prerequisites
- Java 21 (JDK)
- Node.js 22+
- npm (bundled with Node.js)
- Docker Engine with the Docker Compose plugin (or a native PostgreSQL 17 installation)

The Maven Wrapper pins Maven 3.9.11 and downloads it automatically on first use; a separate Maven installation is not required.

## Start PostgreSQL with Docker Compose

From the repository root:

**Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
# Edit .env and replace change_me_local_only with a private local password.
docker compose up -d postgres
docker compose ps
```

**Linux / macOS / Git Bash:**
```bash
cp .env.example .env
# Edit .env and replace change_me_local_only with a private local password.
docker compose up -d postgres
docker compose ps
```

The database port is bound to 127.0.0.1 by default. The named volume `careerpilot-postgres-data` preserves data across container recreation; it is not a backup. Do not run `docker compose down -v` unless you intentionally want to delete the local database volume.

The Compose service uses the pinned PostgreSQL 17.6 image tag. Update the patch tag deliberately after reviewing PostgreSQL release notes and validating the upgrade.

## Backend

From the repository root:

**Windows (PowerShell / Command Prompt):**
```powershell
cd backend
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/careerpilot"
$env:DATABASE_USERNAME = "careerpilot"
$env:DATABASE_PASSWORD = "<the password from .env>"
$env:FLYWAY_USERNAME = $env:DATABASE_USERNAME
$env:FLYWAY_PASSWORD = $env:DATABASE_PASSWORD
.\mvnw.cmd --batch-mode --no-transfer-progress spring-boot:run
```

**Linux / macOS / Git Bash:**
```bash
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/careerpilot
export DATABASE_USERNAME=careerpilot
export DATABASE_PASSWORD='<the password from .env>'
export FLYWAY_USERNAME="$DATABASE_USERNAME"
export FLYWAY_PASSWORD="$DATABASE_PASSWORD"
./mvnw --batch-mode --no-transfer-progress spring-boot:run
```

Flyway applies pending migrations before the application starts. Hibernate validates the mapped entity schema; it does not create or update tables. V1 creates the identity/profile/job/application foundation and V2 creates the JDBC-backed Spring Session tables.

The API starts at `http://localhost:8080`.

For local account registration, start the backend with the `local` profile enabled:

- PowerShell: `$env:SPRING_PROFILES_ACTIVE = "local"`
- Linux / macOS / Git Bash: `export SPRING_PROFILES_ACTIVE=local`

The local profile enables registration and permits the Vite origin `http://localhost:5173`. Registration is disabled by default in the base configuration; keep it disabled for remote deployments until a real email-verification delivery flow is implemented. Do not expose the local profile publicly.

### Authentication API

- `GET /api/auth/csrf` — obtain the CSRF token before a state-changing request.
- `POST /api/auth/register` — local-profile registration; JSON body: `{ "email": "you@example.com", "password": "at-least-12-characters" }`.
- `POST /api/auth/login` — authenticate and establish a server-side session.
- `GET /api/auth/me` — return the current authenticated account.
- `POST /api/auth/logout` — invalidate the current session.

Send the returned CSRF token in the header named by `headerName` for POST/PUT/PATCH/DELETE requests. Keep browser requests credentialed so the HttpOnly session cookie is sent. The session cookie is HttpOnly, SameSite=Lax, host-only, and Secure by default. The local profile disables Secure only for local HTTP development. Idle timeout is 30 minutes and the absolute authenticated-session lifetime is 12 hours by default; configure through environment variables when necessary.
Health check: `http://localhost:8080/actuator/health`

Run backend verification and tests with:

- Windows: `.\mvnw.cmd --batch-mode --no-transfer-progress verify`
- Linux / macOS / Git Bash: `./mvnw --batch-mode --no-transfer-progress verify`

The backend integration tests start a disposable PostgreSQL 17.6 Testcontainers database. Docker must be available when running the test suite.

## Frontend

In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` and `/actuator` requests to the backend on port 8080.

Run the frontend quality checks:

```bash
npm run lint
npm run format:check
npm run test:run
npm run build
```

Use `npm ci` for clean, lockfile-based installs. When intentionally changing frontend dependencies, update the lockfile with `npm install` and commit both `package.json` and `package-lock.json`.

## Configuration and secrets
- Do not commit `.env` files, passwords, API keys, tokens, or personal resume data.
- The committed `.env.example` contains placeholders for local development only. Replace them in your private `.env` file.
- The Compose bootstrap user is a local-development convenience, not a production least-privilege database-role design. Production/self-hosted deployments must use separate migration and runtime credentials.
- Keep local secrets outside version control.
- Backend port can be changed with the `SERVER_PORT` environment variable.
- Database URL, credentials, and pool size are configurable through `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `DATABASE_POOL_SIZE`.

## Current scope
This foundation provides a Spring Boot API, PostgreSQL 17 persistence schema and repositories for account/profile/job/application foundations, Flyway migrations, local Compose database startup, Actuator health endpoint, PostgreSQL integration tests, React starter screen, Vite development proxy, Maven Wrapper, deterministic frontend dependency lockfile, and GitHub Actions build/quality validation.

User registration/login, JDBC-backed sessions, CSRF, CORS, password hashing, and the first authentication UI are implemented in Branch 27. User-facing profile/job/application workflows, email verification and recovery, rate limiting, resume storage, AI provider integration, backup automation, and cloud deployment remain later milestones.
