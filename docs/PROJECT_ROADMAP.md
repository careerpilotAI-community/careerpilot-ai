# CareerPilot AI — Product Roadmap

**Status:** Proposed planning baseline  
**Last updated:** 2026-11-06  
**Scope:** Product direction and delivery phases; not a promise that listed features already exist.

## 1. Product vision

CareerPilot AI is intended to help job seekers manage a structured, user-controlled career-development workflow: discover relevant opportunities, prepare truthful application materials, practice for interviews, and track progress.

The product should assist with decisions rather than make consequential career decisions invisibly on a user's behalf.

## 2. Product principles

- **User control:** Users review and approve applications, messages, profile changes, and submissions.
- **Truthful representation:** Resume tailoring must not invent experience, skills, qualifications, or achievements.
- **Traceability:** Show the source and date for job listings and distinguish verified facts from generated suggestions.
- **Privacy by design:** Collect the minimum data needed; protect resumes, contact details, credentials, and application history.
- **Reliability:** Prefer explainable workflows, validation, and recoverable failures over opaque automation.
- **Incremental delivery:** Build and validate a small useful workflow before adding broad automation.

## 3. Proposed MVP

The first usable release should focus on a coherent workflow rather than every long-term idea.

### MVP capabilities

1. **User profile**
   - Store a user's career history, skills, preferred roles, locations, and job-search constraints.
   - Let users review and edit all stored profile information.

2. **Job discovery and tracking**
   - Capture a job listing's title, company, location, description, source URL, and observed date.
   - Support status tracking such as Saved, Preparing, Applied, Interviewing, Rejected, and Closed.
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

### Explicitly out of MVP

- Autonomous submission to job portals or sending emails without a per-action approval.
- Paid job-board integrations or scraping that violates a provider's terms.
- Claims of guaranteed job matching, ATS scores, or employment outcomes.
- Multi-agent orchestration, complex analytics, and mobile applications before the core workflow is validated.

## 4. Delivery phases

| Phase | Outcome | Exit criteria |
|---|---|---|
| 0 — Product and technical foundation | Confirm scope, architecture, data model, privacy, and development conventions | Decisions documented; risks and open questions reviewed |
| 1 — Application foundation | Repository structure, backend/frontend startup, configuration, health checks, basic CI | A clean checkout can build and run locally using documented steps |
| 2 — Profile and job tracker | Editable profile and job records with status tracking | CRUD flows validated; input validation and tests in place |
| 3 — Resume and application assistant | Job-to-resume comparison and draft generation | Original files preserved; generated content traceable to approved facts |
| 4 — Interview preparation | Practice sets and response notes | User can create, revisit, and evaluate practice sessions |
| 5 — Hardening and release | Security, accessibility, reliability, deployment, and user documentation | Security review and release checklist completed |

Phases are sequential planning gates, not delivery-date commitments. Reprioritize based on user feedback and implementation evidence.

## 5. Initial success measures

Instrument only what is needed and avoid collecting sensitive content for analytics.

- **Activation:** Percentage of users who complete a profile and save their first job.
- **Workflow completion:** Percentage who move from a saved job to a reviewed application draft.
- **Usefulness:** User-rated relevance of job matches and usefulness of resume suggestions.
- **Trust:** Number of factual corrections users make to generated application content; target is to reduce unsupported claims to zero.
- **Reliability:** Build success, API error rate, and recoverable workflow failure rate.
- **Privacy:** No secrets or sensitive resume/application data in application logs.

Targets should be set after baseline usage exists; do not invent targets before measurement.

## 6. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Incorrect or expired job listings | Store source URL and observed date; re-check before presenting as active |
| Fabricated resume claims | Ground suggestions in approved profile facts; require review |
| Accidental application submission | Explicit confirmation at the final action; maintain an audit record |
| Exposure of personal data | Data minimization, access controls, encryption, safe logging, and retention controls |
| Fragile external integrations | Isolate providers behind adapters; handle rate limits and outages |
| Overly broad first release | Enforce MVP exclusions and phase exit criteria |

## 7. Decisions still required

The following must be confirmed before implementation: target deployment environment and budget; authentication approach; database choice; AI provider/local-model strategy; document storage and retention; supported job sources; and whether the first release is single-user or multi-user.

## 8. Change control

Treat this roadmap as a living proposal. Record material scope or architecture changes in pull requests, explain the reason, and update the relevant documentation alongside implementation.
