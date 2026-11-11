# CareerPilot AI — System Design Diagrams

**Status:** Target design for review; diagrams describe planned behavior, not implemented functionality.  
**Related document:** [Target Architecture](../ARCHITECTURE.md) · [Project Roadmap](../PROJECT_ROADMAP.md)

## 1. High-level architecture

\`\`\`mermaid
flowchart TB
    U["Users / Browser"] --> FE["React 19 + Vite + TypeScript"]
    FE -->|"HTTPS / REST"| API["Spring Boot 3.5 + Java 21 API"]
    API --> AUTH["Server-side Session Authentication & Authorization"]
    API --> APP["CareerPilot Application Services"]
    APP --> DB[("PostgreSQL 17")]
    APP --> FS["Document Storage Interface"]
    APP --> AI["AI Provider Interface"]
    FS --> LOCAL[("Local File System")]
    FS --> OBJECT[("Cloud / S3-compatible Object Storage")]
    AI --> OLLAMA["Ollama — local AI"]
    AI --> CLOUD["Configured cloud AI provider"]
\`\`\`

The browser talks to the backend API. The backend applies security and business rules, then uses adapters to reach the database, document storage, and AI provider. Provider credentials remain server-side. Browser authentication uses a server-side session identified by a Secure/HttpOnly cookie; it is not a JWT.

## 2. Browser session lifecycle

\`\`\`mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser
    participant API as Spring Boot API
    participant Session as Server-side Session Store
    participant DB as PostgreSQL 17

    User->>Browser: Submit credentials
    Browser->>API: POST sign-in over HTTPS
    API->>DB: Verify account and password hash
    DB-->>API: Account result
    API->>Session: Create authenticated session
    Session-->>API: Opaque session identifier
    API-->>Browser: Set-Cookie (HttpOnly, Secure, SameSite)
    Browser-->>User: Signed in

    Browser->>API: Protected request + session cookie
    API->>Session: Resolve session
    Session-->>API: Authenticated principal
    API->>API: Check CSRF token for state-changing request
    API->>DB: Authorize owner-scoped operation
    DB-->>API: Authorized result
    API-->>Browser: Response

    User->>Browser: Sign out
    Browser->>API: POST sign-out + session cookie + CSRF token
    API->>Session: Invalidate session
    API-->>Browser: Expire session cookie
\`\`\`

**Lifecycle requirements:** regenerate the session identifier after authentication; enforce idle and absolute expiration; invalidate sessions on logout and relevant account-security events; never accept a client-supplied user ID as the authenticated identity. The concrete session store and timeout values remain implementation decisions.

## 3. Multi-user security and data isolation

\`\`\`mermaid
flowchart TB
    UA["User A"] --> API["Backend API"]
    UB["User B"] --> API
    API --> AUTH["Resolve authenticated server-side session"]
    AUTH --> CTX["Build trusted user context"]
    CTX --> POLICY["Authorize requested action"]
    POLICY --> SVC["Business service"]
    SVC --> REPO["Repository / data access"]
    REPO --> DB[("PostgreSQL 17")]
    DB --> FILTER["Owner-scoped records"]
    FILTER --> SVC
    SVC --> RESP["Response limited to caller's authorized data"]
\`\`\`

**Security requirements:**
- Every read, update, and delete must enforce ownership on the server.
- Changing a record ID, URL, filename, or request payload must not grant access to another user's data.
- File download and deletion must use the same authorization policy as database records.
- Add automated cross-user/IDOR tests before declaring multi-user support ready.
- Cookie-authenticated state-changing requests must pass CSRF validation; credentialed CORS must allow only explicitly trusted origins.

## 4. File upload, validation, and quarantine lifecycle

~~~mermaid
flowchart TD
    B["Browser upload"] --> API["Backend: session + CSRF + owner authorization"]
    API --> LIMIT["Ingress and size limits"]
    LIMIT --> Q["Private quarantine storage"]
    Q --> VALID["Extension, signature, structure, size checks"]
    VALID -->|"Valid"| SCAN["Malware scanner"]
    VALID -->|"Invalid"| REJECT["Reject; inaccessible; cleanup"]
    SCAN -->|"Clean"| PROMOTE["Promote to private final key"]
    SCAN -->|"Malicious / error / timeout"| REJECT
    PROMOTE --> META["Verify checksum and mark AVAILABLE in PostgreSQL"]
    META --> READY["Authorized download through backend"]
~~~

Only AVAILABLE assets are downloadable. Scanner failures remain quarantined; hosted production must fail closed. See [File Storage Architecture](storage/FILE_STORAGE_ARCHITECTURE.md).

## 5. Database and local development topology

~~~mermaid
flowchart TB
    DEV["Developer workstation"]
    COMPOSE["Docker Compose — local profile"]
    PG[("PostgreSQL 17 container")]
    VOL[("Named persistent volume")]
    APP["Spring Boot application"]
    FLYWAY["Flyway migration step"]
    DEV --> COMPOSE
    COMPOSE --> PG
    PG --> VOL
    DEV --> APP
    APP -->|"JDBC / localhost"| PG
    FLYWAY -->|"Versioned SQL migrations"| PG
~~~

Compose is a local convenience; native PostgreSQL can be used instead. Bind the database port to loopback by default, keep credentials outside version control, use a named volume, and run migrations as a controlled step. This diagram does not define production deployment topology. See [Database Architecture](database/DATABASE_ARCHITECTURE.md) for the conceptual ERD, isolation rules, Flyway lifecycle, and backup/restore plan.

## 6. Deployment architecture

\`\`\`mermaid
flowchart TB
    CODE["Same CareerPilot AI codebase<br/>React frontend + Spring Boot backend"]
    CODE --> CONFIG{"Deployment profile"}
    CONFIG --> LOCAL["Local"]
    CONFIG --> SELF["Self-hosted"]
    CONFIG --> CLOUD["Cloud"]

    subgraph L["Local environment"]
      LFE["Local frontend"]
      LBE["Local backend"]
      LDB[("Local PostgreSQL")]
      LFS[("Local disk")]
      LAI["Ollama or cloud AI"]
      LFE --> LBE
      LBE --> LDB
      LBE --> LFS
      LBE --> LAI
    end
    subgraph S["Self-hosted environment"]
      SFE["Reverse proxy / frontend"]
      SBE["Self-hosted backend"]
      SDB[("Server PostgreSQL")]
      SFS["Server disk or object storage"]
      SAI["Ollama or cloud AI"]
      SFE --> SBE
      SBE --> SDB
      SBE --> SFS
      SBE --> SAI
    end
    subgraph C["Cloud environment"]
      CFE["Static frontend hosting / CDN"]
      CBE["Hosted backend"]
      CDB[("Managed PostgreSQL")]
      CFS[("Cloud object storage")]
      CAI["Configured cloud AI"]
      CFE --> CBE
      CBE --> CDB
      CBE --> CFS
      CBE --> CAI
    end
    LOCAL --> LFE
    SELF --> SFE
    CLOUD --> CFE
\`\`\`

**Deployment notes:**
- Local use does not automatically make the application internet-accessible.
- Remote deployments require HTTPS, appropriate firewall rules, and a secured reverse proxy or gateway.
- A free-tier cloud service is an optional starting point, not a guarantee of permanently free or production-grade hosting.
- Ollama is suitable only where the backend can securely reach the Ollama host and sufficient compute is available.
- Cookie settings and trusted frontend origins must be configured per deployment; do not use wildcard CORS with credentials.

## 7. AI provider routing, privacy, and review

~~~mermaid
flowchart TB
  B[Browser] --> API[Spring Boot API]
  API --> AUTH[Session, CSRF, owner authorization]
  AUTH --> FEATURE[CareerPilot feature service]
  FEATURE --> MIN[Data minimization and consent policy]
  MIN --> PORT[Provider-neutral AI interface]
  PORT --> CFG{Server-side provider config}
  CFG -->|Local enabled| OLLAMA[Private Ollama endpoint]
  CFG -->|Cloud explicitly enabled + consent| CLOUD[Approved cloud AI API]
  OLLAMA --> VALID[Response validation and normalization]
  CLOUD --> VALID
  VALID --> DRAFT[Save/display as draft]
  DRAFT --> REVIEW[User reviews and approves]
~~~

**Privacy invariant:** no silent local-to-cloud fallback. The backend selects the provider; browser input cannot supply arbitrary provider, model, endpoint, or credentials. Cloud processing of personal/document data requires prior disclosure and affirmative consent. Provider outages do not change the data-disclosure route automatically. See [AI Provider Architecture](ai/AI_PROVIDER_ARCHITECTURE.md).

## 8. Example product workflow

\`\`\`mermaid
flowchart TD
    START["User signs in"] --> PROFILE["Create / update career profile"]
    PROFILE --> JOB["Find or add a job"]
    JOB --> MATCH["Analyze job description"]
    MATCH --> RESUME["Compare approved resume"]
    RESUME --> SUGGEST["AI suggests improvements"]
    SUGGEST --> REVIEW{"User reviews and approves?"}
    REVIEW -->|"No — revise"| SUGGEST
    REVIEW -->|"Yes"| DRAFT["Create tailored resume / application draft"]
    DRAFT --> TRACK["Save in application tracker"]
    TRACK --> PREP["Prepare for interview"]
    PREP --> PROGRESS["Track application progress"]
\`\`\`

This is a proposed product flow, not a claim that these features already exist. AI-generated material remains a draft until reviewed by the user. Sending an email or submitting an application is a separate consequential action and requires explicit user approval.

## 9. Key design invariants

1. One codebase supports local, self-hosted, and cloud deployments.
2. PostgreSQL 17 is the common relational database baseline.
3. Flyway owns versioned schema changes for application tables.
4. Docker Compose is the local database convenience, not a production requirement.
5. Document bytes are private; metadata lives in PostgreSQL, and files are unavailable until validation and scanning succeed.
6. Storage access always passes through backend authentication and tenant authorization.
3. Browser authentication uses backend-managed sessions with a Secure/HttpOnly cookie; CSRF protection remains enabled.
4. Storage and AI provider selection happen through backend adapters and configuration.
5. AI provider selection is server-side; cloud processing requires disclosure and consent, and provider failure never triggers silent cloud fallback.
5. Secrets never ship in the frontend bundle or public repository.
6. All private data access is authorized server-side for the authenticated user.
7. Diagrams describe target design; implementation status is tracked separately in the roadmap.
8. The architecture review gate must be completed before the next application-code milestone starts.
