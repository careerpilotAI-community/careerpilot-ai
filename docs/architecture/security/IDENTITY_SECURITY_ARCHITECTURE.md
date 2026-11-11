# CareerPilot AI — Identity and Security Architecture

**Status:** Proposed design for review; documentation only.  
**Last updated:** 2026-11-12  
**Related:** [Target Architecture](../../ARCHITECTURE.md) · [Project Roadmap](../../PROJECT_ROADMAP.md) · [System Design Diagrams](../SYSTEM_DESIGN_DIAGRAMS.md)

## 1. Purpose and security objectives

This document defines the identity, account lifecycle, authorization, session, application-security, and privacy controls required for a multi-user CareerPilot AI deployment.

Security objectives:
- Verify identity before granting access to private application data.
- Enforce least privilege and strict per-user data isolation on every request.
- Protect credentials, sessions, uploaded career documents, and personal information.
- Make account recovery and security-sensitive changes resistant to account takeover.
- Provide auditable security events without logging secrets or document contents.
- Keep secure defaults consistent across local, self-hosted, and cloud deployments.

This is a target design, not a claim that all controls are implemented. The browser authentication direction (server-side sessions with an HttpOnly cookie; mobile deferred) is already selected. The concrete account policy and operational values below remain proposals until reviewed.

## 2. Identity and account model

### 2.1 Account states

Proposed lifecycle:

- **PENDING_VERIFICATION:** registration accepted; email ownership not yet confirmed.
- **ACTIVE:** verified and permitted to authenticate.
- **LOCKED:** temporarily blocked by security/risk controls or administrative action.
- **DISABLED:** sign-in blocked; data retained according to documented retention policy.
- **DELETION_PENDING:** user requested account deletion; access is revoked while the deletion workflow completes.
- **DELETED / ANONYMIZED:** personal identity and associated data removed or irreversibly anonymized as legally and operationally appropriate.

Only ACTIVE accounts can establish normal authenticated sessions. Account state changes must invalidate existing sessions where appropriate. Do not reveal whether an email address exists through registration, login, or recovery responses.

### 2.2 Registration and verification

- Collect only the minimum required identity data. Email and password are sufficient for the initial account model unless product requirements justify more.
- Normalize email consistently and enforce a unique database constraint on the normalized value.
- Verify email ownership before enabling normal account access.
- Use a cryptographically random, single-use, short-lived verification token. Store only a hash of the token; bind it to the account and purpose.
- Rate-limit registration, resend, and verification endpoints.
- Avoid account enumeration through response text, status behavior, and timing differences where practical.
- Do not permit users to select privileged roles during registration. New accounts receive the standard user role server-side.

Whether registration is open, invite-only, or administrator-provisioned remains a product decision.

## 3. Authentication and password security

### 3.1 Browser session model

The browser uses the approved server-side session model, not JWT access/refresh tokens.

- Authenticate credentials over HTTPS.
- Use a server-side session store; the browser receives only an opaque session identifier in an HttpOnly cookie.
- Set cookie flags: HttpOnly, Secure for remote deployments, SameSite=Lax by default, Path=/, and host-only scope (no Domain attribute where supported).
- Regenerate the session ID after successful login and privilege changes.
- Enforce idle and absolute session expiration.
- Invalidate server-side sessions on logout, password reset/change, account disablement, and other relevant security events.
- Never place session IDs in URLs, localStorage, sessionStorage, analytics, or logs.
- Configure local-only cookie behavior separately; never weaken remote deployment defaults to make local development easier.
- Use a shared session repository before horizontally scaling multiple backend instances; the concrete technology is an implementation decision.

### 3.2 Password handling

Recommended initial choice: **Argon2id** using a maintained implementation, with parameters calibrated on supported deployment hardware and periodically reviewed. If the selected Spring Security stack or deployment constraints make Argon2id impractical, use BCrypt with a reviewed work factor as a documented alternative.

Requirements:
- Unique salt per password, managed by the password-hashing library.
- Never encrypt passwords reversibly or store plaintext.
- Never log submitted passwords or password hashes.
- Enforce a reasonable minimum length (proposed 12 characters), permit long passphrases, and do not impose arbitrary composition rules.
- Reject commonly compromised passwords using a local or privacy-preserving check where practical.
- Support password change with current-password reauthentication.
- On password change/reset, invalidate all active sessions and require a fresh login.
- Do not force periodic password rotation absent evidence of compromise.

Final password length, compromised-password checking, and Argon2id parameters are review decisions.

### 3.3 Login abuse controls

