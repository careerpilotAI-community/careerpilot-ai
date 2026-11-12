# Testing and Quality Checks

This guide describes the baseline checks for the frontend and backend. It does not claim coverage for product features that have not yet been implemented.

## Backend — Java 21 / Spring Boot

Run from the repository root:

```bash
cd backend
./mvnw --batch-mode --no-transfer-progress verify
```

On Windows Command Prompt or PowerShell:

```powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Conventions:

- Use JUnit 5 for all tests.
- Use `@SpringBootTest` for application-context and full application integration tests; keep these relatively few because they are slower.
- Use Mockito for isolated service/unit tests where collaborators should be replaced with test doubles.
- Use MockMvc for MVC/controller tests when web endpoints are introduced.
- Add database integration tests with the selected PostgreSQL test strategy when persistence is implemented; do not substitute H2 for PostgreSQL-specific behavior without an explicit reason.
- Tests must not depend on production credentials, external services, or personal data.

Current baseline: the backend contains an application-context smoke test only. There are no domain services, controllers, repositories, or persistence flows yet, so feature-level tests will be added alongside those implementation slices.

## Frontend — React / TypeScript

Run from the repository root:

```bash
cd frontend
npm ci
npm run lint
npm run format:check
npm run test:run
npm run build
```

Conventions:

- Use Vitest as the test runner and jsdom as the browser-like environment.
- Use React Testing Library to test rendered behavior through accessible roles, labels, and user-visible text.
- Use `@testing-library/user-event` for realistic user interactions.
- Prefer focused behavior assertions over implementation-detail assertions or large snapshots.
- Keep test files next to the feature/component they cover using `.test.ts` or `.test.tsx`.
- Use ESLint for static analysis and Prettier for consistent formatting.
- Do not commit generated output, coverage reports, or `node_modules`.

## CI expectations

The pull-request workflow should run the same deterministic checks as local development:

1. Backend: Java 21, Maven Wrapper, `verify`.
2. Frontend: Node 22, `npm ci`, lint, formatting check, tests, and production build.

A green CI run validates only the checks that actually execute. It is not evidence of product-feature completeness, production readiness, or configured branch protection.
