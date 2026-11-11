# CareerPilot AI — MVP Product Requirements

**Branch:** 17 — MVP Scope & Product Requirements  
**Status:** Product scope approved; requirements and model are documentation only  
**Date:** 2026-11-12  
**Initial validation target:** Local-first Windows development  
**Architecture baseline:** PostgreSQL 17 + Flyway + Docker Compose; browser server-side sessions; local filesystem storage; Ollama local AI

## 1. Purpose

Define a small, coherent MVP that helps an individual job seeker organize job opportunities and applications, maintain resume versions, and receive AI-assisted job-match and resume suggestions. The application is multi-user by design: each user's career data must remain private to that user.

This document is a product definition, not a technical implementation specification. The domain model is conceptual; field types, constraints, indexes, migrations, APIs, and UI details require later design and approval.

## 2. Product outcome and target user

**Primary user:** A job seeker managing multiple opportunities and application stages who needs one private workspace for career profile, job descriptions, resumes, and application progress.

**Core outcome:** The user can maintain a reliable personal job-search workspace, understand the state of each opportunity, and use local AI to generate reviewable suggestions grounded in their own information.

**MVP success path:** Create an account → complete a career profile → add a job → attach/select a resume → review a match and suggestions → track the application → see progress on the dashboard.

## 3. MVP feature requirements

### FR-01 — Account access

- A visitor can register an account using the approved account-registration policy.
- A registered user can log in and log out.
- The browser uses backend-managed sessions, not browser-stored JWT access/refresh tokens.
- Session cookies must follow the approved deployment profile; HTTPS deployments require Secure and HttpOnly, with an appropriate SameSite policy.
- State-changing browser requests must be protected against CSRF.
- Account recovery, email verification, password policy, hashing parameters, throttling, and administrator provisioning remain policy decisions to close before implementation.
- Account endpoints and user-owned records must enforce server-side authentication and authorization.

### FR-02 — Career profile

- A user can create, view, and update their own career profile.
- Profile information may include display name, professional summary, skills, experience, education, certifications, preferred role titles, preferred locations, work-mode preferences, and job-search constraints.
- Fields are optional unless a later validation decision marks them required.
- The user can review and correct profile facts used by AI assistance.
- No other user can read or change the profile.

### FR-03 — Job and company records

- A user can manually save a job opportunity.
- A job record can capture title, company name, location, employment type, work mode, description, source URL, source name, observed date, and optional posted/closing dates.
- A user can edit or remove their own saved job.
- The system should identify likely duplicates within the user's workspace using available source URL and descriptive attributes; duplicate policy must avoid silently merging distinct vacancies.
- The product must not imply a listing is currently open solely because it is saved. Source freshness/re-verification is a later enhancement.

### FR-04 — Application tracking

- A user can create an application record linked to a saved job.
- A user can update status, applied date, notes, next-action date, and interview date/time details.
- Proposed initial statuses: Saved, Preparing, Applied, Interviewing, Offer, Rejected, Withdrawn, Closed.
- Status changes must be explicit and user initiated in the MVP.
- A user can view and edit only their own application records.
- Deleting a job with a linked application must follow an explicit referential/deletion policy; do not silently orphan records.

### FR-05 — Resume management

- A user can upload and manage multiple resume documents/versions.
- Each resume record should have a user-visible label, original filename, upload timestamp, file size, detected media type, and optional notes/version label.
- The original resume must remain unchanged when a tailored version is created.
- Users can select a resume for a job-specific review.
- Download, replace, and delete operations must be authorized against the owning user.
- Upload limits, allowed formats, malware scanning, quarantine, quotas, encryption, retention, and restore policy are unresolved prerequisites for implementation.
- A resume is private personal data; it must never be exposed through a public URL or logged as document content.

### FR-06 — Application dashboard

- The dashboard shows counts of saved jobs and applications grouped by status.
- The dashboard shows upcoming interview dates from the user's own application records.
- Dashboard totals must derive from persisted records and respect the authenticated user's ownership boundary.
- Empty states should guide a first-time user to add a profile or job.
- Advanced charts, predictive insights, and benchmarking are outside MVP.

### FR-07 — Local AI assistance

