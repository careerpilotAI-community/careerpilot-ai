# CareerPilot AI — Proposed Architecture

**Status:** Initial architecture proposal; decisions marked provisional require review.  
**Last updated:** 2026-11-06

## 1. Architecture goals

- Keep the first release small, testable, and affordable to run.
- Separate product workflows from external job-board, email, document, and AI providers.
- Protect personal career data and keep users in control of consequential actions.
- Make generated content traceable to its inputs and easy to review.
- Allow components to evolve without committing prematurely to infrastructure.

## 2. Proposed high-level shape

The README mentions Java/backend components and a Vite/React frontend as possible future technologies. The following is a proposal, not a record of already implemented code.

```text
Browser
  |
  v
Vite + React UI (proposed)
  |
  | HTTPS / JSON API
  v
Java + Spring Boot API (proposed)
  |
  +-- Identity & User Profile
  +-- Job Discovery & Tracking
  +-- Resume & Document Assistance
  +-- Application Preparation
  +-- Interview Preparation
  |
  +-- Relational Database (choice pending)
  +-- Document Storage (choice pending)
  +-- AI Provider Adapter (provider/model pending)
  +-- Job Source Adapters (sources pending)
  +-- Email / Portal Adapters (future; approval-gated)
```

Keep provider-specific code behind interfaces so that a provider can be replaced without rewriting core business workflows.

## 3. Proposed technology direction

| Concern | Proposal | Status |
|---|---|---|
| Frontend | React with Vite and TypeScript | Provisional |
| Backend | Java with Spring Boot and REST APIs | Provisional |
| Persistence | Relational database; PostgreSQL is a candidate | Not decided |
| Authentication | Standards-based session or token approach with secure refresh/revocation | Not decided |
| AI | Provider-neutral adapter; evaluate hosted and local options | Not decided |
| Documents | Private object/file storage with access and retention controls | Not decided |
| Build and CI | Maven/Gradle for backend and npm-compatible tooling for frontend, according to final stack | Not configured |
| Deployment | Select after cost, data-residency, and operational requirements are known | Not decided |

Do not add dependency-update configuration for an ecosystem until its manifest or workflow exists in the correct directory. Configure Dependabot directories to match the actual repository layout when application manifests are introduced.

## 4. Backend module boundaries

Start as a modular monolith. Avoid microservices until independent scaling, deployment, or ownership needs are demonstrated.

### Identity and profile
Owns user identity references, preferences, career history, skills, and consent settings. Do not duplicate profile facts across modules.

### Job discovery and tracking
Owns normalized job records, source metadata, observed timestamps, duplicate detection, and application status history. External source adapters must respect provider terms and rate limits.

### Resume and document assistance
Owns document metadata, version relationships, extraction status, and job-specific tailoring suggestions. Preserve the original upload; store generated versions separately.

### Application preparation
Owns cover-letter and recruiter-message drafts, review status, and approval records. Sending or submitting is a separate integration action and must require explicit user confirmation.

### Interview preparation
Owns practice sets, question provenance, user notes, and session history.

### AI orchestration
Owns prompt templates, model/provider selection, input minimization, output validation, and provenance metadata. It should not own business records or bypass module authorization.

## 5. Data model — conceptual starting point

Candidate entities (not final schema):

- `User`: account identity and preferences.
- `CareerProfile`: user-approved experience, education, skills, and role preferences.
- `ResumeDocument`: original document metadata, storage reference, and version.
- `JobListing`: normalized job details, source URL, observed date, and verification state.
- `Application`: relationship between a user and a job, including current status.
- `ApplicationEvent`: append-only status and action history.
- `GeneratedDraft`: draft type, source references, model metadata, and review state.
- `ApprovalRecord`: user, action, target, timestamp, and approval outcome.
- `InterviewPractice`: question set, source job, notes, and session metadata.

Store only necessary data. Define retention and deletion behavior before production use. Do not store raw passwords, access tokens, or provider secrets in these business tables.

## 6. API conventions

- Use versioned REST endpoints (for example, `/api/v1`).
- Validate request bodies and return consistent error responses.
- Enforce authorization on every resource access; do not rely on UI hiding.
- Use pagination and filtering for job and application lists.
- Make retried write operations safe where practical through idempotency keys.
- Keep external provider errors separate from internal validation errors.
- Never expose stack traces, secrets, or internal provider credentials to clients.

Illustrative resource groups (not implemented endpoints):

- `/api/v1/profile`
- `/api/v1/jobs`
- `/api/v1/applications`
- `/api/v1/resumes`
- `/api/v1/drafts`
- `/api/v1/interview-practice`

## 7. Security and privacy baseline

1. Use HTTPS in deployed environments and secure cookie/token handling.
2. Apply least-privilege authorization and isolate each user's records.
3. Store secrets in environment-specific secret management, never in Git.
4. Encrypt sensitive data in transit and at rest using platform-supported controls.
5. Exclude resume contents, tokens, email bodies, and personal identifiers from routine logs.
6. Validate uploaded file type, size, and content; scan files where supported.
7. Protect against prompt injection in job descriptions and uploaded documents: treat imported content as untrusted data, not instructions.
8. Require explicit user confirmation before sending an email, submitting an application, or sharing a document externally.
9. Keep an auditable record of approvals and consequential external actions without logging sensitive payloads.
10. Define data export, deletion, retention, and incident-response procedures before public launch.

## 8. External integration boundaries

Define interfaces for job sources, AI models, email delivery, and document storage. Each adapter should implement timeouts, bounded retries, rate-limit handling, observability, and safe failure behavior.

Never assume that a public job listing grants permission to automate account access or application submission. Confirm provider terms and user authorization before enabling an integration.

## 9. Testing and quality gates

- Unit tests for business rules, validation, and status transitions.
- Integration tests for persistence and API authorization.
- Contract tests for external provider adapters.
- Frontend component and workflow tests for critical user journeys.
- Security checks for dependency vulnerabilities, secret exposure, and access-control regressions.
- CI must build and test the actual manifests present in the repository; do not add placeholder workflows that imply an application exists.

## 10. Open architecture decisions

Before Phase 1 implementation, decide and document:

- Single-user prototype or multi-user service?
- Hosting provider, region, budget, and data-residency constraints?
- PostgreSQL or another database?
- Authentication/session strategy?
- Hosted AI, local AI, or configurable provider support?
- Resume file formats, maximum sizes, storage, retention, and deletion?
- Which job sources are permitted and in scope?
- Is outbound email in the first release, and what confirmation flow is required?

## 11. Architecture change process

Keep this document aligned with the code. When a decision becomes final, replace the provisional status with the chosen option and record material trade-offs. Architecture changes should be reviewed with the affected module owners and accompanied by tests and migration notes where applicable.
