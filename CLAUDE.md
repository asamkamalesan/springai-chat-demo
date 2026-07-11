# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Use the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows/PowerShell).

- Build: `./mvnw clean package`
- Run the app: `./mvnw spring-boot:run` (serves on port 8080)
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=SpringaiChatDemoApplicationTests`
- Run a single test method: `./mvnw test -Dtest=SpringaiChatDemoApplicationTests#methodName`

Endpoints (once running). The `/chat*` endpoints require an `Authorization: Bearer <token>` header matching `app.auth.token` (see AuthTokenFilter); `/actuator` is open.
- `GET http://localhost:8080/chat?message=<text>` — synchronous; returns the full reply as plain text.
- `GET http://localhost:8080/chat/stream?message=<text>` — streams the reply token-by-token as Server-Sent Events (`text/event-stream`).
- Actuator endpoints are exposed under `/actuator`.

## Architecture

Spring Boot 3.5 + Spring AI 1.1 app (Java 21) that exposes a single-turn chat endpoint backed by Anthropic Claude.

- `SpringaiChatDemoApplication` — standard Spring Boot entry point.
- `config/ChatClientConfig` — builds the singleton `ChatClient` bean from the auto-configured `ChatClient.Builder`, setting the default system prompt. This is the place to change global chat behavior (system prompt, default options, advisors).
- `controller/ChatController` — REST controller. `/chat` calls the model synchronously via `chatClient.prompt().user(message).call().content()` and lets exceptions propagate to the global handler. `/chat/stream` returns a `Flux<String>` via `.stream().content()` as SSE; since the 200 response has already begun when errors surface mid-stream, they are mapped to a final text chunk via `onErrorResume` rather than an HTTP status.
- `exception/GlobalExceptionHandler` — `@RestControllerAdvice` returning RFC 7807 `ProblemDetail` responses: `NonTransientAiException` (auth/billing/invalid-request) → 400 with the API message; any other `Exception` → 500 with a generic message (details are logged, not returned). Covers the synchronous `/chat` path only — streaming errors are handled reactively in the controller.
- `security/AuthTokenFilter` — `OncePerRequestFilter` guarding the `/chat` endpoints with a static shared-secret bearer token. Clients send `Authorization: Bearer <token>`; it's compared (constant-time) against the `app.auth.token` property. `shouldNotFilter` skips non-`/chat` paths so actuator/health stays open. Fails closed: if `app.auth.token` is blank, all chat requests get 401. Runs before Spring MVC, so it writes its own `ProblemDetail` 401 rather than going through `GlobalExceptionHandler`. Planned evolution: swap the static comparison for JWT validation (Spring Security OAuth2 resource server with an issuer/JWKS).

The Anthropic model, temperature, and max-tokens are configured in `application.yml` under `spring.ai.anthropic`. The `spring-ai-starter-model-anthropic` starter auto-configures the `ChatClient.Builder` from these properties — there is no manual client wiring.

Note: config lives in `application.yml`; the original `application.properties` was removed in favor of it.

### API key / local config

The API key resolves in this order:
1. **AWS Secrets Manager** — imported via `spring.config.import: optional:aws-secretsmanager:springai-chat-demo/anthropic`. Store the secret as JSON containing the property key, e.g. `{ "spring.ai.anthropic.api-key": "sk-ant-..." }`. Region comes from `spring.cloud.aws.region.static` (`AWS_REGION` env var, default `ap-south-1`); AWS credentials use the default provider chain (see below).
2. `SPRING_AI_ANTHROPIC_API_KEY` environment variable (fallback in `application.yml`, empty default).
3. `config/local.yml` — a gitignored local override, imported via a **commented-out** line in `application.yml`. Uncomment `- optional:file:./config/local.yml` to use a local key instead of Secrets Manager (e.g. running without AWS credentials).

The `optional:` prefixes mean the app still starts if a source is absent, falling back to the next. Never commit a real key to `application.yml` or `config/local.yml`.

### AWS credentials

The app never hardcodes AWS credentials — the AWS SDK's default provider chain resolves them from the environment, so the same build runs everywhere. Only the region and secret name live in `application.yml`. The secret read requires the `secretsmanager:GetSecretValue` permission on `arn:aws:secretsmanager:ap-south-1:<ACCOUNT_ID>:secret:springai-chat-demo/anthropic-*` (managed by the `springai-chat-demo-secret-read` policy).

**Local dev (CLI / access key):** configure a dedicated IAM user's access key once — never root, never committed:

```bash
aws configure   # Access key ID, Secret access key, region=ap-south-1, output=json
```

This writes `~/.aws/credentials`; the SDK picks it up automatically. Verify with:

```bash
aws sts get-caller-identity                                    # confirms the IAM principal (not root)
aws secretsmanager get-secret-value --secret-id springai-chat-demo/anthropic --region ap-south-1
```

**ECS (task role — no access keys):** do **not** put access keys in the image, env vars, or task definition. Attach the `springai-chat-demo-secret-read` policy to the **task role** (`taskRoleArn`) — that is the identity the app assumes at runtime, and the SDK resolves it automatically via the ECS container-credentials endpoint. No code or config change from local dev. In the task definition, set `AWS_REGION` (or deploy in `ap-south-1`) so the region resolves:

```json
{
  "taskRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/springai-chat-demo-task",
  "executionRoleArn": "arn:aws:iam::<ACCOUNT_ID>:role/ecsTaskExecutionRole",
  "containerDefinitions": [{
    "name": "springai-chat-demo",
    "image": "<ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/springai-chat-demo:latest",
    "environment": [{ "name": "AWS_REGION", "value": "ap-south-1" }]
  }]
}
```

The **task role** is what the app uses to read the secret (attach the policy here). The **task execution role** is used by the ECS agent to pull the image and write logs — it only needs secret permission if you switch to ECS-native secret injection (the task definition `secrets` block mapping the secret to `SPRING_AI_ANTHROPIC_API_KEY`), which is an alternative to the Spring Cloud AWS import above.
