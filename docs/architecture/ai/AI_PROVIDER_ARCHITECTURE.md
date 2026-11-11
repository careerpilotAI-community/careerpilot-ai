# CareerPilot AI — AI Provider Architecture

**Status:** Proposed design for review; documentation only.  
**Last updated:** 2026-11-12  
**Related:** [Target Architecture](../../ARCHITECTURE.md) · [Project Roadmap](../../PROJECT_ROADMAP.md) · [File Storage Architecture](../storage/FILE_STORAGE_ARCHITECTURE.md)

## 1. Goals and approved direction

CareerPilot AI uses a switchable AI architecture so local, self-hosted, and cloud deployments can select an appropriate model without changing product-feature code.

| Concern | Proposed direction |
|---|---|
| Provider strategy | Backend-owned provider abstraction and adapters |
| Local provider | Ollama over a private backend-to-Ollama connection |
| Cloud provider | One explicitly configured cloud provider adapter at a time for the initial release |
| Provider selection | Server-side deployment configuration; no client-supplied provider/model override |
| Default privacy posture | Local Ollama when configured and operational; cloud processing is opt-in and clearly disclosed |
| Credentials | Server-side secret manager/environment injection only |
| Feature interface | Task-oriented AI application service; provider SDKs stay inside adapters |
| User control | AI output is advisory and saved as a draft; users review before use |
| Failure behavior | Timeouts, bounded retries, rate limits, quota handling, and clear non-sensitive errors |
| Cost control | Explicit provider enablement, model allowlist, token/input/output budgets, and usage accounting |

The approved direction is switchable local/cloud AI. The initial cloud vendor, exact models, embedding provider, and production budget are not selected in this document.

## 2. Logical architecture

Product features call a task-oriented application service (for example, resume analysis or cover-letter drafting). That service builds a validated task request and calls a provider-neutral interface. The configured adapter translates the request to the provider protocol, enforces limits, and normalizes the response.

No frontend component or browser request can select an arbitrary provider, endpoint, model, or API key. A user-facing preference may request a privacy mode only if the server explicitly supports it; the backend remains authoritative.

~~~mermaid
flowchart TB
  FE[Browser / React] --> API[Spring Boot API]
  API --> AUTH[Session, CSRF, owner authorization]
  AUTH --> FEATURE[CareerPilot feature service]
  FEATURE --> POLICY[AI policy and data minimization]
  POLICY --> TASK[Task-oriented AI service]
  TASK --> PORT[Provider-neutral AI interface]
  PORT --> ROUTER[Configured provider adapter]
  ROUTER --> OLLAMA[Private Ollama endpoint]
  ROUTER --> CLOUD[Approved cloud AI API]
  OLLAMA --> NORM[Response validation and normalization]
  CLOUD --> NORM
  NORM --> FEATURE
  FEATURE --> REVIEW[User reviews generated draft]
~~~

## 3. Provider interface and adapter contract

Keep the contract provider-neutral and task-oriented. Avoid exposing vendor SDK classes to application services.

Conceptual operations:

- `generate(request)` — produce a bounded text response for a named task.
- `health()` — report configured provider readiness without exposing secrets or private prompt content.
- `capabilities()` — report server-configured supported capabilities, limits, and model metadata.
- `estimateUsage(request)` — optional preflight estimate where the provider supports it.

A normalized request should include:
- task identifier and prompt/template version;
- system and user instructions separated by trusted server-side code;
- only the minimum approved context needed for the task;
- maximum input/output token budgets and timeout;
- response format/schema requirement where supported;
- correlation ID that contains no personal data.

A normalized response should include:
- generated content;
- provider and model identifiers from server configuration;
- usage/token counts when available;
- latency and safe finish/status classification;
- structured validation errors without raw provider payloads or secrets.

The provider adapter must validate response size and structure, handle malformed/empty output, and never treat provider output as trusted instructions or executable content.

## 4. Provider selection and configuration

Provider and model are deployment-controlled configuration, not user-controlled request parameters.

Proposed configuration concepts (names are illustrative and may be refined during implementation):

| Setting | Purpose |
|---|---|
| `AI_PROVIDER` | `ollama` or the identifier of an explicitly supported cloud adapter |
| `AI_MODEL` | Model allowlisted for the selected adapter |
| `OLLAMA_BASE_URL` | Private backend-reachable Ollama endpoint |
| `OLLAMA_MODEL` | Local model name |
| `AI_API_KEY` | Cloud provider credential injected as a secret |
| `AI_CONNECT_TIMEOUT` | Connection timeout |
| `AI_READ_TIMEOUT` | Overall provider response timeout |
| `AI_MAX_INPUT_TOKENS` | Maximum context budget |
| `AI_MAX_OUTPUT_TOKENS` | Maximum generated output |
| `AI_DAILY_BUDGET` | Optional deployment-level cost/usage budget |
| `AI_ALLOWED_MODELS` | Server-side model allowlist |

Configuration must be validated at startup. If a provider is not configured, the application should report AI as unavailable rather than silently switching to a different provider. Never silently fall back from local to cloud because that changes the data-disclosure boundary and can incur cost.

For Ollama, bind access to a trusted private interface/network, do not expose its unauthenticated API directly to the public internet, and restrict which backend hosts can reach it. For cloud providers, require TLS, verify certificates, keep keys out of logs, rotate credentials, and scope keys to the minimum provider permissions available.

## 5. Privacy, consent, and data minimization

A cloud AI provider receives data sent in prompts. Before sending personal data or document content to a cloud provider:

