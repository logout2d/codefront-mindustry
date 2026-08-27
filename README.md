# Codefront — Mindustry

Codefront is a competitive autonomous robot-warfare game built on top of Mindustry.

Players do not directly control the battle. Before a match they:

1. receive an identical fixed base template and symmetric arena conditions;
2. design only the allowed defensive layout around that base;
3. select a robot deck within a point budget;
4. program autonomous defensive and unit behavior;
5. lock the loadout and watch the simulation resolve without human input.

The winning side is the one whose engineering design and software destroy the opposing team core.

## Core design principles

- **Robot war only.** No crew or character simulation.
- **No in-match economy for MVP.** Mining, conveyors, ammo logistics and production chains are outside the Codefront core loop.
- **Points replace economy.** Weapons, defensive structures, robots and compute are constrained during loadout design.
- **Fixed symmetric bases.** Core infrastructure is supplied by the ruleset and cannot be redesigned by players.
- **Custom defense only in designated zones.** Geometry exploits and pathfinding cheese are explicitly constrained.
- **No manual control after match start.** The match is an autonomous simulation.
- **Team-first architecture.** MVP is 1v1, but the data model is Team vs Team from day one so 2v2 can add cooperative programming later.
- **Authoritative server.** Competitive results are determined by the Codefront match server.
- **Versioned rulesets.** Game build, Codefront version and ruleset version are pinned for competitive seasons.

## Target platform

Initial feasibility target:

- Mindustry: **v159.7**
- Java: **JDK 17**
- Desktop/server: **x86_64 first**
- Mod API: Java mod + server-side plugin capabilities as needed

## Repository status

**Platform feasibility / pre-MVP.**

The previous Stardeus implementation remains a reference prototype. This repository is a clean Mindustry-specific implementation; source code is not being ported directly from Stardeus.

## First milestone

`CF-MIND-AUD-001` must prove that Codefront can:

- load a controlled arena;
- place two fixed bases for opposing teams;
- apply different defense layouts;
- run player processor programs;
- run autonomous units;
- prevent manual intervention;
- detect destruction of the team core;
- produce a winner;
- reset and run another match without restarting the server.

See `docs/` for the architecture, game design, ruleset and roadmap.