- Rate-limit by a combination of account identifier and source/network signals, with safeguards against attackers locking out victims.
- Use progressive delays or temporary throttling rather than permanent lockout from unauthenticated attempts.
- Return generic failure messages.
- Monitor repeated failures, unusual recovery activity, and suspicious session behavior.
- Do not rely on IP-only controls; shared networks and privacy tools make them imperfect.
- CAPTCHA or step-up verification may be introduced only when abuse signals justify the usability and privacy cost.

## 4. Account recovery and security-sensitive actions

Password reset:
1. Accept a reset request without confirming whether the account exists.
2. Send a single-use, short-lived reset link through the verified channel.
3. Store only a hash of the random reset token, bound to account and purpose.
4. Rate-limit requests and token validation.
5. After successful reset, invalidate all sessions and outstanding reset tokens.
6. Notify the account owner of the completed change without including the password or reset token.

Email change:
- Require recent reauthentication.
- Verify control of the new address before switching it.
- Notify the old verified address when possible.
- Invalidate sessions or require reauthentication after the change.

Sensitive actions (password/email changes, account deletion, export initiation, security-setting changes) require recent authentication and CSRF validation. Never send credentials or reset tokens to analytics or logs.

MFA is not required for the first MVP unless public launch risk assessment requires it. Design the account model so MFA can be added later without changing ownership semantics. Before adding MFA, document recovery codes, authenticator enrollment, factor reset, and support procedures.

## 5. Authorization and multi-user isolation

### 5.1 Roles and permissions

Use least privilege. Proposed initial roles:

| Role | Scope |
|---|---|
| USER | Own profile, jobs, resumes, documents, applications, and preferences |
| ADMIN | Deployment-level operational administration only; no routine access to private career content |
| SUPPORT (deferred) | No initial role; if introduced, narrowly scoped, time-bound, audited access |

Do not add ADMIN merely to make every operational task available in the application UI. Administrative privileges must be granted through a trusted provisioning process, never through public registration or client input.

### 5.2 Authorization rules

- Derive the authenticated principal exclusively from the validated server-side session.
- Every service operation must authorize the requested action against the principal and resource owner.
- Scope repository queries by the authenticated owner; avoid loading an arbitrary object by ID and checking ownership only in the frontend.
- Use owner-aware composite foreign keys for relationships between user-owned records where appropriate.
- Apply authorization to reads, writes, exports, downloads, deletes, background jobs, and AI context retrieval.
- Return safe not-found/forbidden responses that do not disclose another user's record existence.
- Never trust client-supplied user IDs, role names, storage keys, filenames, tenant IDs, or ownership fields.
- Scheduled/background tasks must carry an explicit, validated owner or system scope; never run as an implicit global user.
- Admin access to user content is denied by default. Any exceptional support access requires a separate, explicit, time-limited, audited workflow and user/legal policy.

### 5.3 Tenant isolation testing

Automated tests must create at least two users and verify that User A cannot list, read, modify, delete, export, download, or generate AI output using User B's resources. Include ID substitution, nested-resource, bulk endpoint, pagination/filter, file-key guessing, and background-job cases.

## 6. Browser and API security

- **CSRF:** Keep Spring Security CSRF protection enabled for cookie-authenticated state-changing requests. Use a documented SPA token delivery mechanism and require the token header. SameSite is defense-in-depth, not a substitute.
- **CORS:** Allow exact configured origins only. Never combine wildcard origins with credentials. Restrict methods and headers to those required.
- **HTTPS:** Require HTTPS for all remote/self-hosted/cloud environments. Configure trusted reverse-proxy headers explicitly; do not trust arbitrary forwarded headers.
- **Security headers:** Configure an appropriate Content-Security-Policy, HSTS for HTTPS deployments, X-Content-Type-Options, frame protections, Referrer-Policy, and a restrictive Permissions-Policy. Tune CSP to the actual Vite/React asset and deployment model; avoid unsafe-inline/eval where feasible.
- **Input validation:** Validate request schemas, lengths, formats, enum values, pagination, and sorting. Use parameterized database access and context-aware output encoding.
- **Mass assignment:** Bind only explicitly permitted request fields; never bind owner, role, account state, or privileged flags directly from user input.
- **API errors:** Return stable, sanitized error responses. Never expose stack traces, SQL, filesystem paths, provider payloads, secrets, or session internals.
- **Rate limiting:** Protect login, registration, recovery, upload, export, AI generation, and other resource-intensive endpoints.
- **Dependencies:** Pin and regularly update dependencies; enable dependency and secret scanning; review high/critical findings before release.
- **File and AI boundaries:** Follow the dedicated file-storage and AI-provider security designs. Uploaded files and model outputs are untrusted input.

## 7. Secrets, encryption, and data handling

