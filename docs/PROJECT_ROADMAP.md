# CareerPilot AI — Product Roadmap

**Status:** MVP scope approved; Branch 23 build-quality baseline merged in PR #30; Branch 24 reconciles post-merge readiness; product-feature implementation remains gated  
**Last updated:** 2026-11-12  
**Scope:** Product direction and delivery phases; capabilities are plans unless explicitly marked complete.

## 1. Product vision

CareerPilot AI helps job seekers manage a structured, user-controlled career workflow: maintain career information, organize opportunities, prepare truthful application materials, and improve interview readiness. AI assists users; it does not make consequential career decisions invisibly.

## 2. Product principles

- **User control:** Users review and approve applications, messages, profile changes, and submissions.
- **Truthful representation:** Resume tailoring must not invent experience, skills, qualifications, or achievements.
- **Traceability:** Preserve job source URLs and observed dates; distinguish verified facts from generated suggestions.
- **Privacy by design:** Minimize collected data and protect resumes, contact details, and application history.
- **Reliability:** Prefer validated, recoverable workflows over opaque automation.
- **Portability:** One codebase is intended to support local, self-hosted, and cloud deployments.
- **Incremental delivery:** Validate a small useful workflow before adding broad automation.

## 3. Approved architecture direction

See [ARCHITECTURE.md](ARCHITECTURE.md) and the specialist architecture documents.

| Area | Direction |
|---|---|
| Deployment | Local-first initial validation; same codebase intended for local, self-hosted, and cloud |
| Frontend | React 19, Vite, TypeScript (exact manifest versions verified in Branch 22 audit) |
| Backend | Java 21, Spring Boot 3.5.11, Maven (exact manifest versions verified in Branch 22 audit) |
| Database | PostgreSQL 17 |
| Migrations | Flyway |
| Local database | Docker Compose; native PostgreSQL may remain an alternative |
| Tenancy | Multi-user with strict server-side owner-scoped authorization |
| Browser authentication | Server-side sessions with Secure/HttpOnly cookie policy and CSRF protection; mobile deferred |
| Document storage | Local filesystem first, behind an adapter boundary for future private object storage |
| AI | Local Ollama first; configurable cloud provider later, only with disclosure and affirmative consent |
| Career actions | AI output remains a draft; user approval required for consequential actions |

No silent local-to-cloud AI fallback is permitted. The local-first target is not a claim of production security, backup readiness, or public-launch readiness.

## 4. Approved MVP scope — Branch 17

The recommended MVP (Option A) is approved for product planning:

1. **User accounts and profile:** registration, login, logout, and editable career profile.
2. **Job application tracker:** manually create, view, edit, delete, and track applications.
3. **Resume management:** upload and manage multiple resume versions while preserving the original.
4. **Job management:** save job descriptions and associated company/job details.
5. **Application dashboard:** summarize application counts, statuses, and upcoming interview dates.
6. **AI assistance:** local Ollama-based job matching and resume suggestions, with user review and factual grounding.

### Explicitly outside MVP

- Automatic job scraping, crawling, or discovery.
- Automatic job-portal applications or submissions.
- Automatic recruiter emails, messages, or outreach.
- Advanced analytics, forecasting, or multi-agent orchestration.
- Mobile application/client and mobile-specific authentication.
- Cloud AI as an implicit fallback; any future cloud use requires separate provider, privacy, consent, and cost approval.

Detailed requirements, user journeys, feature boundaries, and the conceptual domain model are in [MVP Product Requirements](product/MVP_PRODUCT_REQUIREMENTS.md). The proposed physical schema and persistence gates are in [Database Schema & Persistence Readiness](architecture/DATABASE_SCHEMA_PERSISTENCE_READINESS.md); decisions are recorded in [Persistence Decision Review](architecture/PERSISTENCE_DECISION_REVIEW.md). Identity/session security requirements are documented in [Identity, Session & Security Readiness](architecture/IDENTITY_SESSION_SECURITY_READINESS.md). The consolidated delivery sequence and gates are in [MVP Implementation Readiness](implementation/MVP_IMPLEMENTATION_READINESS.md). The repository baseline and build/quality findings are in [Branch 22 Build & Quality Baseline Audit](implementation/BUILD_QUALITY_BASELINE_AUDIT.md).

## 5. Delivery roadmap and gates

