# AGENTS.md

Rules for AI agents working in this repo. Plain language where possible, but use the right technical terms when they matter.

## Before You Start

1. Read the existing code first. Match its style, naming, and folder structure.
2. Do only what was asked. Do not expand scope on your own.
3. Check the README and docs for project limits and assumptions.

## Design Principles

Follow solid software design. Keep it practical, not overbuilt.

1. **Single responsibility**  
   Each class or module should do one job. APIs handle requests, services hold business logic, repositories talk to the database.

2. **Separation of concerns**  
   Do not mix layers. Business rules do not belong in controllers, handlers, or UI components.

3. **DRY (Don't Repeat Yourself)**  
   Reuse existing code. Copy-paste the same logic in multiple places is a smell.

4. **KISS (Keep It Simple)**  
   The simplest correct solution wins. Avoid extra abstractions "for the future."

5. **Open for extension, closed for modification**  
   Prefer extending behavior over rewriting working code unless the task requires it.

6. **Dependency direction**  
   High-level logic should not depend on low-level details. Depend on interfaces or abstractions where the project already uses them.

## Edge Cases and Concurrency

Always think about what can go wrong before you implement.

1. **List edge cases first**  
   For every feature, ask: empty input? invalid input? duplicate request? expired state? user not allowed? resource already taken?

2. **Discuss approaches when it matters**  
   For non-trivial problems (especially concurrency, payments, booking, shared resources), briefly lay out options and tradeoffs before coding. Example: optimistic locking vs pessimistic locking vs database constraints.

3. **Concurrency**  
   When two or more users or processes can touch the same data at the same time, design for it explicitly. Use transactions, row locks, unique constraints, or idempotency keys as needed. Never assume "this will probably not happen at the same time."

4. **Idempotency**  
   Retries and double-clicks happen. Confirming a payment or creating a booking twice should not create duplicate side effects.

5. **State machines**  
   For flows with steps (hold, pay, confirm, cancel), define valid transitions clearly. Reject invalid states early.

6. **Document assumptions**  
   If you chose one approach over another, note why in the README or in a short comment where the decision lives.

## Error Handling

Treat errors as part of the feature, not an afterthought.

1. **Fail fast on bad input**  
   Validate early. Return clear validation messages.

2. **Use the right HTTP status codes**  
   400 for bad request, 401/403 for auth issues, 404 when not found, 409 for conflicts (e.g. seat already booked), 500 only for unexpected server failures.

3. **Structured error responses**  
   Return a consistent shape: error code, message, and optional field-level details. Help the caller fix the problem.

4. **Do not swallow exceptions**  
   Never catch an error and silently continue. Log it, wrap it, or rethrow with context.

5. **Handle errors at boundaries**  
   Catch and translate errors in service or controller layers. Do not scatter try/catch in every function.

6. **User-safe messages**  
   Tell the user what went wrong in plain language. Do not leak stack traces or internal details in API responses.

7. **Security-aware errors**  
   For unauthorized access to another user's resource, prefer 404 over 403 when exposing existence would be a leak.

## How to Write Code

1. **Readable first**  
   Clear names beat clever one-liners. Code should explain what it does.

2. **Comments where needed**  
   Comment non-obvious business rules, concurrency choices, workarounds, and "why" decisions. Do not comment obvious code.

3. **Match the codebase**  
   Same formatting, imports, patterns, and test style as the rest of the project.

4. **Types and validation**  
   Use strong types where the language supports them. Validate all external input.

5. **No secrets in code**  
   Passwords, tokens, and API keys go in environment variables or config, never committed to git.

6. **Async side effects**  
   Email, SMS, notifications, and heavy I/O should run in the background. Do not block the main user flow.

## Testing

1. Test critical paths: happy path, main failure cases, and concurrency or conflict scenarios where relevant.
2. Add tests when requested or when they protect real behavior.
3. Skip trivial tests that only restate the obvious.

## Git

1. Small, logical commits with clear messages (`feat:`, `fix:`, `test:`, `docs:`).
2. Do not commit unless the user asks.
3. Do not force-push to main or master.

## When You Are Not Sure

1. Prefer the simpler option that meets the requirement.
2. State your assumption in the README and proceed.
3. Ask the user only when the decision truly needs their input.

## Do Not

1. Add features, docs, or tests nobody asked for.
2. Add new libraries without a strong reason.
3. Over-engineer for hypothetical future needs.
4. Change unrelated files while fixing one issue.
5. Skip edge case thinking because the happy path "works."
