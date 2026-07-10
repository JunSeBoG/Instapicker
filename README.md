# Instapicker

A native Android warehouse-picking app. A session is a list of requested products,
each in one of three states — **Pending**, **Removed**, or **Added**. Items move freely
between Pending and Removed; promoting one to **Added** is locked behind a live
**EAN-13 camera scan** that must both be well-formed *and* match the selected product.
Every action lands in an **append-only audit log**, and the exact session — state,
active view, and scan progress — survives process death with no reload.

Built for the *InstaPicker — Senior Android Engineer Challenge*.

---

## Demo

📹 **2-minute walkthrough** — _video link coming soon._

## Screenshots


---

## Build & run

**Requirements**

- Android Studio (latest stable), with the Android SDK.
- **JDK 17** (required by the Android Gradle Plugin).
- `compileSdk 36`, `minSdk 24`.
- A physical device or emulator **with a camera** for the scan flow. Pending ⇄ Removed
  picking works without one.

**From Android Studio**

1. Clone the repo and open the project root; let Gradle sync.
2. Run the `app` configuration on a device/emulator.
3. The camera permission is requested contextually on the first scan, not on launch.

**From the command line**

```bash
git clone https://github.com/JunSeBoG/Instapicker.git
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # install on a connected device
```

**Data.** Products are seeded from
[`app/src/main/assets/seed_products.json`](./app/src/main/assets/seed_products.json)
into Room on first launch — fully offline and deterministic, no backend required. The
seed EAN-13s are checksum-valid, so real scans can be exercised by printing or
displaying the codes. The seed sits behind a `ProductSource` interface, so swapping in
a real backend never touches domain code.

---

## Quick tour

- **Three states & free movement.** Items start in Pending; move any item to Removed
  and back at will. Both directions are logged.
- **The guarded scan.** Open the scanner on a Pending item and scan its barcode. It
  must pass all three checks: 13 digits, a valid EAN-13 checksum, and an exact match to
  the selected product.
- **Stacked items.** A line can require several identical units; each matching scan
  decrements a live `n / total` counter, and the row becomes Added only at zero balance.
- **Soft failure.** A malformed or mismatched scan never changes state — a non-blocking
  banner tells the picker and the attempt is written to the audit log.
- **Rollback.** An Added item can be sent back to Pending or Removed; its scan balance
  is restored.
- **Audit log.** A "Log" action opens the full append-only history — actions, state
  transitions, and validation outcomes, including failures.
- **Resilience.** Kill the app mid-session and relaunch: the same tab, scanner, and
  scan progress come back, read from the database.

---

## Architecture at a glance

Single Gradle module whose packages mirror the target module map, so a later split into
real modules is mechanical:

```
core/model      → pure Kotlin domain models (no Android imports)
core/database   → Room entities, DAOs, mappers
core/scanner    → reusable CameraX + ML Kit capability (no domain knowledge)
feature/picking
  ├─ domain     → PickingReducer, Ean13Validator, state/intent/effect contracts
  ├─ data       → PickingRepository, ProductSource (bundled JSON seed)
  └─ ui         → PickingViewModel, Compose screens
```

One-way MVI: every change goes through a single pure function.

```
UI ──intent──▶ reduce(state, intent) ──▶ (newState, effects)
                                             │
              ViewModel processes intents on one serialized queue:
                1. commit the effects in a single Room transaction
                   (persist the item + append to the audit log)
                2. publish newState ──▶ StateFlow<PickingUiState> ──▶ Compose
```

The reducer has no Android imports, no I/O, and injected time, so every legal and
illegal transition is provable in millisecond JVM tests. Persistence is **write-through**:
the transaction commits *before* the new state is published, so the in-memory state is
never ahead of disk and recovery after process death is simply reading the database.
Ephemeral view state (active tab, whether the scanner or log is open) lives in
`SavedStateHandle` so the exact active view is restored.

### State machine

```
PENDING  ⇄  REMOVED             free, both directions, logged
PENDING  ──▶ ADDED              only via a full EAN-13 scan pass (balance → 0)
ADDED    ──▶ PENDING | REMOVED  rollback; restores the scan balance
```

There is deliberately **no Removed → Added path**: an item must return to Pending before
it can be scanned, keeping the "was it actually on the shelf?" decision explicit.

Full design in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Testing & CI

- **Domain (plain JVM):** the reducer's every branch, the EAN-13 validator, and the scan
  cooldown throttle.