- The MVP AI provider is local Ollama only.
- AI assistance provides job-to-profile/resume relevance observations and resume wording suggestions for a selected job.
- Suggestions must be grounded in user-provided profile/resume facts and the saved job description.
- The system must not invent skills, employers, dates, qualifications, responsibilities, metrics, or achievements.
- AI output is a draft; the user reviews and chooses whether to adopt it.
- AI assistance must not submit applications, send messages, or contact recruiters.
- If Ollama is unavailable or the configured model fails, show a clear failure and preserve user data. Do not silently send data to a cloud provider.
- Cloud AI, model/provider selection UI, embeddings/vector search, and automated job discovery are outside this MVP unless separately approved.
- Model name/version, hardware baseline, prompt safety, data handling, timeouts, quotas, and evaluation criteria must be agreed before implementation.

## 4. Non-functional requirements and invariants

### Security and isolation

1. Every user-owned entity is associated with an authenticated owner.
2. The server derives the owner from the authenticated session; it never trusts a client-supplied owner ID.
3. Read, list, create, update, delete, download, and nested-resource operations enforce ownership.
4. Cross-user tests for direct and indirect object access are release-blocking.
5. Secrets and personal document content must not appear in source control, client bundles, URLs, or logs.
6. Browser session security includes CSRF protection and safe session lifecycle handling.
7. AI calls must not create external data egress in local-only mode.

### Privacy and user control

- Collect only information needed for the stated workflow.
- Provide clear user-facing control over profile, job, application, and resume records.
- Data export/deletion semantics and backup-expiry behavior must be designed before public launch.
- No consequential action may be taken without an explicit user action and confirmation.

### Reliability and usability

- Persisted changes should provide clear success/failure feedback.
- Errors must not expose stack traces, secrets, filesystem paths, or private records.
- Upload and AI failures must not corrupt existing records or the original resume.
- Date/time handling should preserve the user's intended local date/time and a defined timezone policy.

### Portability

- Local-first is the first implementation and validation target.
- Storage and AI integration boundaries should avoid coupling product workflows to a single vendor.
- Self-hosted/cloud support is a later validation target, not an MVP launch claim.

## 5. Explicitly out of scope

- Automated scraping/crawling or scheduled discovery from job boards.
- Automated job-portal account creation, application submission, or form filling.
- Sending recruiter emails, LinkedIn messages, or other outreach.
- Automatic resume distribution to third parties.
- Advanced analytics, market intelligence, salary prediction, or application success prediction.
- Mobile apps and mobile authentication.
- Cloud AI provider integration or automatic local-to-cloud fallback.
- Multi-agent orchestration, paid job-board integrations, and team/recruiter functionality.

## 6. Primary user journeys

### Journey A — First-time account and profile

1. Visitor opens the local application.
2. Visitor registers according to the approved registration policy.
3. User logs in and receives a server-managed session.
4. User enters relevant career facts and preferences.
5. User reviews and saves their profile.
6. User sees a clear next step to add a job opportunity.

**Acceptance outcome:** The authenticated user can retrieve and update their own profile; another test user cannot access it.

### Journey B — Save and track a job

1. User opens the job workspace and chooses to add a job.
2. User enters the job title, company, description, source URL, location, and available details.
3. User saves the job.
4. User creates an application/tracking record and selects an initial status.
5. User updates status and notes as the process progresses.
6. User sees the application and status counts on the dashboard.

**Acceptance outcome:** Job and application records persist, remain owner-scoped, and dashboard counts match the user's records.

### Journey C — Manage resume versions

1. User uploads an original resume after upload policy and scanning controls are configured.
2. User sees the resume in a private resume library.
3. User uploads another version or labels a version for a particular role.
4. User selects a resume for a saved job.
5. User can download or delete only their own documents.

**Acceptance outcome:** Multiple versions can be managed without overwriting the original; unauthorized users cannot list or download them.

### Journey D — Request AI job-match and resume suggestions

1. User selects one saved job and one resume/profile.
2. The application sends only the required content to the configured local Ollama service.
3. The service returns structured observations and suggested wording.
4. The application labels the result as AI-generated and shows the source context.
5. User reviews, edits, accepts, or rejects each suggestion.
6. Any adopted content is saved as a separate draft/version; the original resume remains unchanged.