- Keep secrets out of Git, frontend bundles, logs, test fixtures, and error messages.
- Inject deployment secrets through environment/secret-management facilities; use separate credentials per environment and least-privilege scopes.
- Rotate credentials and document emergency revocation.
- Require TLS in transit between browser, reverse proxy, backend, database, object storage, and cloud AI provider where applicable.
- Use encryption at rest provided by the database, disk, or object-storage platform; define key ownership, rotation, recovery, and access policy before production.
- Do not claim end-to-end encryption: the backend must process data for the product features.
- Minimize personal data in logs and analytics. Do not log passwords, session IDs, reset/verification tokens, resume contents, job application content, AI prompts/responses, or provider credentials.
- Define retention and deletion behavior for account data, backups, audit records, and provider-side data before public multi-user launch.

## 8. Security events, audit, and monitoring

Record security-relevant events with timestamp, actor/account reference, event type, outcome, correlation ID, and safe metadata:
- login success/failure and logout;
- session invalidation and expiration;
- registration verification and recovery request/completion;
- password/email changes;
- role or account-state changes;
- account export/deletion requests;
- administrative configuration changes;
- exceptional support access, if ever introduced.

Never record raw secrets, session cookies, reset links, document content, or AI prompt/response content. Protect audit records from ordinary user modification and restrict access to operational personnel. Define retention and alert thresholds before production.

Monitor authentication failure rates, recovery abuse, unusual session activity, authorization denials, rate-limit events, dependency vulnerabilities, and service health. Alerts must not contain sensitive user content.

## 9. Account data lifecycle

Before public multi-user release, define and test:
- account data export in a usable format;
- account deletion request, confirmation, and completion;
- deletion of owned database records and document objects;
- cleanup of derived AI artifacts and queued/background work;
- handling of audit/security records that must be retained;
- backup expiry and the maximum period before deleted data ages out of backups;
- notification and recovery behavior for interrupted deletion workflows.

Deletion must be idempotent and retryable. A user must not be told deletion is complete until the defined workflow reaches its verified completion state.

## 10. Threat model and security tests

| Threat | Primary mitigations |
|---|---|
| Credential stuffing / brute force | Rate limits, generic responses, monitoring, compromised-password checks |
| Session theft / fixation | HttpOnly/Secure cookie, ID rotation, HTTPS, expiry, logout invalidation |
| CSRF | Spring Security CSRF token, exact CORS origins, SameSite defense-in-depth |
| IDOR / cross-user data exposure | Principal-derived owner scope, owner-aware constraints, automated two-user tests |
| Privilege escalation | Server-assigned roles, least privilege, explicit DTO binding, audited admin provisioning |
| Account takeover through recovery | Single-use hashed tokens, short expiry, rate limits, session invalidation |
| XSS / malicious content | Output encoding, CSP, safe rendering, untrusted upload/model content handling |
| Secret leakage | Secret injection, scanning, redacted logs, rotation |
| Abuse of expensive endpoints | Per-account/deployment quotas, concurrency limits, request bounds |
| Data loss / incomplete deletion | Tested backup/restore, idempotent deletion workflow, lifecycle reconciliation |

Release-blocking tests:
- Cookie flags, session ID rotation, expiration, logout and account-state invalidation.
- CSRF missing/invalid token rejection for state-changing requests.
- CORS rejects untrusted origins and credentialed wildcard configuration.
- User A cannot access User B's records, files, exports, or AI context.
- Public registration cannot assign ADMIN or alter account state/owner fields.
- Recovery tokens are single-use, hashed at rest, expire, and invalidate sessions.
- Sensitive values do not appear in logs or API errors.
- Account export and deletion lifecycle is tested end-to-end.
- Security headers and HTTPS behavior are verified in the deployed profile.

## 11. Open decisions and implementation gate

Resolve and approve before identity/security implementation:

1. Registration policy: open registration, invitation-only, or administrator provisioning.
2. Email verification delivery provider and privacy-safe delivery configuration.
3. Password policy values and Argon2id parameters (or documented BCrypt alternative).
4. Login throttling thresholds, lockout/delay policy, and abuse monitoring.
5. Session repository and idle/absolute timeout values for local, single-instance, and horizontally scaled deployment.
6. Exact CSRF token mechanism, trusted CORS origins, reverse-proxy trust, and cookie profile.
7. Whether MFA is deferred for MVP or required before public launch.
8. Initial role model and trusted administrator provisioning process.
9. Account export/deletion semantics, audit retention, and backup expiry.
10. Security monitoring ownership, incident response, and dependency update policy.

**Implementation gate:** Documentation only. Do not add registration/login controllers, Spring Security configuration, session-store dependencies, password hashing code, email delivery, account tables/migrations, MFA, or security filters until the open decisions are reviewed and explicitly approved.