- **Persistence (Robolectric + real Room):** state written through the repository
  survives a brand-new database instance — process death proven, not asserted — and
  re-seeding never overwrites saved progress.
- **UI (Robolectric):** the Compose screen tested as a pure function of state (tab
  boundaries, empty states, scan progress, the soft message, the log overlay), and the
  ViewModel's active-view restore from `SavedStateHandle`.

GitHub Actions runs four checks on every push and PR — **lint, unit tests, build, and
detekt** ([`.github/workflows/ci.yml`](./.github/workflows/ci.yml)).

---

## Part C — Reflective analysis

### 1. Architectural trade-offs

The deliberate compromises — each with the alternative I rejected and why — are written
up in [`TRADEOFFS.md`](./TRADEOFFS.md). The short version: one atomic row per product
with a scan counter instead of one row per unit; a single Gradle module instead of many;
and a bundled JSON seed behind a `ProductSource` interface instead of a real backend.

With an extra week I'd evolve it in this order: promote the packages into real Gradle
modules so dependency direction is enforced at compile time; add the per-unit stacking
model (so "1 added, 2 pending" for the same product can be shown); add an instrumented
process-death test (`am kill`) to back up the JVM-level resilience proofs; and put a real
product service behind the existing `ProductSource` seam, with sync and conflict handling.

### 2. Data integrity & security

Honestly, the persistence layer is built to survive *accidents* — crashes, calls,
process death — not a *malicious user*. Room is plain SQLite, so on a rooted device
someone can open the database, fake an Added state without ever scanning, or delete
audit rows. Append-only is enforced in the DAO (insert and read only, no update or
delete), not cryptographically.

To harden it for an enterprise rollout I'd work in layers: encrypt the database at rest
(SQLCipher, with the key in the Android Keystore); make tampering *detectable* by
hash-chaining the audit log, so each entry covers the previous entry's hash and any edit
or deletion breaks the chain; and, most importantly, stop trusting the client — the
server holds the real session, the device sends signed scan events, and any impossible
transition is rejected and flagged server-side.

### 3. Production observability

The architecture already gives me the hooks, because every change is one intent through
one reducer and every outcome is already an audit entry. For 5,000 pickers I'd add:

- **State-transition events** — report each transition (intent, from → to, outcome). If
  an *impossible* one ever shows up (Removed → Added, a negative balance), that's an
  automatic alert — a state regression caught remotely before anyone files a bug.
- **Scan metrics** — scan-open → detection latency, and the pass / mismatch / format-fail
  rates broken down by device model, to catch bad barcode data or a decoder regression on
  specific hardware.
- **Restore metrics** — how often a session restores correctly after process death, and
  how long it takes; that's the direct health signal for this app's hardest requirement.
- **The usual pipelines** — crash and ANR reporting, plus traces around the scan pipeline
  and the Room writes.

### 4. AI synergy

Both, honestly. It clearly sped up the parts that are just *specification* — scaffolding,
Compose boilerplate, and especially the reducer's test matrix. The friction showed up
exactly on platform lifecycles: the agent "released the camera" but leaked the ML Kit
detector's native handle, and it quietly drifted from the repo's own PR template. Both
are written up in [`AI_LOG.md`](./AI_LOG.md), and both were slips of *context*, not
competence.

What worked was treating the AI like a constrained junior: [`AGENTS.md`](./AGENTS.md)
pins the invariants, and nothing merges without tests and a review against those rules.
One case stood out — an independent model review of the submission caught a spot where my
own docs claimed write-through persistence while the code was still emitting state before
persisting; a good reminder that a design doc is a claim to verify, not evidence. Net: a
real accelerator for output, with the engineering judgment — lifecycle boundaries,
state-machine invariants, and what *not* to build — staying firmly human.

---

## Repository guide

| Document | Purpose |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | The design framework: guiding principles, state model, persistence strategy, delivery plan. First commit on `main`. |
| [`AGENTS.md`](./AGENTS.md) | The AI collaboration contract: system prompt, hard constraints, templates, review gate. |
| [`AI_LOG.md`](./AI_LOG.md) | Where the AI was wrong and how it was corrected. |
| [`TRADEOFFS.md`](./TRADEOFFS.md) | Deliberate design trade-offs and their rejected alternatives. |
| [`docs/prompts/context-schema.md`](./docs/prompts/context-schema.md) | Compact domain schema used to anchor AI sessions. |
