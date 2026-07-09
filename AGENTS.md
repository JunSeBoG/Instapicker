# AGENTS.md — AI Collaboration Configuration

This file is the **control surface** between me and the AI coding
agents used to build Instapicker. It is committed to the repository so a reviewer
can see *how* the AI was steered — not just what it produced. The premise: a senior
engineer **anchors and constrains** generative tooling, reviews every output against
these rules, and rejects anything that violates them. See [`AI_LOG.md`](./AI_LOG.md)
for concrete instances where the AI was overruled.

---

## 1. Agent roles

| Agent | Scope | Boundary |
|---|---|---|
| **Architect agent** | Reason about state model, module boundaries, trade-offs. | Produces proposals only. No code lands from this role without a matching test. |
| **Implementer agent** | Write Kotlin/Compose against an already-agreed interface. | Must not invent public API surface; must not add dependencies. |
| **Test agent** | Write JUnit / Compose tests for the reducer, validator, persistence. | Tests are authored/reviewed *before* the implementation is accepted. |

---

## 2. System prompt (verbatim anchor passed to every session)

```
You are assisting a Senior Android Engineer building Instapicker, a native
warehouse-picking app. It must excel at STATE PREDICTABILITY, RESILIENCE TO
PROCESS DEATH, HARDWARE DISCIPLINE, and DELIBERATE ENGINEERING TRADE-OFFS.

PROJECT SHAPE:
- Single Gradle module. Packages mirror the target module map
  (core/model, core/database, core/scanner, feature/picking/{domain,data,ui}).
- Do NOT create new Gradle modules. Do NOT add a use-case layer — the ViewModel
  talks to the repository directly.

NON-NEGOTIABLE ARCHITECTURE:
- Kotlin, Jetpack Compose, MVI with a PURE reducer: (state, intent) -> (newState, effects).
- The reducer contains NO Android imports, NO I/O, NO side effects. Effects are
  data returned to the ViewModel, which executes them. If you put persistence,
  logging, camera, or coroutines INSIDE the reducer, you are wrong — stop.
- Single immutable UiState exposed as StateFlow. UI is a pure function of state.
- Room is the single source of truth for domain state AND the append-only audit
  log. Ephemeral view state (active tab, whether the scanner is open for an item)
  lives in SavedStateHandle, so the EXACT active view is restored after process death.
- Persistence is WRITE-THROUGH: domain mutations hit Room in a transaction before
  the new state is emitted. Restoration = read the DB, never "replay a cache".

BARCODE / STATE RULES:
- CameraX for lifecycle-correct camera; ML Kit restricted to FORMAT_EAN_13.
- Added is reachable ONLY from Pending, ONLY via a scan that passes all three:
  13 numeric digits + valid EAN-13 checksum + exact match to the selected
  product's ean13.
- A Removed item CANNOT be scanned — it must be moved back to Pending first.
- Anything short of a full pass => NO state change, a soft non-blocking
  notification, and a LOGGED validation failure.
- Stacked items: each matching scan decrements remainingToScan; Added only when
  it reaches 0. Added -> Pending/Removed rollback is a first-class intent.

CODE STANDARDS:
- Kotlin coroutines with structured concurrency; never GlobalScope.
- Camera/ImageAnalysis MUST be lifecycle-bound and released; back-pressure with
  STRATEGY_KEEP_ONLY_LATEST; stop analysis after first valid match.
- No blocking calls on the main dispatcher. No memory leaks of Context/Activity.
- Immutable data classes; stable keys in LazyColumn.
- Call functions and constructors with named arguments (`param = value`), not
  positionally — call sites stay self-documenting and safe from argument-order slips.
- Every reducer branch and every validator branch must have a unit test.
- Code must pass detekt (static analysis) against the committed baseline; the full
  CI (lint, unit tests, build, detekt) must be green.

OUTPUT DISCIPLINE:
- Prefer the smallest change that satisfies an agreed interface.
- Do NOT add dependencies, change public API, or introduce a new architectural
  pattern — a Gradle module, or a use-case layer — without explicitly flagging it
  and waiting for approval.
- When unsure about a lifecycle or threading detail, say so and cite the risk —
  do not guess silently. Legacy patterns (AsyncTask, LiveData-as-event-bus,
  findViewById, Loader) are FORBIDDEN.
```

---

## 3. Technical constraints (hard rules the agent must never break)

1. **Reducer purity.** Zero side effects, zero Android/IO inside the reducer.
   Violations are auto-rejected in review.
2. **Single module.** No new Gradle modules; the package layout mirrors the target
   module map and migration to real modules is left for later.
3. **No use-case layer.** The ViewModel calls the repository directly; thin
   pass-through interactors are ceremony and are rejected.
4. **No new dependencies** without justification in the PR description.
5. **No `GlobalScope`, no `runBlocking` on main, no `AsyncTask`, no `LiveData`
   event hacks.**
6. **Camera resources are lifecycle-bound and explicitly released.**
7. **Added is reachable only from Pending**, only via a passing EAN-13 scan; a
   Removed item cannot be scanned.
8. **Audit DAO is insert-only.** Any generated `@Update`/`@Delete` on the log is a
   red flag and must be removed.
9. **State restoration must be proven by a test**, not asserted in prose.
10. **detekt must pass** against the committed baseline; CI must be green before merge.

---

## 4. Markdown / artifact templates the agent must follow

**Commit message template**
```
<type>(<scope>): <imperative summary>

- what changed and why (state-machine impact if any)
- tests added/updated
```
`type ∈ {feat, fix, test, refactor, docs, chore}`

**Reducer test template**
```kotlin
@Test fun `<given> when <intent> then <state assertion>`() { /* pure, no Android */ }
```

**Pull request description template** (the live copy is at
[`.github/pull_request_template.md`](./.github/pull_request_template.md), which
GitHub pre-fills on every PR)
```
## What
<summary of the change>

## Why
<motivation / the requirement it addresses>

## How
<approach; state-machine impact; key decisions>

## Testing
<unit/UI coverage; how to reproduce>

## Checklist
- [ ] CI green (lint, unit tests, build, detekt)
- [ ] Tests cover new branches
- [ ] State changes go only through the reducer
- [ ] No new dependency / Gradle module / use-case layer (or justified)
- [ ] Docs / AI_LOG updated if relevant
```

**AI_LOG entry template**
```
### Entry N — <short title>
**Context** → **AI proposal** → **Why it was flawed** → **My correction** → **Lesson**
```

---

## 5. Review gate (how AI output is accepted)

An AI-generated change is merged only when it:
1. compiles and passes CI (lint, unit tests, build, detekt);
2. touches state only through the reducer;
3. ships with tests covering new branches;
4. adds no unapproved dependency, public API, Gradle module, or use-case layer;
5. respects camera/threading lifecycle rules.

If any check fails, the diff is corrected by hand and the reasoning recorded in
[`AI_LOG.md`](./AI_LOG.md).

---

## 6. Companion files

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — the design framework these rules enforce.
- [`AI_LOG.md`](./AI_LOG.md) — where the AI was wrong and how I corrected it.
- [`docs/prompts/context-schema.md`](./docs/prompts/context-schema.md) — a compact,
  structured schema of the domain (entities, state machine, intents/effects,
  invariants) fed to the AI to anchor its context.
- [`.github/pull_request_template.md`](./.github/pull_request_template.md) — the PR
  template GitHub pre-fills.
