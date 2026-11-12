# Branch 22 — Build & Quality Baseline Audit

**Audit date:** 2026-11-12  
**Audited ref:** `main` at `08f303dbdae02eeb3193636ad8b696ca947d251a`  
**Scope:** Read-only inspection of repository structure, manifests, starter source, tests, CI and project readiness documents.  
**Change boundary:** This report is documentation only. No application code, dependencies, configuration, CI, migrations or infrastructure were changed by the audit.

## 1. Executive summary

The repository is a clean, deliberately minimal application foundation. The checked-in backend and frontend starter files align with the documented Java 21 / Spring Boot 3.5.11 and React 19 / Vite / TypeScript direction. A GitHub Actions workflow already builds/verifies the backend and builds the frontend on pushes and pull requests targeting `main`.

The repository is **not yet an MVP implementation**. The backend currently contains only the Spring Boot entry point, basic Actuator configuration and one context-load test. The frontend is a static landing/status screen. There is no persistence integration, Flyway migration, PostgreSQL driver, Docker Compose file, authentication, API feature implementation, or product workflow UI.

**Overall assessment:** suitable as a starter foundation; not ready to begin feature slices without first completing and verifying the build baseline and agreeing a reproducible frontend dependency install. The next implementation step should remain the bounded build/quality baseline, followed by persistence only after the open schema and operational decisions are approved.

## 2. Repository snapshot

The recursive `main` tree was not truncated. Relevant observed structure:

- `.github/workflows/ci.yml`, `.github/dependabot.yml`, issue and pull-request templates.
- `backend/pom.xml`, a single application entry point, `application.properties`, and one Spring Boot context test.
- `frontend/package.json`, Vite/TypeScript configuration, and a small React app with CSS.
- Architecture, product, persistence, identity/security and implementation-readiness documents under `docs/`.
- No checked-in frontend lockfile, Docker Compose manifest, Flyway migration directory, database configuration, or application feature modules appeared in the tree.

Repository default branch is `main`. The audited tree corresponds to the Branch 21 merge commit above.

## 3. Verified technology baseline

| Area | Observed in repository | Assessment |
|---|---|---|
| Java | Maven property `java.version=21`; CI uses Temurin 21 | Matches documented target |
| Spring Boot | Parent version `3.5.11` | Exact version verified |
| Maven | Maven project; CI runs `mvn verify` | Wrapper not present in audited tree; local Maven version is a prerequisite |
| Frontend | React and React DOM `^19.2.0` | Matches React 19 direction |
| Build tooling | Vite `^7.1.12`, TypeScript `~5.9.3`, React plugin `^5.0.4` | Versions verified from manifest |
| Node | CI uses Node 22; local guide says Node 22+ | Consistent |
| Database | PostgreSQL 17 selected in docs, but no driver/configuration/migrations present | Planned, not implemented |
| Migration tool | Flyway selected in docs, but no dependency or migration files present | Planned, not implemented |
| Containerization | Docker Compose selected in docs, but no compose file present | Planned, not implemented |
| Security | Session/CSRF design documented, but no Spring Security dependency or security implementation present | Planned, not implemented |
| AI / storage | Ollama and private file storage documented, no implementation present | Planned, not implemented |

The Maven dependency list currently includes Spring Web, Actuator, Validation and the test starter. It does not include Spring Security, Spring Data JPA, PostgreSQL, Flyway, or a database driver.

## 4. Backend findings

### Implemented

- `CareerPilotApiApplication` starts a standard Spring Boot application.
- Application name and configurable server port are set.
- Actuator exposes only `health` and `info`; health details are hidden.
- A single `@SpringBootTest` context-load smoke test exists.
- CI executes Maven `verify`.

### Gaps

- No controllers, domain model, services, repositories, persistence configuration or API contract are present.
- No database driver, connection configuration, Flyway dependency or migration scripts are present.
- No authentication, session management, Spring Security, CSRF or authorization code is present.
- The sole test checks that the application context loads; it does not verify HTTP behavior, configuration profiles, persistence, security or domain rules.
- No Maven Wrapper files were found in the audited tree. Reproducibility currently depends on a compatible locally installed Maven version.

**Assessment:** the backend is a valid starter skeleton, not yet a functional career application API.

## 5. Frontend findings

### Implemented

- React 19 application bootstrapped with React DOM and Vite.
- TypeScript project references and Vite React plugin are configured.
- Landing/status page communicates that application features are planned.
- Vite development proxy forwards `/api` and `/actuator` to `http://localhost:8080`.

### Gaps

- No router, API client, state/data layer, forms, authentication UI or product feature screens are present.
- No frontend test runner or test files were found.
- No lint or formatting scripts/configuration were found in the manifest/tree inspected.
- No package lockfile was found. CI uses `npm install`, so dependency resolution is not pinned by a committed lockfile and clean builds may resolve different compatible dependency versions over time.

**Assessment:** a minimal presentational shell only; no MVP workflow is implemented.

## 6. CI, dependency and repository hygiene

### Existing strengths

- CI runs on pull requests and pushes to `main`.
- Backend and frontend checks run as separate jobs.
- Workflow permissions are limited to `contents: read`.
- Concurrency cancels superseded runs for the same workflow/ref.
- Maven dependency caching is configured.
- Dependabot is configured for Maven, npm and GitHub Actions on a monthly cadence.
- `.gitignore` excludes build output, node modules, common environment files, keys/certificates, credentials and logs.

### Gaps / verification needed

