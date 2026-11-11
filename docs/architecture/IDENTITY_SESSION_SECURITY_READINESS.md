# Branch 20 — Identity, Session & Security Readiness

**Status:** Proposed security design; documentation only
**Date:** 2026-11-12
**Prerequisites:** MVP scope (Branch 17), database readiness (Branch 18), persistence decision review (Branch 19)

## 1. Purpose and boundary

Define the browser identity and security contract before persistence or identity implementation. The approved direction is server-side sessions with Secure/HttpOnly cookies and CSRF protection; mobile authentication is deferred.

This document is a design proposal only. It does not implement authentication, authorization, password handling, SQL, endpoints, filters, UI, or infrastructure. Separate explicit implementation authorization is required.

## 2. Identity lifecycle

- ACTIVE: may authenticate and use the application.
- DISABLED: authentication denied; existing data retained pending account policy.
- PENDING_VERIFICATION is used only if email verification is selected.
- DELETION_PENDING is used only if a complete deletion workflow is approved.
- Normalize email consistently before uniqueness checks and login.
- Store only a strong password hash. Argon2id is preferred where supported; BCrypt is an acceptable parameterized and benchmarked fallback.
- Never store plaintext/reversible passwords, password hints, or secrets in logs.
- Use generic authentication errors to reduce account enumeration.
- Rate-limit login, registration, password-reset and verification attempts.
- Password reset, if included, uses single-use, short-lived random tokens stored hashed and delivered through a configured channel.

## 3. Browser session contract

- Server creates an opaque, cryptographically random session identifier; session state remains server-side.
- Cookie flags: HttpOnly; Secure in HTTPS deployments; SameSite=Lax by default; Path=/; omit broad Domain unless required.
- Secure=false is permitted only in an explicit loopback development profile, never for internet-facing deployment.
- Rotate session ID after login and privilege changes to prevent fixation.
- Invalidate server-side session on logout, account disablement and password reset.
- Proposed timeout defaults for review: 30-minute idle timeout and 12-hour absolute lifetime.
- Never put session IDs in URLs, browser storage, logs, analytics or error messages.
- Require HTTPS for self-hosted/cloud deployments and configure trusted proxy handling explicitly.

## 4. CSRF and browser security

- Keep Spring Security CSRF protection enabled for state-changing cookie-authenticated requests.
- Use a synchronizer token or correctly configured cookie-to-header pattern.
- GET, HEAD and OPTIONS must not mutate state.
- Validate Origin/Referer as defense in depth, not as a replacement for CSRF tokens.
- Configure explicit CORS origins; credentialed requests must never use wildcard origins.
- Apply suitable CSP, X-Content-Type-Options, Referrer-Policy, frame protections and Permissions-Policy.
- Encode untrusted user and AI content when rendered; sanitize permitted rich text.
- Do not store auth tokens in localStorage or sessionStorage. Browser MVP uses the session cookie.

## 5. Authorization and multi-user isolation

- Resolve user identity exclusively from the authenticated server-side session.
- Never accept user_id, owner_id or role from request payload as an authorization source.
- Every read, update, delete, download, export and AI operation is owner-scoped.
- Use deny-by-default authorization and explicit role checks for any future administrative functions.
- UUIDs are identifiers, not access controls.
- Enforce ownership at service boundaries and reinforce relationships with owner-aware database foreign keys.
- Use consistent non-revealing not-found/forbidden behavior to avoid leaking record existence.
- Background tasks must receive trusted owner context and re-check authorization at execution time.
- Cross-user administrative access is excluded from MVP.

## 6. Passwords, secrets and configuration

- Keep database credentials, session keys, mail credentials and AI provider secrets out of source control.
- Use environment variables or deployment-specific secret stores; provide safe placeholder examples only.
- Fail startup when required production secrets are missing or insecure defaults are detected.
- Never reuse development secrets in self-hosted/cloud deployments.
- Document secret rotation and recovery.
- Never log passwords, cookies, session IDs, reset tokens, authorization headers, resume contents or raw AI prompts.

## 7. Abuse protection and observability

- Apply configurable rate limits to authentication and expensive AI endpoints.
- Bound request sizes and enforce server-side validation.
- Log safe structured error codes and correlation IDs, not sensitive content.
- Audit login success/failure, logout, password reset, account disablement and permission denial without secrets.
- Define log retention/access controls before public deployment.
- Provide a safe session revocation mechanism.

## 8. Required security tests

- Unauthenticated requests cannot access protected resources.
- Login rotates session ID; logout invalidates it server-side.
- Expired or disabled sessions cannot access protected resources.
- Missing/invalid CSRF token blocks unsafe cookie-authenticated requests.
- Cookie attributes match deployment profile.
- Cross-origin credentialed requests are rejected unless explicitly allowlisted.
- User A cannot list/read/update/delete/download/export User B records or documents.
- Tampered owner IDs and guessed UUIDs do not bypass ownership checks.
- Passwords, cookies, reset tokens and resume contents never appear in logs.
- Rate limits and generic errors resist brute-force and account enumeration.
- Security headers are present and verified in integration tests.
- Production profile rejects insecure default secrets and HTTP-only deployment.

## 9. Deployment profiles

| Profile | Transport | Registration and operations |
|---|---|---|
| Local developer | Secure may be disabled only for loopback HTTP; HttpOnly and SameSite remain enabled | Explicit development configuration; no real secrets |
| Local personal use | Prefer HTTPS; do not expose ports publicly by default | Registration disabled or restricted to owner unless deliberately configured |
| Self-hosted | HTTPS and Secure cookie required | Operator-managed secrets, backups, updates and account policy |
| Cloud | HTTPS and Secure cookie required | Managed secrets, monitoring, rate limiting, recovery and privacy disclosures |

Bind local services to loopback unless the user explicitly configures network access. Local deployment is not automatically secure.

## 10. Decisions requiring review

1. Email verification: configurable or mandatory for public/cloud registration?
2. Password hashing: Argon2id preferred, BCrypt acceptable fallback?
3. Session timeout: approve 30-minute idle and 12-hour absolute defaults?
4. Registration: open, invite-only, or operator-created accounts per deployment profile?
5. Password reset: include only when a secure delivery channel is configured?
6. Account deletion: define confirmation, deletion and backup-expiry behavior?
7. Administrative role: exclude from MVP unless a concrete operator requirement emerges?
8. Local exposure: loopback-only by default; explicit opt-in for LAN access?

## 11. Implementation gate

- [ ] Identity lifecycle and registration policy approved.
- [ ] Password hashing and reset policy approved.
- [ ] Session cookie and timeout policy approved.
- [ ] CSRF/CORS/browser security contract approved.
- [ ] Owner-scoped authorization rules approved.
- [ ] Secret management and deployment profiles approved.
- [ ] Security test plan accepted.
- [ ] Separate explicit authorization to implement identity/security received.

**Gate:** documentation only. Merging this proposal does not authorize implementation.