| Milestone | Outcome | Status / exit gate |
|---|---|---|
| 0 — Architecture direction | Target architecture and specialist designs | Documented; operational decisions remain open |
| 1 — Application foundation | Java/Spring Boot and React/Vite starter foundation | **Completed:** initial foundation merged in PR #7 |
| 2 — Product definition | MVP scope, requirements, boundaries, user journeys, conceptual domain model | **Scope approved; documentation prepared in Branch 17.** Merge/review does not authorize implementation |
| 3 — Database schema readiness | Physical relational design, multi-user isolation, migration policy, backup/restore and persistence acceptance criteria | **Merged in PR #25; design decisions consolidated in merged PR #26** |
| 4 — Persistence decision review | Resolve schema, ownership, application history, resume storage, retention, RLS and backup defaults | **Merged in PR #26; recommended persistence defaults recorded** |
| 5 — Identity and session security readiness | Account lifecycle, server-side sessions, CSRF/CORS, secrets, authorization and security tests | **Merged in PR #27; security design documented** |
| 6 — MVP implementation readiness | Consolidated delivery slices, verification checklist, quality gates and remaining decisions | **Merged in PR #28; implementation sequence documented** |
| 7 — Build & quality baseline audit | Verify actual repository manifests, starter code, tests, CI and reproducibility gaps | **Completed in Branch 22 audit; documentation only. No application code changed** |
| 8 — Build & quality baseline improvements | Deterministic builds, frontend lockfile, Maven Wrapper, CI quality checks and test conventions | **Completed and merged in PR #30 (merge commit `37f0f6d`); successful CI run recorded in PR #30. Branch protection/required-check enforcement remains unverified** |
| 8A — Post-merge readiness reconciliation | Align roadmap and readiness documents with merged repository state; preserve Branch 22 audit as historical evidence | **Branch 24: documentation-only reconciliation in progress** |
| 9 — Persistence implementation | PostgreSQL 17, Flyway, Docker Compose, repository integration tests | Not authorized; requires approved readiness gates and explicit implementation approval |
| 10 — Identity and isolation implementation | Account lifecycle, server-side sessions, CSRF, authorization, cross-user tests | Requires identity/security decisions and explicit implementation approval |
| 11 — Core tracker | Profile, job records, application status and dashboard | Requires persistence and identity gates |
| 12 — Resume/document management | Secure upload, versioning, storage adapter, scanning, retention and recovery | Requires file policies and storage security approval |
| 13 — Local AI assistance | Ollama integration, job matching and resume suggestions | Requires model/hardware baseline, grounding, privacy, safety and quota decisions |
| 14 — Local end-to-end validation | Integrated MVP workflows and recovery checks | Requires all preceding slices and explicit release criteria |
| 15 — Later deployment modes | Self-hosted and cloud deployment guides/validation | Separate operational, security, privacy and cost review |

Milestones are planning gates, not delivery-date commitments. No feature is complete merely because its requirements or architecture document is merged.

## 6. Success measures

Initially measure only what is necessary and avoid collecting sensitive document content for analytics:

- Activation: users who complete a profile and save their first job.
- Workflow completion: users who progress from a saved job to a reviewed application draft.
- Usefulness: user-rated relevance of job matches and resume suggestions.
- Trust: unsupported claims in generated application content; target zero.
- Reliability: build success, API error rate, and recoverable workflow failures.
- Privacy: no secrets or sensitive resume/application content in logs.

Set numeric targets after a baseline exists.

## 7. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Cross-user data exposure | Server-side owner checks, owner-aware data model, and cross-user authorization tests |
| Unsafe browser sessions/CSRF | Secure cookie profiles, CSRF protection, session lifecycle controls, and tests |
| Fabricated resume claims | Ground AI suggestions in user-approved facts; require review |
| Exposure of personal documents | File validation, private storage, authorization, quotas, retention and recovery controls |
| Local AI resource constraints | Document supported model/hardware baseline and fail clearly when unavailable |
| Accidental consequential action | No automated submission or outreach in MVP; explicit user confirmation for later actions |
| Scope expansion | Enforce MVP exclusions and milestone approval gates |

## 8. Current implementation gate

Branch 22 confirms that the current repository is still a starter foundation: the backend has only the application entry point and a context-load test; the frontend is a static starter screen; PostgreSQL, Flyway, Docker Compose, authentication, document storage and AI integration are not yet implemented. The CI workflow builds/verifies the backend and builds the frontend, but frontend dependency installation is not lockfile-deterministic and the repository ruleset/required-check configuration remains unverified.

The Branch 23 build/quality baseline is merged in PR #30 at `37f0f6d6054e49a81bb928eb14bb9920b1fb23f9`. PR #30 records a successful permanent CI run (`43164048115`) on the latest PR commit, covering backend Maven Wrapper `verify` and frontend `npm ci`, lint, formatting, tests, and production build. The commit-associated workflow-run lookup returned no runs, so the PR's recorded run is retained as evidence rather than independently revalidated here. Main branch protection and required-check enforcement remain unverified; do not assume CI is enforced by branch rules. Branch 22 remains the historical read-only audit of the pre-Branch-23 tree; its findings are not current-state claims where Branch 23 has since closed the gap. Branch 24 reconciles these documents only. This authorization does not extend to persistence or other product-feature coding. Before persistence coding, resolve the database schema and migration policy, backup/restore expectations, identity/session/CSRF/password controls, authorization tests, document upload/storage/scanning policy, Ollama model requirements, AI grounding and privacy controls, and milestone acceptance criteria.

Branch 23's build/quality baseline is complete and merged. Branch 24 is documentation-only. Any subsequent product-feature implementation branch, including persistence, still requires explicit authorization. Documentation-only approval is not coding authorization.

## 9. Change control

Record material scope or architecture changes in pull requests, explain the reason, and update this roadmap and affected specialist documents. Keep approved decisions distinct from recommendations and unresolved proposals.
