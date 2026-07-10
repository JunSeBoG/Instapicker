# Instapicker — Design Trade-offs (working log)

> Running log of the deliberate trade-offs made while designing Instapicker. These
> feed the **"Architectural Trade-offs" reflective answer in `README.md` (Part C, Q1)**.
> Working notes — not necessarily committed.
>
> Format per entry: **Decision · What we chose · Why · What we gave up · With more time.**

---

## Domain trade-offs

### 1. Stack representation: one atomic row vs one row per unit

**Decision:** how a multi-unit product (e.g. 3 identical milk cartons) is stored,
scanned, and displayed.

**Chosen — Option A (atomic row + counter).** One row per *product*
(`PickItem` with `requestedQty` and `remainingToScan`). The whole row has a single
state. Each valid matching scan decrements `remainingToScan`; the row flips to
ADDED only when it reaches 0. Pending ⇄ Removed moves the whole row at once.

**Why:** the state machine stays a simple, fully-testable function; the UI is a 1:1
map of rows; partial progress lives in one field (`remainingToScan`) that persists
to Room, so it survives process death. Predictable and cheap to prove correct.

**What we gave up:** partial availability *within* a product. You cannot show
"1 added, 2 pending" for the same product — the row is atomic. A half-scanned stack
sits in PENDING showing "1 of 3", and only the whole row moves to ADDED.

**With more time — Option B (one row per unit + UI grouping).** Store N rows, one
per unit: each has a unique `id`, a shared `ean13`, and its own `state` (no
counter). A scan flips *one* pending unit of that `ean13` to ADDED. The UI groups
rows by `(ean13, state)` and shows a count, so "Leche ×3" in Pending becomes
"Leche ×2" in Pending + "Leche ×1" in Added after one scan. This models real
partial availability naturally.

**Why we did NOT do B now — the added complexity concentrates in three places:**
- **UI aggregation layer.** No longer a 1:1 row map; you compute groups by
  `(ean13, state)` with counts, and stable keys become the group key, not an id.
- **Interaction → unit mapping.** The scanner targets a *group* (an `ean13`), so on
  a match the reducer must pick which pending unit to flip, and handle "no pending
  units left".
- **Move/remove semantics become ambiguous.** "Remove" on a group of 3 — all three?
  one? This re-introduces the "how many?" question that Option A removes, forcing
  either all-or-nothing or per-quantity UI controls.

**Other downsides of B:** a product can appear in several tabs at once (harder keys
/ animations); row count scales with total units, not distinct products; larger
test surface (grouping, unit selection, group actions) — more places for bugs, in
the UI/interaction layer that's hardest to test in a 3-day window.

**Verdict:** A is the right call for the deadline (simple, predictable, proven). B
is a legitimate, well-understood evolution — chosen against, not overlooked.

### 2. Scan progress resets when a row leaves Pending

- **Decision:** what happens to a half-scanned balance when a row is moved.
- **Chosen:** moving Pending → Removed (and back) resets `remainingToScan` to
  `requestedQty`.
- **Why:** avoids "ghost" progress on a row pulled off the shelf; re-scanning from
  zero is predictable and fully auditable.
- **Gave up:** preserving partial scan progress across a move.
- **With more time:** tie this to Option B, where per-unit state makes the reset moot.

---

## Architecture trade-offs

### 3. Single module with package structure, not true multi-module
- **Chosen:** one app module whose packages mirror the target module map
  (`core/model`, `core/database`, `core/scanner`, `feature/picking/...`).
- **Why:** faster builds and far less wiring for a 3-day build; the package layout
  makes a later migration to real modules mechanical.
- **Gave up:** compile-time enforcement of the dependency direction.
- **With more time:** promote each package group to its own module.

### 4. No use-case layer
- **Chosen:** business logic lives in the reducer and validator; the ViewModel calls
  the repository directly.
- **Why:** the would-be use cases are thin pass-throughs; adding them is ceremony.
- **Gave up:** a formal orchestration seam.
- **With more time:** introduce a use case only where real orchestration appears.

---

## Scope & security trade-offs

### 5. Bundled JSON seed instead of a real backend
- **Chosen:** products are seeded from a bundled JSON asset behind a `ProductSource`
  interface.
- **Why:** offline, deterministic, reviewable; swapping to a real API is a one-file
  change that never touches domain code.
- **With more time:** a real product service behind the same interface.

### 6. Client-side persistence only
- **Chosen:** Room on-device is authoritative for this challenge.
- **Gave up:** tamper-resistance — a rooted device can edit the SQLite file and fake
  an ADDED state without scanning.
- **With more time:** SQLCipher-encrypted DB + HMAC-signed session state (keys in the
  Android Keystore) + a hash-chained audit log; ultimately, a server-authoritative
  session so the client is never trusted.

### 7. On-device ML Kit over a custom scanner
- **Chosen:** ML Kit (Play Services) for EAN-13 decoding.
- **Why:** faster to a robust result; offline; the `:core:scanner` seam keeps it
  swappable (e.g. for a hardware laser scanner).
- **With more time:** pluggable decoder abstraction with a hardware-scanner backend.
