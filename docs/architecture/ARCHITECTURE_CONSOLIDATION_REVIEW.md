# CareerPilot AI — Architecture Consolidation Review

**Status:** Review findings and implementation-readiness gate; no application implementation authorized.  
**Date:** 2026-11-12

## 1. Purpose
This review consolidates the approved architecture direction and identifies decisions that must be closed before the next application-code milestone. A merged documentation PR does not mean the described feature has been implemented, tested, deployed, or approved for production.

## 2. Consolidated architecture baseline
| Area | Current direction | Status / remaining gate |
|---|---|---|
| Deployment | One codebase supports local, self-hosted, and cloud | Direction documented; choose first end-to-end deployment target |
| Frontend | React 19, Vite, TypeScript | Foundation exists; verify exact dependency versions in manifests |
| Backend | Java 21, Spring Boot 3.5.x, Maven | Foundation exists; verify exact dependency versions |
| Database | PostgreSQL 17 | Selected; finalize schema, indexes, ownership constraints and operations |
| Migrations | Flyway | Selected; verify compatibility/version and migration execution ownership |
| Local database | Docker Compose; native PostgreSQL alternative | Local convenience, not a production requirement |
| Tenancy | Multi-user, strict owner-scoped authorization | Required; automated cross-user tests are release-blocking |
| Browser authentication | Server-side sessions with HttpOnly cookie; mobile deferred | Direction selected; operational values and detailed controls remain open |
| Document storage | Hybrid local filesystem and private object storage adapters | Design documented; limits, scanner, quotas, retention and restore remain open |
| AI | Switchable local Ollama and configured cloud provider | Design documented; cloud provider, model, consent UX, quotas and terms remain open |
| Privacy | No silent local-to-cloud fallback; explicit disclosure and consent | Required invariant |
| Career actions | AI output remains a draft; user approves consequential actions | Required product invariant |

## 3. Cross-document review findings
1. **Markdown diagram fences:** Some Mermaid fences in ARCHITECTURE.md and SYSTEM_DESIGN_DIAGRAMS.md are escaped as literal backticks, while other diagrams use normal fenced blocks. Standardize and verify GitHub rendering.
2. **Diagram numbering:** The system design document's key design invariants contain duplicate/out-of-order numbering. Renumber sequentially and remove duplicated statements.
3. **Review-gate duplication:** ARCHITECTURE.md repeats identity/security decisions across review items. Consolidate into one checklist with status.
4. **Roadmap status clarity:** Distinguish completed foundation work (PR #7) from future persistence, identity, storage, AI and product milestones. Do not label the platform complete because the foundation is merged.
5. **Decision vs proposal labels:** Session timeouts, Argon2id parameters, registration policy, MFA scope and retention periods are proposals/unresolved, not approved operational values.
6. **No implementation implication:** A design marked reviewed/merged is not evidence of a working security control.
7. **Specialist gates:** Do not infer cloud vendors, free-tier guarantees, scanner availability, backup objectives, or AI data-retention terms.
8. **First deployment target:** The architecture supports all modes, but MVP validation should start with one explicit target (recommended: local-first) before self-hosted/cloud readiness is claimed.

## 4. Decisions that must be explicitly closed
### Product and release scope
- Confirm MVP feature boundary and exclusions.
- Confirm local-first as the first end-to-end validation target, while retaining the all-modes architecture.
- Define public-launch readiness separately from local development readiness.

### Database and operations
- Final entity scope, indexes, uniqueness rules, owner-aware foreign keys, and deletion behavior.
- Exact Flyway version and Spring Boot/JDBC compatibility.
- Migration execution policy and rollback/forward-fix strategy.
- Backup encryption, retention, off-site copy, point-in-time recovery, and tested RPO/RTO.
- Test database and document restore together.

### Identity and application security
- Open vs invitation-only registration and email verification delivery.
- Password policy, Argon2id parameters (or documented BCrypt fallback), login abuse thresholds.
- Session repository, idle/absolute timeout, local vs remote cookie profiles.
- CSRF token mechanism, exact CORS origins, reverse-proxy trust and HTTPS termination.
- MFA requirement for public launch; administrator provisioning and exceptional support access.
- Account export/deletion, audit retention, backup expiry, security monitoring and incident-response ownership.

### Documents and AI
- Upload limits, allowed formats, per-user quotas, scanner selection and fail-closed behavior.
- Storage provider selection, encryption, retention, backup and restore.
- First cloud AI provider and review of data processing, retention and model-training terms.
- Local Ollama model and supported hardware requirements.
- Consent UX, provider/model allowlist, token/cost budgets, per-user/deployment quotas.
- Decide whether embeddings/vector search are excluded from MVP or need a separate design.

### Deployment and delivery
- First local setup and supported operating systems.
- Secret injection and rotation approach per deployment mode.
- CI quality gates, dependency/security scanning, integration test services and release process.
- Do not assume any free tier is permanent or production-grade.

## 5. Recommended implementation sequence after approval
1. Resolve and approve open decisions in this review and specialist architecture documents.
2. Reconcile documentation issues in Section 3 and merge the final architecture baseline.
3. Implement persistence foundation: PostgreSQL connection, Flyway migrations, local Compose setup, repository/integration tests.
4. Implement identity and session security with CSRF, account lifecycle, and cross-user authorization tests.
5. Implement profile and job tracker after persistence and identity gates pass.
6. Implement private document storage and scanning after its operational policy is approved.
7. Add AI adapters after provider terms, disclosure/consent, quotas, and local model requirements are approved.
8. Validate local deployment end-to-end before claiming self-hosted or cloud readiness.

This sequence is a recommendation, not authorization to start coding. Each milestone still requires explicit approval.

## 6. Implementation readiness checklist
- [ ] Architecture and system diagrams render correctly on GitHub.
- [ ] Specialist architecture documents are linked and internally consistent.
- [ ] MVP scope and first deployment target approved.
- [ ] Database schema and migration/backup decisions approved.
- [ ] Identity, session, CSRF/CORS, password and account-lifecycle decisions approved.
- [ ] Storage limits, scanning, retention and recovery decisions approved.
- [ ] AI provider, privacy, consent and budget decisions approved.
- [ ] CI/security gates and deployment secrets approach approved.
- [ ] Implementation order and acceptance criteria approved.

**Gate:** Until the checklist is explicitly reviewed and the user authorizes the next implementation milestone, this branch remains documentation-only. Do not add application code, dependencies, migrations, authentication endpoints, storage adapters, AI integrations, or cloud infrastructure as part of this review.