1. Clearly identify that an external AI provider will process the relevant information.
2. Explain the categories of data sent and the purpose.
3. Explain the provider's applicable retention/training/data-processing terms in the product's privacy notice.
4. Obtain affirmative user consent before the first cloud processing of personal or document data; provide a way to withdraw future consent.
5. Store the consent version, timestamp, user, and scope as an auditable preference without storing prompt contents.
6. Re-check consent when provider or material data-processing terms change.

Local processing should be the default when Ollama is configured and healthy, but the user must not be told data is local unless the backend confirms the actual selected route. A configured cloud provider must be clearly identified in the UI before processing.

Minimize each task's context:
- Send selected resume sections rather than the entire document where feasible.
- Remove unrelated contact details, account identifiers, and sensitive fields unless essential to the requested task.
- Do not send passwords, session cookies, API keys, internal logs, or unrelated users' records.
- Enforce user ownership before loading any resume/job context.
- Do not use one user's documents or prompts as another user's context.

Provider processing and retention terms require legal/product review before public multi-user cloud release. Do not promise zero retention or no model training unless the selected provider contract and configuration support that claim.

## 6. Prompt injection and untrusted content

Job descriptions, uploaded resumes, extracted document text, web content, and model responses are untrusted data.

- Clearly delimit untrusted document/job text in prompts and instruct the model to analyze it as data, not follow embedded instructions.
- Never let content in a resume/job description override system policy, authorization, provider selection, tool permissions, or application instructions.
- AI generation has no direct database write, file deletion, email sending, job application submission, or arbitrary tool execution capability.
- Use allowlisted task templates and bounded context; do not expose generic autonomous tool execution in the MVP.
- Validate structured output against a schema and apply business validation before storing or displaying it.
- Treat generated URLs, commands, code, and claims as untrusted suggestions.
- Require user review for resume edits, recruiter messages, and application materials. Consequential external actions require separate explicit confirmation.

## 7. Reliability, resilience, and failure handling

- Apply connection and response timeouts; never allow unbounded model generation to occupy request threads.
- Use bounded retries only for transient, idempotent failures; do not retry indefinitely or duplicate billable requests without an idempotency strategy.
- Respect provider rate-limit responses and retry-after guidance.
- Use a concurrency limit and bounded queue; reject overload with a recoverable response.
- Add circuit breaking or temporary provider-unavailable state to prevent repeated calls to a failing provider.
- Validate model output and fail safely on empty, malformed, oversized, or policy-incompatible responses.
- Do not automatically switch provider on outage. Present a clear unavailable state and let the user retry or explicitly choose another configured mode.
- Keep provider errors sanitized; never return raw SDK stack traces, prompts, credentials, or provider response headers to clients.
- For local Ollama, surface model-not-installed, host-unreachable, insufficient-resource, and model-loading states distinctly where safe.

## 8. Cost, quotas, and abuse controls

- Cloud AI is disabled until an administrator configures an approved provider and secret.
- Allowlist provider/model combinations; do not accept arbitrary endpoint URLs or model identifiers from clients.
- Apply per-user and deployment-level request, token, concurrency, and daily/monthly budget limits.
- Limit input document size and extracted text length before prompting.
- Track usage by user, task, provider, model, token count, latency, and outcome, while avoiding prompt/response content in analytics.
- Provide an operator kill switch to disable cloud AI immediately.
- Require explicit configuration for paid models; do not assume free tiers are permanent or suitable for production.
- Protect generation endpoints with session authorization, CSRF, rate limiting, and abuse monitoring.

## 9. Logging, observability, and audit

Record safe operational metadata: correlation ID, task type, configured provider/model, latency, usage counts, outcome, retry count, and sanitized error category.

Do not log prompt/response bodies, resume content, job-description text, personal contact details, API keys, cookies, authorization headers, or full provider payloads by default. Debug logging must not bypass this policy.

Audit:
- provider/model configuration changes;
- cloud AI enable/disable events;
- user cloud-processing consent and withdrawal;
- administrative budget/limit changes;
- generation request metadata and outcome, without prompt content.

Provide health and metrics for provider readiness, latency, error rates, rate limiting, usage/budget consumption, and local Ollama reachability. Restrict operational dashboards and avoid exposing user-level sensitive content.

## 10. Testing and validation

Before release, test:
- local Ollama and cloud adapter contract conformance;
- provider selection cannot be overridden by browser parameters;
- cloud calls are blocked without explicit configuration and required consent;
- no silent local-to-cloud fallback occurs;
- ownership checks happen before any user document is loaded;
- prompt injection in resume/job text cannot change policy or invoke tools;
- malformed/oversized output is rejected safely;
- timeouts, rate limits, outage, retry, circuit-breaker, and budget exhaustion;
- secrets and prompt content do not appear in logs or error responses;
- generated output remains a draft and requires user review.

## 11. Open decisions and implementation gate

Resolve before AI implementation:

1. Select the first cloud provider and confirm its data-processing agreement, retention, training-use, region, and security terms.
2. Select the initial local Ollama model and define minimum memory/compute requirements for supported local installations.
3. Decide whether embeddings/vector search are in MVP; if so, separately select embedding provider, vector storage, data deletion, and cloud/local privacy behavior.
4. Approve cloud AI disclosure and affirmative-consent UX, including consent withdrawal and versioning.
5. Set per-user and deployment token/cost budgets, rate limits, and paid-model allowlist.
6. Finalize task contracts, prompt versioning, output schemas, and safe context minimization.
7. Define operational monitoring, key rotation, emergency cloud-provider kill switch, and provider incident process.
8. Confirm whether users can select local/cloud mode or whether mode is administrator-controlled per deployment; any user preference must remain constrained by server policy.

**Implementation gate:** Documentation only. Do not add provider SDK dependencies, API keys, Ollama runtime integration, prompt templates, AI controllers, embeddings, vector databases, or AI execution code until the decisions above are reviewed and explicitly approved.
