# AI-DLC Workshop

Spring Boot 4 + Java 21 RAG chat application. Users upload documents, the app
chunks and indexes them into Azure AI Search, then answers questions against
those documents via an Azure OpenAI model. Streaming (`text/event-stream`) and
sync (`application/json`) chat endpoints are both provided. A Thymeleaf demo UI
is available in dev/test mode without authentication.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| Gradle wrapper | included (`./gradlew`) |
| Azure OpenAI resource | chat deployment + `text-embedding-3-small` embedding deployment |
| Azure AI Search resource | any tier; index is created automatically |

No local database setup is needed for dev — the app uses an in-memory H2
database by default.

---

## Running locally (dev profile)

### 1. Set environment variables

Copy these to a `.env` file or export them in your shell. **Never commit real
keys.**

```bash
# Azure OpenAI (AI Foundry OpenAI-compatible endpoint)
AZURE_OPENAI_API_KEY=<your-key>
AZURE_OPENAI_ENDPOINT=https://<your-resource>.openai.azure.com/
AZURE_OPENAI_CHAT_DEPLOYMENT=<your-chat-deployment-name>      # e.g. gpt-4o
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small      # must be 1536-dim

# Azure AI Search
AZURE_AI_SEARCH_ENDPOINT=https://<your-resource>.search.windows.net
AZURE_AI_SEARCH_API_KEY=<your-admin-key>
AZURE_AI_SEARCH_INDEX=ai-dlc-dev                              # created automatically
```

If any of these are missing the app still starts but chat and ingestion will
fail at runtime with a 5xx error (not a startup crash in dev mode).

### 2. Start the app

```bash
./gradlew bootRun
```

The default profile is `dev`. H2 is used in-memory — no Postgres required.

The app listens on **http://localhost:8080**.

### 3. Open the chat demo UI

Navigate to **http://localhost:8080/chat** in a browser.

- The chat UI is fully open (no JWT required) when the `prod` profile is not
  active.
- The **Token** field is optional in dev; leave it blank or paste a JWT if you
  want to test per-user document scoping.
- The H2 console is available at **http://localhost:8080/h2-console**
  (JDBC URL: `jdbc:h2:mem:aidlc`, user: `sa`, password: empty).

---

## Running tests

Tests use an in-memory H2 database and mock out Azure OpenAI / Azure AI Search
— **no real Azure credentials are needed**.

```bash
./gradlew test
```

All 46 tests should pass with a clean `BUILD SUCCESSFUL`.

---

## API reference

All endpoints except `/actuator/health`, `/actuator/info`, `/chat`, and
`/api/chat/**` require a Bearer JWT (`Authorization: Bearer <token>`).

In dev mode the app accepts HMAC-256 JWTs signed with the dev key
`dev-only-secret-key-not-for-production-32b!`. In production set
`JWT_ISSUER_URI` to your OIDC issuer.

### Upload a document

```
POST /api/documents
Content-Type: multipart/form-data
Authorization: Bearer <token>

file=<binary>
```

- Accepted file size: up to 2 MB.
- The `sub` claim from the JWT scopes the document to that user — other users
  cannot retrieve it via chat.
- Returns `201 Created` with a JSON body:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "filename": "report.pdf",
  "status": "PENDING_INGEST",
  "uploadedAt": "2026-05-09T10:00:00Z"
}
```

Ingestion runs asynchronously. The document status transitions:

```
PENDING_INGEST → INGESTED
             └→ INGEST_FAILED  (errorMessage field is populated)
```

### Sync chat (JSON response)

```
POST /api/chat
Content-Type: application/json
Authorization: Bearer <token>

{ "question": "What does the report say about Q3 revenue?" }
```

- `question` must not be blank and must be ≤ 2000 characters.
- Returns `200 OK`:

```json
{
  "answer": "According to the report ...",
  "citations": [
    { "source": "report.pdf", "docId": "chunk-uuid" }
  ]
}
```

If no relevant documents are found the model says so in the answer; `citations`
is an empty array.

### Streaming chat (SSE)

```
GET /api/chat/stream?question=<url-encoded-question>
Accept: text/event-stream
Authorization: Bearer <token>   (optional in dev/test)
```

- Tokens are emitted as individual SSE `data:` frames.
- The stream ends when the model finishes. No `[DONE]` sentinel is emitted by
  default.
- Use `fetch()` + `ReadableStream` (not `EventSource`) when you need to pass
  the `Authorization` header from JavaScript.

### Health check

```
GET /actuator/health
```

Returns `200 OK` with `{ "status": "UP" }` — no authentication required.

---

## Production deployment

Set the `prod` Spring profile (`SPRING_PROFILES_ACTIVE=prod`) and provide all
required environment variables:

```bash
# Database (Postgres)
DATABASE_URL=jdbc:postgresql://<host>:5432/<db>
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<password>

# Azure (same keys as dev — all required, no defaults)
AZURE_OPENAI_API_KEY=...
AZURE_OPENAI_ENDPOINT=...
AZURE_OPENAI_CHAT_DEPLOYMENT=...
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=...
AZURE_AI_SEARCH_ENDPOINT=...
AZURE_AI_SEARCH_API_KEY=...
AZURE_AI_SEARCH_INDEX=...

# JWT — REQUIRED in prod; missing this causes a startup failure
JWT_ISSUER_URI=https://<your-oidc-issuer>/
JWT_AUDIENCE=ai-dlc-workshop   # optional, defaults to "ai-dlc-workshop"
```

In prod:
- The `/chat` UI and `/api/chat/**` endpoints **require** a valid Bearer JWT.
- The H2 console is disabled.
- The symmetric HMAC-256 dev decoder is **not** used; all tokens must be
  issued by the configured OIDC provider.

---

## Project layout

```
src/main/java/com/ai_dlc/workshop/
├── ai/           IngestionService, AiConfig (ChatClient + TokenTextSplitter beans)
├── chat/         ChatController (sync), StreamingChatController (SSE),
│                 ChatPageController (Thymeleaf), ChatService, StreamingChatService
├── config/       SecurityConfig (JWT resource server)
├── document/     DocumentController, DocumentService, Document (JPA entity)
└── web/          ProblemDetailControllerAdvice (RFC 9457 error responses)

src/main/resources/
├── db/migration/ Flyway SQL migrations (V1–V3)
├── prompts/      rag-chat.st  (RAG prompt template)
├── static/js/    chat.js      (SSE streaming client)
└── templates/    chat.html    (Thymeleaf demo UI)
```
