# CareerPilot AI — Product Roadmap

**Status:** Planning baseline; target architecture decisions documented and awaiting review gate  
**Last updated:** 2026-11-07  
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

The detailed target architecture and diagram are in [ARCHITECTURE.md](ARCHITECTURE.md).

| Decision | Approved direction |
|---|---|
| Deployment | Support local, self-hosted, and cloud modes from the same codebase |
| Document storage | Hybrid: local filesystem adapter and cloud/object-storage adapter |
| AI provider | Switchable: local Ollama and a configurable cloud AI provider |
| Users | Multi-user with strict server-side data isolation |
| Database | PostgreSQL as the common relational database baseline |

Specific hosting vendors, exact authentication model, migration tool, file policies, and production operational controls remain subject to review. Free cloud tiers are optional and may have quotas, sleeping, suspension, or changing terms; the architecture must not depend on any one free tier.

## 4. Proposed MVP capabilities

1. **User account and profile**
   - Account lifecycle and secure sign-in.
   - Store career history, skills, preferred roles, locations, and job-search constraints.
   - Let users review, edit, export, and delete their stored profile information.

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

## 6. Delivery roadmap and gates

| Milestone | Outcome | Exit criteria / gate |
|---|---|---|
| 0 — Product and architecture | Scope, architecture, deployment modes, storage and AI strategy documented | Architecture reviewed; unresolved authentication, database, file, AI-data, and operational decisions resolved before implementation |
| 1 — Application foundation | Backend/frontend startup, configuration, health checks, basic CI | **Completed:** initial Java/Spring Boot and React/Vite foundation merged in PR #7; clean-checkout build/run instructions maintained |
| 2 — Persistence foundation | PostgreSQL integration, schema migrations, local database setup, repository tests | Migration from empty database succeeds; integration tests pass; local setup works with Docker Compose or documented native PostgreSQL |
| 3 — Multi-user identity and isolation | Registration/sign-in, account lifecycle, authorization, user ownership | Automated tests prove cross-user access is denied; secrets/passwords are safely handled |
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
| Cross-user data exposure | Enforce ownership in backend authorization and test IDOR/cross-tenant attempts |
| Incorrect or expired job listings | Store source URL and observed date; re-check before presenting as active |
| Fabricated resume claims | Ground suggestions in approved profile facts; require review |
| Accidental application submission | Explicit confirmation at the final action; maintain an audit record |
| Exposure of personal data | Data minimization, access controls, encryption, safe logging, and retention controls |
| External provider outage or cost | Provider adapters, quotas/timeouts, configurable selection, and local AI option |
| Loss of uploaded documents | Defined backup/restore, retention, and deletion procedures |
| Free-tier hosting changes | Keep providers replaceable; do not treat free-tier availability as a production guarantee |
| Overly broad first release | Enforce MVP exclusions and milestone exit criteria |

## 9. Current review gate — no implementation yet

The high-level choices for deployment, storage, and AI provider are approved and recorded. Before starting the next application-code milestone, review [ARCHITECTURE.md](ARCHITECTURE.md) and explicitly resolve:

- Authentication model and account recovery/deletion behavior.
- PostgreSQL version, migration tool, and local startup method.
- File type/size limits, retention, backup, and malware-scanning approach.
- Initial AI adapter contract and disclosure/consent behavior for external processing.
- Secrets management, HTTPS, logging, monitoring, and restore requirements.
- The first deployment mode used for end-to-end validation.

**No application implementation should begin until this review gate is completed.** Documentation changes do not count as implementation.

## 10. Change control

Treat this roadmap as a living proposal. Record material scope or architecture changes in pull requests, explain the reason, and update the relevant documentation alongside implementation.
