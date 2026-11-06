# Local Development

CareerPilot AI is in its application-foundation phase. Run the backend and frontend separately during local development.

## Prerequisites
- Java 21 (JDK)
- Maven 3.9+
- Node.js 22+
- npm

PostgreSQL is selected for the upcoming persistence phase. The current starter does not yet connect to a database.

## Backend
From the repository root:
```bash
cd backend
mvn spring-boot:run
```
The API starts at `http://localhost:8080`.
Health check: `http://localhost:8080/actuator/health`
Run backend tests with `mvn verify`.

## Frontend
In a second terminal:
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173`. Vite proxies `/api` and `/actuator` requests to the backend on port 8080.
Create a production frontend build with `npm run build`.

## Configuration and secrets
- Do not commit `.env` files, passwords, API keys, tokens, or personal resume data.
- Keep local secrets outside version control.
- Backend port can be changed with the `SERVER_PORT` environment variable.
- The frontend proxy currently targets the local backend at port 8080.

## Current scope
This foundation provides a Spring Boot application entry point, Actuator health endpoint, smoke test, React starter screen, Vite development proxy, and GitHub Actions build validation.

Authentication, PostgreSQL persistence, job/profile features, AI provider integration, document storage, and cloud deployment are intentionally deferred to later roadmap phases.
