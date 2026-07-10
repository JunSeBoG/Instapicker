# Context Schema — Instapicker

A compact, structured anchor of the domain, meant to be prepended to an AI session
so the agent reasons against the real invariants instead of guessing. The full prose
lives in [`ARCHITECTURE.md`](../../ARCHITECTURE.md); the behavioural rules for the
agent live in [`AGENTS.md`](../../AGENTS.md). This file is the machine-friendly
reference.

## Entities
```
PickState = PENDING | REMOVED | ADDED

PickItem {
  id: String
  name: String
  ean13: String            # the expected barcode
  requestedQty: Int (>= 1) # units in the stack
  remainingToScan: Int     # 0..requestedQty; reaching 0 => ready for ADDED
  state: PickState
}
```

## State machine
```
PENDING  <->  REMOVED                       # free, both directions, logged
PENDING  --(scan passes, balance hits 0)-->  ADDED
ADDED    --(rollback)-->  PENDING | REMOVED  # restores the full balance

# No REMOVED -> ADDED. A Removed item must return to PENDING before it can be scanned.
# Failed/partial scan: NO state change; soft notification; logged validation outcome.
```

## Scan validation (all three required to pass)
```
1. exactly 13 numeric digits
2. valid EAN-13 checksum
3. exact match to the selected item's ean13
```

## MVI contract
```
intent -> reduce(state, intent) -> Reduction(newState, effects)

# reduce() is PURE: no Android, no I/O, no coroutines; time is injected.
```

## Persistence & resilience
```
Room            = single source of truth for pick_items + append-only audit_log
write-through   = mutate Room in a transaction BEFORE emitting new state
restore         = read the DB (never replay a cache)
SavedStateHandle = active tab + scanner-open-for-item (the exact active view)
```

## Invariants (must always hold)
```
requestedQty >= 1
remainingToScan in 0..requestedQty
ADDED reachable only from PENDING, only via a full scan pass
audit log is insert-only (append-only)
```
