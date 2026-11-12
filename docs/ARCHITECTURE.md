# CareerPilot AI — Target Architecture

**Status:** Proposed architecture approved for documentation; implementation is gated on review.  
**Last updated:** 2026-11-12  
**Scope:** Deployment-independent target architecture for a multi-user application. This document describes intended design, not completed functionality.

## 1. Architecture decisions

| Concern | Decision | Implication |
|---|---|---|
| Deployment priority | **All modes** — local, self-hosted, and cloud | One codebase and consistent application behavior across environments |
| Document storage | **Hybrid** — local disk and cloud/object storage | Select the storage adapter through environment configuration |
| AI provider | **Switchable** — local Ollama and cloud AI providers | Keep provider-specific APIs behind a common application interface |
| Application tenancy | **Multi-user** | Each authenticated user can access only their own records and documents |
| Relational database | **PostgreSQL 17** | Common database baseline across local, self-hosted, and cloud deployments |
| Schema migrations | **Flyway** | Versioned, reviewable schema changes; implementation is not started |
| Local database setup | **Docker Compose** | Reproducible local PostgreSQL 17 service; native PostgreSQL remains an alternative |
| Browser authentication | **Server-side sessions with a secure HttpOnly cookie** | The backend owns session state; the browser sends the session cookie automatically |
| Mobile support | **Deferred** | The initial authentication design targets browser clients; mobile-specific authentication will be reviewed when mobile is prioritized |

Specific hosting vendors, object-storage vendors, AI vendors beyond the local Ollama option, Flyway version/configuration, and detailed file/operational policies remain open until their respective design gates. See [File Storage Architecture](architecture/storage/FILE_STORAGE_ARCHITECTURE.md) for file safety, [AI Provider Architecture](architecture/ai/AI_PROVIDER_ARCHITECTURE.md) for provider privacy and safety, and [Identity and Security Architecture](architecture/security/IDENTITY_SECURITY_ARCHITECTURE.md) for account lifecycle, authorization, session hardening, and security operations.

## 2. High-level architecture

The frontend communicates only with the backend API. The backend owns authentication, authorization, business rules, persistence, document access, and AI-provider orchestration. Provider adapters isolate infrastructure choices from product features.

