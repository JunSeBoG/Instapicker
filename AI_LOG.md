# AI_LOG.md — Human ↔ Machine Friction Journal

A living record of moments where an AI coding agent proposed a flawed, unsafe, or
sub-optimal implementation, why it was wrong, and how I took control. The point of
this file is to demonstrate **judgment over autocomplete**: generative tooling
accelerates output, but the engineer owns correctness. The rules the agents are
expected to follow live in [`AGENTS.md`](./AGENTS.md).

Each entry follows the same shape:

> **Context** → **AI proposal** → **Why it was flawed** → **My correction** → **Lesson.**

---

## Entries

### Entry 1 — PR description ignored the repo's own template

**Context.** Wrapping up the `feature/picking-ui` branch, I asked the agent for a
pull-request description. The repo ships a fixed PR template
([`.github/pull_request_template.md`](./.github/pull_request_template.md)) whose
sections `AGENTS.md` (§4) explicitly tells agents to follow.

**AI proposal.** It produced a description with invented sections — `What`,
`Screenshots`, `Out of scope` — dropping `Why`, `How`, and the `Checklist`, and
reordering the rest.

**Why it was flawed.** It violated the "follow the defined markdown templates"
rule in `AGENTS.md` §4. A PR that doesn't match the template loses the checklist
gate (reducer-only state changes, no new deps/modules, docs updated) and forces a
reviewer to reconcile two different shapes. It also revealed the agent had stopped
re-reading `AGENTS.md` before generating artifacts, exactly the failure mode the
file exists to prevent.

**My correction.** I caught the drift, had it re-read `AGENTS.md` and the live
template, and regenerated the description in the real order — `What / Why / How /
Testing / Checklist` — folding the screenshots into `How` instead of a new
top-level section.

**Lesson.** Templates are a hard constraint, not a starting point the agent may
"improve." Re-read `AGENTS.md` and the referenced templates immediately before
generating any commit or PR, and diff the output against the template shape before
trusting it.

### Entry 2 — leaked the ML Kit detector's native resources

**Context.** Building the camera scanner (`core/scanner`). The agent wrote a
`CameraBarcodeScanner` composable that binds CameraX and, per its own doc comment,
"releases the hardware immediately" in `onDispose` — it unbound the camera and shut
down the analysis executor there.

**AI proposal.** For each scan session it created a fresh ML Kit detector inside the
bind step — `BarcodeScanning.getClient(...)` — but never closed it:

```kotlin
.also { it.setAnalyzer(executor, BarcodeAnalyzer(onBarcode = onBarcode)) }
// ...onDispose { provider?.unbindAll(); executor.shutdown() }  // detector never closed
```

**Why it was flawed.** `BarcodeScanner` is `Closeable` and holds native resources.
Unbinding the camera does not release the detector, so every open/close of the
scanner leaked one. This breaks `AGENTS.md` §3.6 ("camera resources are lifecycle-
bound and explicitly released") in spirit — the agent released the obvious resource
(the camera) and quietly missed the less obvious one (the ML Kit client).

**My correction.** I made `BarcodeAnalyzer` implement `Closeable` and forward
`close()` to the detector, hoisted the analyzer to a remembered instance so I hold a
reference, and closed it in the same `onDispose` that frees the camera:

```kotlin
override fun close() { scanner.close() }
// onDispose { provider?.unbindAll(); executor.shutdown(); analyzer.close() }
```

I also wrapped the callback in `rememberUpdatedState` so the remembered analyzer
never calls a stale lambda.

**Lesson.** "Released the camera" is not the same as "released everything the camera
pipeline created." Audit every `Closeable`/native handle a feature allocates — not
just the headline one — and tie each to the same lifecycle boundary.

### Entry 3 — documented a stronger persistence guarantee than the code delivered

**Context.** `AGENTS.md` and `ARCHITECTURE.md` state the persistence model plainly:
write-through — "domain mutations hit Room in a transaction BEFORE the new state is
emitted", and "restoration = read the DB, never replay a cache".

**AI proposal.** The `PickingViewModel.dispatch` the agent wrote does the opposite of
what it documented: it emits the reducer's new state immediately (optimistically) and
runs the persistence effects *afterwards*, in a fresh coroutine per dispatch — not
inside a transaction and not serialized — while `items` is also merged from the Room
flow, giving the field two writers.

```kotlin
_state.value = reduction.state              // optimistic emit FIRST
viewModelScope.launch { effects.forEach ... } // persist LATER, async, no transaction
```

**Why it was flawed.** It contradicts the guarantee the docs advertise. A process
death in the window between the emit and the persist would drop the last mutation
(e.g. a scan), and two rapid intents could run their effects out of order. An
independent model review of the submission flagged exactly this gap — a good reminder
that self-authored architecture docs are claims to be verified, not evidence.

**My correction.** Reconcile the implementation to the documented contract: run the
effects (persisting inside a `withTransaction`) and let `items` derive **only** from
the Room flow — the reducer keeps owning the ephemeral view bits (scanner, message,
tab, log) — with effects pushed through a single serialized queue so ordering is
deterministic. (The existing repository fake already models this write-through, so the
production path is being aligned to what the tests already assume.)

**Lesson.** A design document is a promise, and the ViewModel is where that promise is
kept or broken. When the docs claim "persist before emit", the dispatch loop must
actually await the write before publishing state — and a second, independent reviewer
(human or model) is worth its weight for catching doc-vs-code drift the author is blind to.

<!--
Template for a new entry:

### Entry N — <short title>

**Context.** <what we were building>

**AI proposal.** <what the agent suggested, with a snippet if useful>

**Why it was flawed.** <the concrete problem: broken invariant, lifecycle leak,
spec error, etc.>

**My correction.** <what I changed instead, with a snippet>

**Lesson.** <the takeaway; often pinned by a test so it can't regress>
-->