**Acceptance outcome:** Suggestions are reviewable, traceable to source facts, and do not silently trigger external processing or consequential actions.

### Journey E — Review dashboard and upcoming interviews

1. User opens the dashboard.
2. User sees their saved-job/application totals by status.
3. User sees upcoming interviews based on dates they entered.
4. User selects an item to open its own job/application detail.

**Acceptance outcome:** Dashboard data is accurate for the current user and does not disclose another user's activity.

## 7. Initial conceptual domain model

The model below is an initial product-level vocabulary, not an approved SQL schema.

| Entity | Purpose | Key relationships |
|---|---|---|
| User | Account identity and lifecycle | Owns one CareerProfile and many Jobs, Applications, Resumes, and AI suggestion records |
| CareerProfile | User's career facts and preferences | Belongs to one User |
| Skill | Normalized skill vocabulary if needed | May be associated with a CareerProfile; normalization strategy TBD |
| Job | Saved job opportunity and description | Owned by one User; may be linked to many Applications only if product policy permits, otherwise one active application per user/job |
| Company | Company details associated with a job | May be shared as user-scoped or global reference data; ownership and deduplication policy TBD |
| Application | User's tracking record for a job | Belongs to one User and references one Job |
| Resume | Uploaded original or user-managed resume version | Belongs to one User; may be selected by multiple AI review runs |
| ResumeVersion / ResumeAsset | Distinguishes original and tailored versions and their stored file objects | Belongs to a Resume or User; exact aggregate boundary TBD |
| InterviewEvent | Interview date/time and optional notes | Belongs to an Application |
| AISuggestionRun | Records a user-requested local AI analysis | Belongs to one User; references a Job and optional Resume/Profile snapshot |
| AISuggestion | Individual match observation or proposed wording | Belongs to one AISuggestionRun; adoption remains a user action |

### Conceptual relationships

- User 1 — 1 CareerProfile.
- User 1 — many Jobs.
- User 1 — many Applications.
- User 1 — many Resumes.
- Job 1 — many Applications only if multiple applications/re-application history is supported; otherwise enforce one current application per user/job.
- Application 1 — many InterviewEvents.
- User 1 — many AISuggestionRuns.
- AISuggestionRun 1 — many AISuggestions.
- All user-owned relationships must be owner-consistent; a record owned by User A must not reference a private resource owned by User B.

### Data-model decisions still open

- Whether Company is a global reference entity or user-scoped value object.
- Whether multiple application attempts for the same job are supported.
- Whether resume versions are one aggregate with immutable assets or independent records.
- Whether profile/skill facts require normalization or can begin as structured fields.
- Soft-delete vs hard-delete policy, retention, and backup expiry.
- Owner-aware foreign keys, uniqueness, indexes, audit fields, and timestamp/timezone representation.
- Whether interview events are embedded in Application or modeled as a separate entity.

These must be settled in the database design milestone before migrations are written.

## 8. Acceptance criteria for MVP planning

The MVP is ready for implementation planning only when:

- [ ] Product scope and exclusions are reviewed and accepted.
- [ ] Account registration, verification, recovery, session and authorization policies are decided.
- [ ] Conceptual domain model is translated into an approved physical schema.
- [ ] Owner isolation and cross-user test cases are specified.
- [ ] Resume upload formats, limits, scanning, quotas, retention and recovery are approved.
- [ ] Local Ollama model/hardware baseline, grounding, prompt safety and failure behavior are approved.
- [ ] Dashboard definitions and status transitions are finalized.
- [ ] Local startup, backup/restore and test strategy are documented.
- [ ] Implementation milestone and its exact acceptance criteria are separately approved.

## 9. Decision log

- **Approved:** Recommended MVP scope (Option A): accounts/profile, job application tracker, resume management, job management, application dashboard, and local Ollama AI assistance for job matching/resume suggestions.
- **Approved:** Initial deployment/validation target is local-first.
- **Approved direction:** PostgreSQL 17 + Flyway + Docker Compose; browser server-side sessions; mobile deferred.
- **Not approved by this document:** Any implementation, public launch, cloud AI, automated job discovery, automated application submission, or recruiter outreach.

**Gate:** This document is documentation only. Do not add application code, dependencies, database migrations, API endpoints, UI implementation, storage adapters, or AI integrations under Branch 17.
