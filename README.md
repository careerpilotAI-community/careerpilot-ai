# CareerPilot AI

**AI-assisted career-development platform for job seekers and professionals.**

CareerPilot AI is an early-stage project intended to bring career-development tools together, including job discovery, resume support, interview preparation, career guidance, and productivity features.

> **Project status:** Initial application foundation is in place. The target architecture is documented; implementation of persistence and product features is gated on architecture review.

## Project goals

- Make practical career-development tools accessible to job seekers.
- Help users prepare resumes and interviews.
- Support structured job-search workflows.
- Use AI as an assistant while keeping users in control of important decisions.

## Technology and architecture direction

- **Backend:** Java 21, Spring Boot 3.5.x, Maven.
- **Frontend:** React 19, Vite, and TypeScript.
- **Database:** PostgreSQL is the common database baseline across deployment modes; integration is planned, not yet implemented.
- **Deployment:** One codebase intended to support local, self-hosted, and cloud deployments.
- **Document storage:** Hybrid design with local filesystem and cloud/object-storage adapters.
- **AI:** Switchable design supporting local Ollama and a configurable cloud AI provider.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the target architecture and [docs/PROJECT_ROADMAP.md](docs/PROJECT_ROADMAP.md) for milestones and review gates.

The repository currently contains a starter backend and frontend, not the complete product. Architecture decisions do not imply that planned integrations are already implemented.

## Suggest an improvement

This repository is public so people can review the project and share ideas.

- Open a GitHub Issue to suggest a feature or report a problem.
- Explain the problem, expected benefit, and an example where possible.
- Do not post credentials, tokens, personal information, or confidential data.
- Pull requests and code contributions require prior written permission from the repository owner.

Submitting a suggestion does not automatically transfer ownership of separately copyrightable material in that suggestion. Any permission needed to adopt a contribution will be agreed in writing before use.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before participating.

## Security

Please read [SECURITY.md](SECURITY.md). Report vulnerabilities privately using the repository's private vulnerability reporting feature when available. Do not publish exploit details in public Issues.

## Copyright and permissions

Copyright © 2026 CareerPilot AI. All rights reserved.

This repository is publicly viewable, but it is **not currently licensed for reuse**. No permission is granted to copy, modify, distribute, sublicense, or commercially exploit repository contents. Prior written permission from the copyright holder is required, except for rights necessarily granted under GitHub's Terms of Service or required by applicable law.

There is intentionally no `LICENSE` file. Public visibility does not mean the project is open source or that reuse is permitted. Read [COPYRIGHT.md](COPYRIGHT.md) for the full permissions policy.

## Disclaimer

CareerPilot AI is intended as a career-support tool. It is not a recruitment agency and does not guarantee employment, interviews, or selection. Users should review AI-generated content and independently verify job listings and other information.
