# Codefront Ruleset v1 — Draft Contract

Status: **DRAFT / values TBD**

This file defines the shape of the first competitive ruleset. Numeric balance values remain intentionally unset until platform feasibility is proven.

## Platform pin

- Mindustry build: `v159.7`
- Java runtime for development/server: JDK 17
- Initial server architecture: x86_64
- Codefront ruleset id: `CF-RULESET-001`

## Match format

- Teams: 2
- Player slots per team: 1 for MVP
- Future target: 2 slots per team without schema redesign
- Human control after start: prohibited
- Winner: opposing Team Core destroyed
- Timeout/tiebreaker: TBD

## Arena

- Symmetric official arena
- Fixed base template supplied by ruleset
- Legal defense zones supplied by ruleset
- Player cannot edit fixed base infrastructure
- Player cannot build outside legal zones

## Economy

Disabled as a player strategy for MVP:

- mining;
- resource production;
- conveyor logistics;
- ammo supply chains;
- economic expansion.

Any engine-level resource requirements necessary for a chosen weapon must be normalized or abstracted by Codefront so equivalent selected defenses can operate without player logistics.

## Budgets

- Defense Points (DP): TBD
- Robot Points (RP): TBD
- Compute Points (CP): TBD
- wall/path-shaping allowance: TBD

Budgets are validated pre-match and are not an in-match currency.

## Defense policy

Ruleset owns explicit block whitelist/blacklist and per-type/count limits.

Validator must reject illegal coordinates, overlapping/invalid placements, forbidden configurations and structures intended solely to break required navigation assumptions.

## Robot policy

Ruleset owns unit whitelist and RP costs.

Runtime spawn/reinforcement policy: TBD after the first feasibility spike.

Programs may control only the ownership scope allowed by the ruleset.

## Logic policy

Native mlog is allowed for early prototypes.

Competitive restrictions are TBD pending audit of:

- controllable APIs/instructions;
- time access;
- RNG;
- sensing scope;
- cross-team/allied control;
- instruction/code limits;
- network/server behavior.

## Server authority

The official server determines:

- accepted locked loadouts;
- runtime rules;
- start time;
- victory;
- result reason;
- match result record.

## Versioning rule

A rated result must never depend on an unpinned rolling game build. Updating Mindustry for a future season creates a new compatible ruleset/version rather than silently mutating old competitive semantics.