\`\`\`mermaid
flowchart TB
    U[User / Browser] --> FE[React 19 + Vite + TypeScript]
    FE -->|HTTPS / REST API| API[Spring Boot 3.5 + Java 21]
    API --> AUTH[Session Authentication & Authorization]
    API --> APP[CareerPilot Application Services]
    APP --> DB[(PostgreSQL 17)]
    APP --> FS[Document Storage Adapter]
    APP --> AI[AI Provider Adapter]
    FS --> LOCAL[(Local File System)]
    FS --> OBJECT[(Cloud / S3-compatible Object Storage)]
    AI --> OLLAMA[Local Ollama]
    AI --> CLOUD[Configured Cloud AI Provider]
    API -.-> OBS[Health, Logs & Metrics]
\`\`\`

The diagram shows target components. It does not imply that authentication, persistence, storage adapters, AI integration, or observability beyond the existing health endpoint are already implemented.

For the expanded diagrams, see [System Design Diagrams](architecture/SYSTEM_DESIGN_DIAGRAMS.md). The database model and operating strategy are in [Database Architecture](architecture/database/DATABASE_ARCHITECTURE.md).

## 3. Application components

### Frontend
- React 19, Vite, and TypeScript.
- Calls backend APIs; it must not connect directly to PostgreSQL, document storage credentials, or AI provider secrets.
- Uses environment-specific API configuration without embedding secrets in the browser bundle.
- Relies on the browser to send the session cookie; application JavaScript must not be able to read the authentication cookie.
- Sends the CSRF token using the agreed request header for state-changing requests.

### Backend
- Java 21 and Spring Boot 3.5.x with Maven.
- Exposes the REST API and owns business logic.
- Enforces authentication and per-user authorization at the service/data access boundary.
- Integrates with persistence, document storage, and AI through interfaces/adapters.
- Keeps provider credentials on the server and out of frontend code and logs.
- Creates, validates, rotates where appropriate, expires, and invalidates server-side sessions.

### Database
- PostgreSQL 17 is the common relational database across deployment modes.
- Flyway is the selected schema migration tool; migration scripts will be reviewed and version-controlled with the application.
- Docker Compose is the selected convenience for local PostgreSQL startup; it is not a production deployment requirement or hard runtime dependency.
- User-owned records must carry a non-null ownership relationship, and queries/mutations must enforce it.
- Composite owner-aware foreign keys should prevent relationships from crossing user boundaries.
- See [Database Architecture](architecture/database/DATABASE_ARCHITECTURE.md) for conceptual entities, tenant isolation, migration lifecycle, and backup/restore strategy.

### Document storage — hybrid
See [File Storage Architecture](architecture/storage/FILE_STORAGE_ARCHITECTURE.md) for the backend-owned storage interface, private local/object adapters, quarantine and malware scanning, owner authorization, retention, and recovery design.

### AI integration — switchable
See [AI Provider Architecture](architecture/ai/AI_PROVIDER_ARCHITECTURE.md) for the provider-neutral contract, Ollama/cloud adapters, server-side selection, data minimization and consent, prompt-injection controls, cost limits, resilience, and testing. AI output remains advisory and users review generated career materials; consequential actions require separate explicit approval.

## 4. Deployment modes

All modes run the same application artifacts. Differences are configuration and infrastructure, not separate product forks.

| Mode | Frontend | Backend | Database | Documents | AI |
|---|---|---|---|---|---|
| Local | Local development server or local static build | Local Spring Boot process/container | Local PostgreSQL | Local disk | Ollama or configured cloud provider |
| Self-hosted | Served locally or through a reverse proxy | Self-hosted Spring Boot process/container | Self-hosted PostgreSQL | Local disk or configured object storage | Ollama or configured cloud provider |
| Cloud | Static frontend hosting/CDN | Hosted Spring Boot service/container | Managed PostgreSQL | Configured object storage | Configured cloud provider; Ollama only where reachable and intentionally operated |

A local installation is not automatically reachable from the public internet. Remote access requires deliberate network configuration, HTTPS, and appropriate firewall/reverse-proxy controls. A cloud deployment is not automatically free or permanently available on a provider's free tier.

## 5. Configuration strategy

Use Spring profiles for environment-specific defaults and environment variables/secrets for deployment-specific values. Keep safe examples in version control; never commit real credentials.

Planned profile names:
- \`local\`
- \`selfhosted\`
- \`cloud\`

Illustrative backend settings (names are design examples and may be refined during implementation):

| Setting | Purpose |
|---|---|
| \`SPRING_PROFILES_ACTIVE\` | Select deployment profile |
| \`DATABASE_URL\`, \`DATABASE_USERNAME\`, \`DATABASE_PASSWORD\` | PostgreSQL connection |
| \`STORAGE_PROVIDER\` | Select local or object storage adapter |
| \`STORAGE_LOCAL_ROOT\` | Local private document directory |
| \`STORAGE_ENDPOINT\`, \`STORAGE_BUCKET\` | Object storage destination |
| \`STORAGE_ACCESS_KEY\`, \`STORAGE_SECRET_KEY\` | Object-storage credentials |
| \`AI_PROVIDER\` | Select Ollama or cloud adapter |
| \`OLLAMA_BASE_URL\`, \`OLLAMA_MODEL\` | Local AI endpoint and model |
| \`AI_API_KEY\`, \`AI_MODEL\` | Cloud AI credentials and model |

Exact property names, validation rules, secret management, and profile files will be finalized during implementation. Frontend configuration may contain public API base URLs only; it must never contain database, storage, or AI secrets.

## 6. Authentication, authorization, and privacy boundaries

### 6.1 Selected browser authentication model

CareerPilot AI will use **server-side session authentication** for the initial browser application.

1. The user submits credentials to the backend over HTTPS.
2. The backend verifies the credentials and creates a server-side authenticated session.
3. The backend returns an opaque session identifier in a cookie configured as HttpOnly, Secure in remote/production deployments, SameSite=Lax by default, Path=/, and without a Domain attribute where the host-only cookie model is supported.
4. The browser automatically includes the cookie on eligible requests. Frontend JavaScript cannot read the authentication cookie.
5. The backend resolves the session on each request and derives the trusted user identity from the server-side session, never from a client-supplied user ID.
6. Logout invalidates the server-side session and expires the browser cookie.

The cookie is a transport mechanism for the session identifier; it is not a JWT. Session state remains server-side. The exact cookie name, session repository, timeout values, and local-development cookie profile will be finalized during implementation.

### 6.2 Required security controls

- **HTTPS:** Require HTTPS for remote, self-hosted, and cloud deployments. Secure cookies must be enabled in those environments. Any localhost-only development exception must be isolated to the local profile and must not weaken deployed settings.
- **Cookie scope:** Use HttpOnly, Secure, SameSite=Lax by default, Path=/, and a host-only cookie (no Domain attribute). Reassess SameSite only if a documented cross-site deployment requirement emerges.
- **CSRF protection:** Keep Spring Security CSRF protection enabled for cookie-authenticated state-changing requests. Provide a CSRF token through a deliberate SPA-compatible mechanism and require it in the configured request header. SameSite is defense-in-depth, not a replacement for CSRF protection.
- **CORS:** Allow only explicitly configured frontend origins. Do not use wildcard origins with credentialed requests. Permit credentials only for trusted origins and the required methods/headers.
- **Session lifecycle:** Regenerate the session identifier after successful authentication and privilege changes where applicable; enforce idle and absolute expiration; invalidate sessions on logout and account security events. Define session-store behavior before horizontal scaling.
- **Credential handling:** Store passwords using a modern adaptive password-hashing algorithm with unique salts; never store or log plaintext passwords. Select and configure the concrete algorithm and work factor during implementation.
- **Abuse resistance:** Rate-limit login and account-recovery endpoints, use generic authentication failure responses, and monitor suspicious repeated attempts without logging secrets.
- **Authorization:** Enforce role/permission checks and owner-scoped access on the backend for every protected operation. Never trust a user ID, role, filename, or record ownership supplied by the browser.
- **Data protection:** Keep private documents outside public web roots; validate uploads; use least-privilege database/storage credentials; avoid logging session IDs, credentials, resume contents, contact details, or other sensitive personal data.
- **Account lifecycle:** Define verified registration, password reset/recovery, session invalidation after password changes, account deletion, and data export before public multi-user release.
- **Testing:** Add tests for CSRF rejection, cookie flags, session fixation protection, expiration/logout, unauthorized requests, role boundaries, and cross-user/IDOR access attempts.

### 6.3 Trade-offs and rationale

**Why sessions are selected for the browser MVP**
- A server-side session is straightforward for a same-origin or deliberately configured browser application.
- HttpOnly cookies prevent ordinary frontend JavaScript from reading the authentication credential, reducing exposure from token theft through some classes of client-side script injection.
- The backend can invalidate a session on logout or account-security events without waiting for a self-contained access token to expire.
- The browser handles cookie transmission, avoiding application-managed bearer-token storage.

**Costs and limitations**
- Cookie authentication requires explicit CSRF protection and careful CORS/SameSite configuration.
- The backend must maintain session state. Multiple backend instances will need a shared session repository or another coordinated session strategy; the specific technology is not selected here.
- Cross-origin frontend/backend hosting needs deliberate credentialed CORS and cookie-domain design. Prefer same-site deployment where practical.
- This browser-first choice does not define a mobile-native authentication contract. Mobile support is deferred and will trigger a separate security review.

**Why JWT access/refresh tokens are not selected now**
- JWT access/refresh flows add token issuance, refresh rotation, replay detection, revocation, expiry, signing-key management, and client storage decisions.
- Those capabilities may be useful for mobile clients or independently consumed APIs, but they add complexity without a current MVP requirement.
- JWTs are not inherently safer than sessions, and putting JWTs in HttpOnly cookies would still require CSRF defenses.

## 7. Identity and security design\n\nSee [Identity and Security Architecture](architecture/security/IDENTITY_SECURITY_ARCHITECTURE.md) for registration and verification, password handling, recovery, roles, owner-scoped authorization, CSRF/CORS, security headers, secrets, audit events, account export/deletion, threat modeling, and release-blocking tests. The selected browser session model remains unchanged; detailed operational values and account policies require approval before implementation.\n\n## 8. Operational and cost principles

- Keep the application runnable locally without a paid service.
- Treat free cloud tiers as optional deployment targets, not guaranteed production infrastructure.
- Do not couple the codebase to one hosting vendor.
- Provide health checks and structured, privacy-safe logs.
- Document database and document backup/restore procedures before production use.
- Make resource-intensive or externally billed AI providers opt-in and configurable.

## 9. Review gates and open decisions

The deployment, storage, AI strategy, multi-user direction, PostgreSQL 17 baseline, Flyway migration tool, Docker Compose local setup, browser session authentication, and deferral of mobile support are documented decisions. Before implementation begins, review and confirm:

1. This architecture and the Mermaid component/system diagrams.
2. Database entity scope, owner-aware constraints, indexing, Flyway/JDBC compatibility, migration execution policy, and local Compose setup.
3. PostgreSQL backup retention, encryption, off-site storage, point-in-time recovery, and tested RPO/RTO targets.
4. File limits, allowed formats, retention, backup, and malware-scanning policy.
5. Initial AI provider adapter contract, cloud provider terms, consent/disclosure behavior, prompt-injection controls, and cost budgets.
6. Session repository/scaling approach, cookie configuration by deployment profile, and concrete password-hashing configuration.
7. Account registration, verification, recovery, deletion, and data-export behavior.
8. Secrets management, HTTPS, CORS origins, logging, monitoring, and restore expectations for a public multi-user deployment.
9. Registration policy, verification delivery, password-hashing parameters, login abuse controls, session repository/timeouts, CSRF/CORS/proxy settings, MFA scope, role provisioning, account export/deletion, and security operations as detailed in [Identity and Security Architecture](architecture/security/IDENTITY_SECURITY_ARCHITECTURE.md).\n10. The first deployment target used for end-to-end validation.

**Implementation gate:** Do not begin the next application-code milestone until this document and its remaining open decisions have been reviewed. Approval of the documented authentication direction does not by itself authorize implementation or resolve the operational details above.


## 10. Build and quality baseline

The repository uses reproducible toolchain entry points so local development and pull-request validation run the same commands:

- **Backend:** Java 21 with the Maven Wrapper, pinned to Maven 3.9.11. Developers do not need a separately installed Maven distribution.
- **Frontend:** Node.js 22 with a committed npm lockfile. Use `npm ci` for clean installs; dependency changes must update and commit both the package manifest and lockfile.
- **Pull-request checks:** backend Maven `verify`; frontend ESLint, Prettier check, Vitest/React Testing Library tests, and production build.
- **Test conventions:** JUnit 5 and Spring Boot Test for backend tests, Mockito for isolated unit tests, Vitest and React Testing Library for frontend behavior tests.

Branch 23 / PR #30 merged this baseline at `37f0f6d6054e49a81bb928eb14bb9920b1fb23f9`. PR #30 records successful permanent CI run `43164048115` on its latest PR commit. Branch-protection and required-check enforcement remain unverified. The baseline does not imply that product features, persistence, authentication, or production-readiness controls are implemented.