1. **Frontend reproducibility:** add and commit a lockfile, then use `npm ci` in CI after validating the lockfile locally.
2. **Frontend quality gates:** decide whether linting and unit/component tests are required for the first product slice; currently only TypeScript compilation and Vite production build run.
3. **Backend test depth:** preserve the context smoke test, but add focused tests with each feature and integration tests when persistence is introduced.
4. **Build tool reproducibility:** decide whether to add Maven Wrapper and pin its distribution.
5. **Security/dependency checks:** no explicit dependency vulnerability, secret scanning, static analysis or container scanning workflow was identified in the inspected CI configuration. Decide which checks are appropriate before broader distribution.
6. **Branch protection:** the repository tree and CI workflow do not establish whether branch protection/rulesets require the CI checks. This audit did not verify repository ruleset settings.
7. **Workflow action maintenance:** current workflow uses `actions/checkout@v4`, `actions/setup-java@v4` and `actions/setup-node@v4`; review action versions as part of routine maintenance rather than changing them in this audit.

## 7. Documentation consistency

- The README accurately says the repository contains a starter backend/frontend and that integrations are planned, not implemented.
- The roadmap and implementation-readiness document correctly distinguish approved design from implemented features and require separate implementation approval.
- The local development guide matches the current starter: backend and frontend run separately, and PostgreSQL is not connected.
- The README opening describes the project as an “open-source” platform, while its copyright section explicitly says the repository is not currently licensed for reuse and that no license file exists. These statements can confuse contributors and users. Recommend changing the introductory wording to “publicly viewable” or “community-facing” unless an open-source license is intentionally approved. **Do not add a LICENSE file or grant reuse rights as part of this audit.**
- The repository metadata reports a detected license classification of “Other”; the actual repository policy text states no reuse license is granted. Keep the written policy as the source of truth and avoid describing the project as open source until the licensing decision changes.

## 8. Prioritized recommendations

| Priority | Recommendation | Reason / exit evidence |
|---|---|---|
| P0 | Establish a reproducible baseline on a clean checkout | Record successful Java 21/Maven `mvn verify` and Node 22 frontend build |
| P0 | Commit frontend lockfile and switch CI to `npm ci` | Deterministic dependency installation |
| P0 | Verify required CI checks and branch protection/rulesets | Prevent unvalidated changes reaching `main`; ruleset status remains unverified |
| P1 | Decide Maven Wrapper policy | Reproducible Maven distribution across developer machines and CI |
| P1 | Define minimum backend/frontend test conventions | Avoid adding features without testable acceptance coverage |
| P1 | Add lint/format checks if selected by the team | Consistent frontend and Java quality gates |
| P1 | Resolve README “open-source” wording | Align project description with no-license reuse policy |
| P2 | Select appropriate dependency/security scanning gates | Improve supply-chain and release readiness before external deployment |

## 9. Recommended implementation sequence after this audit

1. **Build & quality baseline:** lockfile, deterministic install, wrapper decision, documented local commands, CI and required-check verification. Keep this slice free of product features.
2. **Persistence implementation:** only after the schema, migration, backup/restore and retention decisions are explicitly approved. Add PostgreSQL/Flyway/Compose in a bounded PR.
3. **Identity/session implementation:** only after registration, password hashing, session lifetime, CSRF/CORS and account lifecycle decisions are approved.
4. **Owner-scoped tracker:** implement profile, saved jobs and applications with cross-user tests.
5. **Resume/document management:** only after file validation, scanning, encryption, retention and deletion policies are approved.
6. **Local AI:** only after model/hardware baseline, grounding, privacy, and no-cloud-fallback tests are specified.
7. **Integrated local MVP validation:** fresh setup, persistence, isolation, backup/restore and end-to-end acceptance.

This sequence follows the merged Branch 21 delivery plan. It does not convert any planned feature into an approved implementation.

## 10. Audit limitations

- This was a repository-content and configuration review of the `main` tree; no local clone or executable build was run.
- CI workflow file contents were inspected, but no current workflow run result was available for the audited commit from the commit-associated workflow-run query.
- Repository branch protection/rulesets were not verified.
- No dynamic application security testing, dependency vulnerability scan, performance test or deployment test was performed.
- Findings describe the audited commit only and may change after subsequent merges.

## 11. Approval boundary

Branch 22 is a read-only baseline audit and documentation deliverable. It does not authorize implementation or modify application code. Any build/quality changes and all product implementation slices require their own explicit scope and approval.


## 12. Historical status after Branch 23 (post-merge note)

This report is intentionally preserved as the Branch 22 historical audit of `main` at `08f303dbdae02eeb3193636ad8b696ca947d251a`. Its observations describe that audited tree, not the post-Branch-23 repository state. Branch 23 merged as PR #30 at `37f0f6d6054e49a81bb928eb14bb9920b1fb23f9` and addressed the build-baseline gaps: committed frontend lockfile with `npm ci`, Maven Wrapper pinned to 3.9.11, frontend lint/format/test tooling, a starter component test, and pull-request CI quality checks. PR #30 records successful validation on permanent CI run [43164048115](https://github.com/careerpilotAI-community/careerpilot-ai/actions/runs/43164048115). The run association lookup for the merge commit returned no workflow runs, so this historical note relies on the explicit CI evidence recorded in PR #30.

The following Branch 22 findings remain useful as historical context but must not be presented as current gaps: missing lockfile, missing Maven Wrapper, missing frontend lint/test scripts, and CI using `npm install`. The following remain unverified or open: branch-protection/ruleset enforcement and required-check configuration; dedicated security/dependency scanning decisions; feature-level backend/frontend tests beyond the starter baseline; and all product capabilities identified as not implemented in the audit. Branch protection was not verified by Branch 22 and remains unverified after Branch 23.

Branch 22 remains an immutable historical audit; do not rewrite its audited ref or imply that it inspected the Branch 23 tree.
