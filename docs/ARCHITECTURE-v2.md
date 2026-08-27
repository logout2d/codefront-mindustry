# Codefront Architecture v2 — Mindustry

Status: **ACCEPTED BASELINE / PRE-AUDIT**

## 1. Architectural goal

Use Mindustry as the simulation, rendering, unit, pathfinding, combat, map and networking foundation while Codefront owns competitive match rules, loadout validation, autonomy constraints and results.

Do not port Stardeus runtime code. Preserve only platform-independent Codefront concepts that remain useful.

## 2. Trust boundaries

### Trusted code

- Codefront Java mod;
- Codefront server plugin/services;
- pinned Mindustry build;
- official Codefront rulesets and arenas.

### Untrusted player data

- DefenseLayout;
- RobotDeck;
- player programs;
- metadata accepted by the loadout format.

Player-supplied Java/JAR code is not a valid Codefront loadout component.

## 3. Authoritative server

Competitive results are server authoritative.

Codefront does not require clients to reproduce a match bit-for-bit.

Every official result must identify at least:

- Mindustry build;
- Codefront version;
- RulesetVersion;
- arena identifier/version;
- locked loadout hashes;
- participants/slots;
- winner/result reason.

## 4. Team-first match model

```text
CodefrontMatch
├─ matchId
├─ rulesetRef
├─ arenaRef
├─ teams[]
│  └─ TeamEntry
│     ├─ teamId
│     └─ playerSlots[]
│        └─ PlayerSlot
│           ├─ slotId
│           ├─ playerId
│           └─ PlayerLoadout
└─ result
```

MVP constraints teams to two teams with one PlayerSlot each. The schema must not encode `PlayerA`/`PlayerB` as permanent architectural concepts.

## 5. PlayerLoadout

```text
PlayerLoadout
├─ formatVersion
├─ defenseLayout
├─ robotDeck
├─ programs
└─ metadata
```

The fixed base is **not** player-owned loadout data. It comes from the Ruleset/Arena definition and is identical for equivalent slots.

## 6. DefenseLayout

DefenseLayout describes only permitted customization around the fixed base.

Each placement should be reducible to deterministic declarative data such as:

- block/content identifier;
- legal position relative to slot origin;
- orientation;
- approved configuration.

The validator owns all legality decisions.

## 7. RobotDeck

RobotDeck specifies permitted unit types and quantities/costs. It does not serialize live Mindustry Unit state.

Runtime units are created by MatchRunner according to the ruleset's spawn/reinforcement policy.

## 8. Programs

Programs are declarative/untrusted player artifacts executed through approved Mindustry logic mechanisms.

Program validation is a separate concern from schematic/defense validation.

Future restrictions may include:

- maximum code/instruction size;
- processor limits;
- prohibited instructions/sensors;
- wall-clock access;
- RNG policy;
- ownership scope;
- communication bandwidth.

## 9. Ruleset

Ruleset is the competitive contract. It defines or references:

- compatible Mindustry build;
- compatible Codefront version;
- arena/base template;
- legal defense zones;
- allowed blocks;
- allowed units;
- DP/RP/CP budgets;
- manual-control restrictions;
- spawn policy;
- victory/timeout rules;
- team size;
- future TeamBus policy.

Rulesets are immutable once used for a rated season/match history.

## 10. Validator pipeline

```text
Submitted loadout
      ↓
Format validation
      ↓
Content whitelist validation
      ↓
Geometry / legal-zone validation
      ↓
Point-budget validation
      ↓
Program validation
      ↓
Ownership / team-policy validation
      ↓
Canonicalize
      ↓
Hash
      ↓
LOCKED LOADOUT
```

No player-controlled mutation is accepted after locking.

## 11. MatchRunner lifecycle

Target lifecycle:

1. select/pin ruleset;
2. load/reset official arena;
3. instantiate fixed symmetric team bases;
4. apply validated DefenseLayouts;
5. initialize programs/processors;
6. create robots according to RobotDeck/spawn policy;
7. enforce no-manual-control rules;
8. start authoritative match clock;
9. observe victory/timeout conditions;
10. freeze result;
11. emit result record/log;
12. reset to a known-clean state;
13. prove a second match can run without server restart.

## 12. 2v2 extension point

Future team mode adds multiple PlayerSlots per TeamEntry without changing the basic match model.

Preferred collaboration rule:

```text
Player A1 Programs ─┐
                    ├─ bounded TeamBus ─ coordination only
Player A2 Programs ─┘
```

Programs retain player ownership boundaries. TeamBus is not required for MVP and must not be implemented before 1v1 feasibility is proven.

## 13. Current non-goals

- custom robot physics/pathfinding;
- crew/characters;
- Stardeus save compatibility;
- carrier ships;
- mining/economy simulation;
- matchmaking/rating/accounts;
- custom Codefront programming language;
- 2v2 implementation;
- mobile support.
