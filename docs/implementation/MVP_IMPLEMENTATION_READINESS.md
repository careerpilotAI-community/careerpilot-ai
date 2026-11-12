# Branch 21 — MVP Implementation Readiness & Delivery Plan

**Status:** Documentation baseline; Branch 23 build/quality slice completed in PR #30; product implementation remains gated  
**Date:** 2026-11-12  
**Prerequisites:** Approved MVP (Branch 17), database design and decisions (Branches 18–19), identity/session security design (Branch 20)

## 1. Purpose

Consolidate the approved product, architecture, persistence and security decisions into a practical, testable implementation sequence. Identify remaining decisions and acceptance criteria so implementation can begin in small, reviewable slices.

This document does not authorize implementation. No application code, SQL migrations, dependencies, CI workflows, Docker changes, endpoints or UI are included.

## 2. Baseline decisions carried forward

- Local-first initial validation; architecture remains portable to self-hosted and cloud deployments.
- React 19 + Vite + TypeScript frontend; Java 21 + Spring Boot 3.5.x + Maven backend. Verify actual repository manifests before changing versions.
- PostgreSQL 17, Flyway and Docker Compose for local database service.
- Browser authentication uses server-side sessions and HttpOnly cookies; CSRF remains enabled.
- Mobile support is deferred.
- Local filesystem document storage behind a storage abstraction.
- Ollama local AI first; no silent cloud fallback.
- AI-generated content is always a draft requiring user review.
- Owner identity comes from authenticated server-side session; all private data operations are owner-scoped.
- Resume originals are immutable; tailored versions are separate assets.
- MVP excludes automated scraping, applications, recruiter outreach, advanced analytics and mobile client.

## 3. Pre-implementation verification

Before the first implementation PR:
- Inspect current main tree, manifests, dependency versions, package scripts and existing tests.
- Confirm the existing application foundation from PR #7 and avoid duplicating scaffolding.
- Confirm exact Spring Boot/Spring Security/Flyway/PostgreSQL versions are mutually compatible.
- Confirm current Docker Compose service names, ports, health checks and persistent volume conventions.
- Confirm repository branch protection and required CI checks; this remains an outstanding repository-settings verification.
- Review all merged architecture docs for contradictions; update documentation through a separate PR if found.
- Establish baseline build/test commands for frontend and backend.

## 4. Recommended implementation sequence

### Slice 0 — Build and quality baseline — completed in Branch 23 / PR #30
- Maven Wrapper pinned to Maven 3.9.11; backend CI runs wrapper `verify`.
- Frontend lockfile committed; CI uses deterministic `npm ci`.
- Frontend ESLint, Prettier check, Vitest/jsdom and React Testing Library baseline added.
- Pull-request CI runs backend verification and frontend install, lint, formatting, tests and production build.
- PR #30 records successful permanent CI run `43164048115` on its latest PR commit.
- No product-feature behavior was added. Main branch protection and required-check enforcement remain unverified.
- Exit: build/quality baseline merged in PR #30 at `37f0f6d6054e49a81bb928eb14bb9920b1fb23f9`.

### Slice 1 — Database and persistence foundation
- Add PostgreSQL 17 local service only if not already present.
- Add Flyway baseline migration for the approved minimal identity/profile/job/application schema.
- Implement persistence entities/repositories only for the approved slice.
- Add owner-aware keys, constraints and indexes.
- Exit: clean database migration, repeatable integration tests, migration validation, backup/restore smoke test.

### Slice 2 — Identity and session security
- Implement registration/login/logout according to approved registration policy.
- Use strong password hashing and server-side session authentication.
- Set cookie attributes by deployment profile; rotate session ID after authentication.
- Keep CSRF enabled; configure SPA token handling and explicit CORS policy.
- Add session expiry/revocation and generic authentication errors.
- Exit: security test suite passes, including CSRF, session fixation, expiry, logout and unauthenticated access.

### Slice 3 — Owner-scoped profile and core tracker
- Implement career profile, saved jobs and applications.
- Enforce authenticated owner scope in every read/write path.
- Implement status transitions and application uniqueness/re-application behavior according to approved schema decision.
- Exit: User A cannot access or infer User B's data; core CRUD and dashboard tests pass.

