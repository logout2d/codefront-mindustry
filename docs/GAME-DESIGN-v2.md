# Codefront Game Design v2 — Mindustry

Status: **ACCEPTED BASELINE**

## 1. Product identity

Codefront is a competitive game about autonomous robot warfare.

The player competes by designing a defensive system, selecting robots and writing programs. Once the match starts, direct human control is disabled.

The game is not about crew simulation, mining efficiency or manually commanding an RTS army.

## 2. Core loop

1. Select a ruleset.
2. Receive the fixed base template and legal build zones defined by that ruleset.
3. Spend pre-match point budgets on defensive structures and robot composition.
4. Write/configure autonomous programs.
5. Validate and lock the loadout.
6. Start the match on a symmetric arena.
7. Observe the autonomous battle.
8. Record the authoritative result.

## 3. Fixed base principle

Both sides receive identical non-editable core infrastructure.

The fixed base may include, as required by a ruleset:

- Team Core;
- processor/control infrastructure;
- unit spawn interfaces;
- sensors or other standardized match infrastructure.

Players may customize only explicitly designated defense zones.

The goal is to compare engineering and software choices rather than hidden economic or base-layout advantages.

## 4. No in-match economy for MVP

The following Mindustry systems are outside the MVP Codefront loop:

- mining;
- resource extraction;
- production chains;
- conveyor logistics;
- ammunition logistics;
- economic expansion.

A weapon selected during loadout construction is considered paid for by its point cost. Its operation during a match must not depend on a player-built resource chain unless a future ruleset explicitly introduces such a mechanic.

## 5. Point budgets

Points exist before the battle, not as an in-match currency.

Initial design separates budgets conceptually:

- Defense Points (DP);
- Robot Points (RP);
- Compute Points (CP).

Exact values are TBD and belong to a versioned ruleset.

Separate budgets prevent degenerate designs that delete an entire pillar of gameplay solely to maximize another.

## 6. Defense design

Players choose allowed defense types, positions and orientations inside legal defense zones.

Validation must prevent geometry abuse such as:

- blocking mandatory paths;
- surrounding protected infrastructure with illegal filler;
- constructing outside the legal zone;
- exceeding wall or structure limits;
- using banned blocks/configurations.

Walls and other path-shaping structures may require a dedicated sub-budget or count limit.

## 7. Robots

Mindustry units are the foundation for Codefront robots.

The MVP should reuse existing unit movement, combat, pathfinding and logic-control capabilities rather than implement a custom drone engine.

A player selects a RobotDeck constrained by RP and ruleset whitelists.

Spawn/reinforcement timing is deliberately TBD. Candidate modes include initial deployment, waves or replenishment. The first spike should use the simplest mode that proves autonomous combat.

## 8. Programming

Programs are a primary competitive asset.

For early prototypes, native Mindustry logic (mlog) is preferred over inventing a custom language.

Programs may control permitted player-owned units and defense behavior. Competitive validation may later restrict unsafe, nondeterministic or meta-breaking operations.

Compute capacity is a game-design resource and may be represented through CP, processor count, processor tiers, instruction throughput or a combination of these.

## 9. Match control

After match start:

- manual unit possession is disabled;
- manual building is disabled;
- manual command actions that affect combat are disabled;
- locked loadouts are immutable;
- the server is authoritative.

Players are observers of the systems they designed.

## 10. Symmetry

Competitive arenas should be symmetric by construction or proven equivalent by ruleset design.

Symmetry covers:

- fixed base geometry;
- legal defense zones;
- spawn geometry;
- relevant terrain/obstacles;
- starting state;
- budgets;
- rule modifiers.

## 11. Team play

The architecture is Team vs Team from the first implementation even though MVP gameplay is 1v1.

Future 2v2 should add meaningful cooperative programming rather than merely doubling unit counts.

Accepted direction for 2v2:

- one shared team objective/core;
- individual player budgets;
- player ownership of robots/programs;
- programs may coordinate but should not directly take over allied ownership;
- future bounded TeamBus for machine-to-machine communication;
- no human intervention after start.

## 12. Victory

Default MVP victory condition: destruction of the opposing Team Core.

Timeout/tiebreak rules are TBD and must be versioned in the ruleset.
