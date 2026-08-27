# Codefront Mindustry Roadmap

## Phase 0 — Platform feasibility

### CF-MIND-AUD-001

Read-only/source-backed audit of Mindustry v159.7 APIs and runtime behavior needed for Codefront.

Exit: PASS/PARTIAL/FAIL evidence for every acceptance item in `docs/audits/CF-MIND-AUD-001.md`.

### CF-MIND-SPIKE-001

Minimum automated 1v1:

- official test arena;
- two fixed mirrored bases;
- opposing teams;
- minimal defense variation;
- autonomous units;
- minimal processor logic;
- manual intervention disabled;
- core destruction => winner;
- reset and second match.

No production UI.

## Phase 1 — Loadout foundation

### CF-MIND-SPIKE-002 — DefenseLayout

- declarative defense placements;
- legal-zone validation;
- whitelist;
- DP budget;
- canonical representation/hash.

### CF-MIND-SPIKE-003 — RobotDeck

- unit whitelist;
- RP costs;
- spawn policy;
- ownership enforcement.

### CF-MIND-SPIKE-004 — Programs

- processor/program import;
- program validation limits;
- CP budget;
- ownership/safety restrictions.

## Phase 2 — MVP

Deliver the complete loop:

`Workshop/Loadout → Validate → Lock → Match → Result`

MVP includes:

- 1v1 only;
- fixed symmetric bases;
- defense customization;
- robot deck;
- mlog programs;
- authoritative server result;
- versioned ruleset;
- repeatable match reset.

Explicitly exclude matchmaking/rating/accounts unless required to prove the loop.

## Phase 3 — Competitive hardening

- exploit/geometry hardening;
- deterministic canonical hashing;
- result records and battle logs;
- server performance limits;
- seasonal version pinning;
- balance telemetry;
- tournament runner.

## Phase 4 — Team mode

### 2v2

- two PlayerSlots per team;
- shared team objective/core;
- individual player DP/RP/CP budgets;
- ownership isolation;
- map geometry designed specifically for team play;
- bounded TeamBus for program-to-program coordination;
- team-specific balance and unit-cap testing.

Only after stable 1v1.

## Phase 5 — Optional evolution

Candidates, not commitments:

- richer TeamBus protocols;
- 3v3+ experiments;
- custom Codefront programming language compiled to mlog;
- seasons/rating/matchmaking;
- replay/spectator tooling;
- additional robot/defense content.
