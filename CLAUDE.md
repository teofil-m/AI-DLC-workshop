# AI-DLC Workshop — Claude Code Guide

## Project
Spring Boot 4 + Java 21 application with Spring AI integration (Azure Vector Store, RAG pipelines).  
Frontend: Thymeleaf. Auth: Spring Security 6. Persistence: JPA + Flyway + PostgreSQL/H2.

## Agent Swarm

This project ships six specialised sub-agents. Use them via the Claude Code agent system (`/agents` or the Agent tool). Each agent owns a specific layer of the stack.

| Agent | Trigger phrase / use case |
|---|---|
| `fe-dev` | Thymeleaf templates, CSS, JS, WebSocket client, UI/UX |
| `be-dev` | REST controllers, services, JPA, Flyway migrations, WebSocket server, Spring Security config |
| `ai-integrator` | Spring AI wiring: ChatClient, RAG, embeddings, vector store, tool calling, prompt templates |
| `tdd-agent` | Write tests first (JUnit 5, `@WebMvcTest`, `@DataJpaTest`), TDD red-green-refactor cycles |
| `code-reviewer` | Review diffs, PRs, or files for correctness, security, performance, and coverage |
| `ai-apps-architect` | Architecture decisions, ADRs, module design, cross-cutting concerns, agent coordination |

### Running agents in parallel
When a task spans multiple layers, spawn agents concurrently. Example — adding a chat feature:
1. `ai-apps-architect` → design the component boundaries
2. `ai-integrator` + `be-dev` run in parallel → implement Spring AI wiring + REST endpoint
3. `fe-dev` → build the Thymeleaf streaming UI
4. `tdd-agent` → write slice tests for each layer
5. `code-reviewer` → final review

## Build & Run

```bash
./gradlew bootRun          # start with H2 (dev profile)
./gradlew test             # run all tests
./gradlew build            # full build + tests
```

## Key Conventions
- DTOs are Java records; JPA entities are never exposed in API responses
- All schema changes go through Flyway (`src/main/resources/db/migration/V{n}__{desc}.sql`)
- Prompt templates live in `src/main/resources/prompts/`
- Secrets via environment variables — never committed to source
- Tests: unit tests are plain JUnit 5; Spring context tests use the appropriate slice annotation
