# Local Development

CareerPilot AI is in its application-foundation phase. Run the backend and frontend separately during local development.

## Prerequisites
- Java 21 (JDK)
- Node.js 22+
- npm (bundled with Node.js)

The Maven Wrapper pins Maven 3.9.11 and downloads it automatically on first use; a separate Maven installation is not required. PostgreSQL is selected for the upcoming persistence phase. The current starter does not yet connect to a database.

## Backend

From the repository root:

**Windows (PowerShell / Command Prompt):**
```powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress spring-boot:run
```

**Linux / macOS / Git Bash:**
```bash
cd backend
./mvnw --batch-mode --no-transfer-progress spring-boot:run
```

The API starts at `http://localhost:8080`.
Health check: `http://localhost:8080/actuator/health`

Run backend verification and tests with:

- Windows: `.\mvnw.cmd --batch-mode --no-transfer-progress verify`
- Linux / macOS / Git Bash: `./mvnw --batch-mode --no-transfer-progress verify`

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
- Keep local secrets outside version control.
- Backend port can be changed with the `SERVER_PORT` environment variable.
- The frontend proxy currently targets the local backend at port 8080.

## Current scope
This foundation provides a Spring Boot application entry point, Actuator health endpoint, context-load smoke test, React starter screen, Vite development proxy, Maven Wrapper, deterministic frontend dependency lockfile, and GitHub Actions build/quality validation.

Authentication, PostgreSQL persistence, job/profile features, AI provider integration, document storage, and cloud deployment are intentionally deferred to later roadmap phases.
