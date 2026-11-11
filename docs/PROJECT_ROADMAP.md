# CareerPilot AI — Product Roadmap

**Status:** Planning baseline; target architecture decisions documented and remaining review gate in progress  
**Last updated:** 2026-11-12  
**Scope:** Product direction and delivery phases; listed capabilities are plans unless explicitly marked complete.

## 1. Product vision

CareerPilot AI helps job seekers manage a structured, user-controlled career-development workflow: discover relevant opportunities, prepare truthful application materials, practice for interviews, and track progress.

The product assists with decisions rather than making consequential career decisions invisibly on a user's behalf.

## 2. Product principles

- **User control:** Users review and approve applications, messages, profile changes, and submissions.
- **Truthful representation:** Resume tailoring must not invent experience, skills, qualifications, or achievements.
- **Traceability:** Show the source and date for job listings and distinguish verified facts from generated suggestions.
- **Privacy by design:** Collect the minimum data needed; protect resumes, contact details, credentials, and application history.
- **Reliability:** Prefer explainable workflows, validation, and recoverable failures over opaque automation.
- **Portability:** One codebase should support local, self-hosted, and cloud deployments.
- **Incremental delivery:** Build and validate a small useful workflow before adding broad automation.

## 3. Approved architecture direction

The detailed target architecture and diagrams are in [ARCHITECTURE.md](ARCHITECTURE.md). The database model and operations are in [Database Architecture](architecture/database/DATABASE_ARCHITECTURE.md). The upload, storage adapter, validation, quarantine, and recovery design is in [File Storage Architecture](architecture/storage/FILE_STORAGE_ARCHITECTURE.md). The provider, privacy, consent, and cost design is in [AI Provider Architecture](architecture/ai/AI_PROVIDER_ARCHITECTURE.md).

| Decision | Approved direction |
|---|---|
| Deployment | Support local, self-hosted, and cloud modes from the same codebase |
| Document storage | Hybrid: private local filesystem adapter and private cloud/object-storage adapter |
| File safety | Backend-authorized upload/download; validation and quarantine; malware scan before availability; opaque storage keys |
| AI provider | Switchable: local Ollama and a configurable cloud AI provider; cloud use requires explicit disclosure/consent and server-side enablement |
| Users | Multi-user with strict server-side data isolation |
| Database | PostgreSQL 17 across deployment modes |
| Schema migrations | Flyway for versioned SQL migrations |
| Local database setup | Docker Compose; native PostgreSQL remains an alternative |
| Browser authentication | Server-side sessions, identified to the browser by a secure HttpOnly cookie; CSRF protection required |
| Mobile support | Deferred; revisit authentication design when a mobile client is prioritized |

Specific hosting vendors, Flyway/JDBC dependency compatibility, file policies, session repository/scaling design, password-hashing configuration, and production operational controls remain subject to review. PostgreSQL 17 remains supported through 8 November 2029 under the community versioning policy; keep its minor release current and review the major-version choice ahead of that date. Free cloud tiers are optional and may have quotas, sleeping, suspension, or changing terms; the architecture must not depend on any one free tier.

The selected session cookie is not a JWT. JWT access/refresh tokens are not part of the initial browser MVP. The session design requires CSRF protection because browsers automatically attach cookies to eligible requests.

## 4. Proposed MVP capabilities

1. **User account and profile**
   - Account lifecycle and secure sign-in using backend-managed sessions for browser clients.
   - Store career history, skills, preferred roles, locations, and job-search constraints.
   - Let users review, edit, export, and delete their stored profile information.
   - Protect state-changing cookie-authenticated requests with CSRF tokens.
2. **Job discovery and tracking**
   - Capture a listing's title, company, location, description, source URL, and observed date.
   - Track statuses such as Saved, Preparing, Applied, Interviewing, Rejected, and Closed.
   - Detect likely duplicates and flag stale listings for re-verification.
3. **Resume assistance**
   - Compare a selected resume with a job description.
   - Suggest role-relevant wording while preserving factual accuracy.
   - Keep the original resume unchanged and create separately named tailored versions.
   - Require user review before a tailored resume is exported or used.
4. **Application preparation**
   - Draft cover letters and recruiter messages from approved profile facts and a selected job.
   - Require explicit user approval before sending or submitting anything.
5. **Interview preparation**
   - Generate role-specific practice questions from the job description and the user's actual experience.
   - Let users record notes and practice responses.

## 5. Explicitly out of MVP

- Autonomous submission to job portals or sending emails without per-action approval.
- Paid job-board integrations or scraping that violates a provider's terms.
- Claims of guaranteed job matching, ATS scores, or employment outcomes.
- Multi-agent orchestration, complex analytics, and mobile applications before the core workflow is validated.
- Mobile-native authentication flows; these require a separate design and security review if mobile support is approved later.

## 6. Delivery roadmap and gates