### Slice 4 — Resume metadata and private document storage
- Implement upload only after file type/size, scanning, storage path, encryption, retention and deletion policies are approved.
- Store opaque file keys and metadata in PostgreSQL; binaries remain private.
- Preserve original and tailored assets as immutable versions.
- Exit: authorization, validation, download, checksum, backup and restore tests pass.

### Slice 5 — Local AI assistance
- Integrate Ollama behind a provider abstraction with explicit local provider configuration.
- Add model availability/health checks, timeouts, bounded input/output and graceful unavailable behavior.
- Ground suggestions in supplied job/profile/resume facts; retain provenance.
- Never silently send user data to a cloud provider.
- Exit: tests prove no cloud fallback, outputs remain drafts, and unsupported resume claims are rejected or flagged.

### Slice 6 — Integrated local MVP validation
- Validate end-to-end user journey: account → profile → save job → track application → manage resume → request AI suggestion → review suggestion.
- Test fresh setup, restart persistence, backup/restore and account data isolation.
- Document setup, configuration, troubleshooting and known limitations.
- Exit: all MVP acceptance tests pass and release checklist is approved.

## 5. Cross-cutting quality gates

- Unit tests for domain rules and service behavior.
- PostgreSQL integration tests for constraints and persistence.
- API/security tests for authentication, CSRF, authorization and cross-user isolation.
- Frontend tests for key flows and validation/error states.
- CI checks for backend build/test, frontend build/test, dependency/security scanning and migration validation.
- No secrets, session identifiers, resume contents or raw AI prompts in logs.
- Dependency versions pinned through supported build mechanisms; dependency updates reviewed.
- No feature marked complete solely because code compiles; acceptance tests and documentation must pass.

## 6. Decisions still required before relevant slices

| Decision | Required before | Recommended handling |
|---|---|---|
| Registration mode (open/invite/operator-created) | Identity slice | Configurable by deployment; default local profile restricted |
| Email verification | Public registration | Optional only when configured delivery exists; no fake verification |
| Session idle/absolute expiry | Identity slice | 30-minute idle / 12-hour absolute as proposed defaults; validate UX/security |
| Argon2id parameters | Identity slice | Benchmark on supported local hardware and configure safely |
| Application re-application representation | Persistence slice | Resolve explicitly before migration; do not silently overwrite history |
| Account deletion and backup expiry | Persistence release | Document retention and deletion semantics before launch |
| Resume file validation/scanning/encryption | Resume slice | No upload feature until policy and implementation are approved |
| Ollama supported model/hardware baseline | AI slice | Select based on measured local performance; no assumptions about hardware |
| Backup location, retention and encryption | Local MVP release | User-configurable private location; verify restore |
| Public/self-hosted release hardening | Later deployment | Separate threat model and operational review |

## 7. MVP acceptance criteria

- User can create/access an account under the selected registration policy and securely log out.
- Session is server-side, cookie protected, rotated on authentication and invalidated on logout/expiry.
- CSRF protection is effective for unsafe browser requests.
- User can create and update a career profile.
- User can save job details and manage application status.
- Dashboard contains only the authenticated user's records.
- User can upload/manage resumes only after document security policy approval; originals remain unchanged.
- Local AI can produce reviewable suggestions when configured and available.
- AI never submits applications or contacts recruiters.
- No implicit cloud processing or fallback occurs.
- User data survives restart and can be restored from documented backup.
- Cross-user access tests pass for records, files, exports and AI operations.
- Setup and operational documentation is sufficient for a clean local installation.

## 8. Release and implementation authorization gates

- [ ] Confirm exact repository foundation and dependency versions.
- [ ] Approve registration and account lifecycle policy.
- [ ] Approve password hashing parameters and session expiry defaults.
- [ ] Resolve application re-application schema.
- [ ] Approve account deletion and backup retention policy.
- [ ] Approve document security policy before upload implementation.
- [ ] Select and benchmark Ollama model/hardware baseline before AI integration.
- [ ] Approve CI/security checks and branch protection requirements.
- [ ] Approve the implementation sequence and first slice.
- [ ] Receive explicit authorization to start implementation.

**Gate:** This branch is planning/documentation only. Merge does not authorize implementation. Begin only the specific slice the user explicitly approves.