| Milestone | Outcome | Exit criteria / gate |
|---|---|---|
| 0 — Product and architecture | Scope, architecture, deployment modes, storage, AI, and browser authentication documented | Architecture reviewed; remaining database, file, AI-data, session operations, account lifecycle, and deployment decisions resolved before implementation |
| 0.5 — File storage design | Storage interface, local/object adapters, file safety, malware scanning, retention, and recovery documented | File Storage Architecture reviewed; file types/limits, quotas, scanner policy, metadata lifecycle, and recovery controls approved before storage implementation |
| 0.6 — AI provider design | Provider-neutral contract, local/cloud routing, privacy, consent, prompt safety, budgets, and resilience documented | AI Provider Architecture reviewed; cloud provider terms, local model requirements, consent UX, model allowlist, budgets, and embedding scope approved before AI implementation |
| 1 — Application foundation | Backend/frontend startup, configuration, health checks, basic CI | **Completed:** initial Java/Spring Boot and React/Vite foundation merged in PR #7; clean-checkout build/run instructions maintained |
| 2 — Persistence foundation | PostgreSQL 17 integration, Flyway schema migrations, local database setup, repository tests | Migration from an empty database succeeds; migration validation and integration tests pass; local setup works with Docker Compose or documented native PostgreSQL |
| 3 — Multi-user identity and isolation | Registration/sign-in, server-side session lifecycle, authorization, user ownership | Secure HttpOnly session cookie and CSRF protections configured; session expiration/logout tested; automated tests prove cross-user access is denied; credentials safely handled |
| 4 — Profile and job tracker | User profile and job records with status tracking | CRUD flows validated; validation, ownership checks, duplicate handling, and tests in place |
| 5 — Document storage | Storage interface plus local and object-storage adapters | Upload/download/delete authorization tests pass; size/type rules and backup/retention policy documented |
| 6 — AI provider integration | Common AI interface with Ollama and one selected cloud provider adapter | Provider can be changed by configuration; failure handling and user data disclosure are tested |
| 7 — Resume and application assistant | Job-to-resume comparison and draft generation | Original files preserved; generated content traceable to approved facts; user approval required |
| 8 — Interview preparation | Practice sets and response notes | User can create, revisit, and evaluate practice sessions |
| 9 — Deployment and hardening | Local, self-hosted, and cloud deployment guides; security, accessibility, reliability | Deployment validation, security review, backup/restore test, and release checklist completed |

Milestones are planning gates, not delivery-date commitments. Architecture support for all three deployment modes does not require launching all modes simultaneously. Validate local operation first, then self-hosted and cloud deployment as resources and readiness permit.

## 7. Initial success measures

Instrument only what is needed and avoid collecting sensitive content for analytics.

- **Activation:** Percentage of users who complete a profile and save their first job.
- **Workflow completion:** Percentage who move from a saved job to a reviewed application draft.
- **Usefulness:** User-rated relevance of job matches and usefulness of resume suggestions.
- **Trust:** Unsupported claims in generated application content; target is zero.
- **Reliability:** Build success, API error rate, and recoverable workflow failure rate.
- **Privacy:** No secrets or sensitive resume/application data in application logs.

Set numeric targets only after baseline usage exists.

## 8. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Cross-user data exposure | Enforce ownership in backend authorization, test IDOR/cross-tenant attempts, and use owner-aware database constraints |
| CSRF or unsafe cookie configuration | Keep CSRF protection enabled; configure cookie flags and credentialed CORS explicitly; test state-changing requests |
| Session theft or fixation | HttpOnly/Secure cookie settings, session ID regeneration, expiration, logout invalidation, HTTPS, and safe logging |
| Incorrect or expired job listings | Store source URL and observed date; re-check before presenting as active |
| Fabricated resume claims | Ground suggestions in approved profile facts; require review |
| Accidental application submission | Explicit confirmation at the final action; maintain an audit record |
| Exposure of personal data | Data minimization, access controls, encryption, safe logging, and retention controls |
| External provider outage or cost | Provider adapters, quotas/timeouts, configurable selection, and local AI option |
| Loss of uploaded documents | Defined database and document backup/restore, retention, and deletion procedures; test recovery together |
| Migration drift or unsafe schema changes | Immutable Flyway migrations, validation, review, and pre-deployment recovery procedures |
| Free-tier hosting changes | Keep providers replaceable; do not treat free-tier availability as a production guarantee |
| Overly broad first release | Enforce MVP exclusions and milestone exit criteria |

## 9. Current review gate — no implementation yet

The high-level choices for deployment, storage, AI provider, multi-user tenancy, PostgreSQL 17, Flyway, Docker Compose for local development, browser session authentication, and deferral of mobile support are documented. The file storage design is in [File Storage Architecture](architecture/storage/FILE_STORAGE_ARCHITECTURE.md). Before starting application-code milestones, review [ARCHITECTURE.md](ARCHITECTURE.md) and explicitly resolve:

- Initial entity scope, tenant ownership constraints, indexes, Flyway/JDBC/Spring Boot dependency compatibility, and migration-job ownership.
- PostgreSQL backup retention, encryption, off-site storage, point-in-time recovery, and tested recovery-point/recovery-time objectives.
- File type/size limits, per-user and deployment quotas, scanner selection/fail-closed behavior, metadata lifecycle, retention, backup, and restore strategy.
- Initial AI adapter contract, cloud provider terms, disclosure/consent behavior, prompt-injection defenses, local model requirements, and usage/cost budgets.
- Session repository/scaling strategy, cookie settings by deployment profile, and concrete password-hashing configuration.
- Registration verification, account recovery, password reset, account deletion, and data-export behavior.
- Secrets management, HTTPS, trusted CORS origins, logging, monitoring, and restore requirements.
- The first deployment mode used for end-to-end validation.

**No application implementation should begin until this review gate is completed.** Documentation changes do not count as implementation.

## 10. Change control

Treat this roadmap as a living proposal. Record material scope or architecture changes in pull requests, explain the reason, and update the relevant documentation alongside implementation.
