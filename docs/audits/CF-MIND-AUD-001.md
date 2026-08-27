# CF-MIND-AUD-001 — Mindustry Platform Feasibility

Status: **READY**

Type: bounded read-only/source-backed audit. Do not implement production Codefront features during this task.

## Target

- Mindustry `v159.7`
- official Mindustry source/Javadocs/wiki where possible
- official Java mod/plugin mechanisms
- desktop + headless server, x86_64 first

## Purpose

Prove whether Mindustry can host the accepted Codefront v2 loop before substantial implementation begins.

## Questions / acceptance checks

### A. Bootstrap and lifecycle

1. Can a Java mod load on desktop v159.7?
2. Can the same trusted Codefront code run in the headless-server environment, or what separation is required between mod and server plugin?
3. What lifecycle/events are safest for arena load, match start, end and reset?

### B. Arena and fixed bases

4. Can Codefront load/select a controlled custom arena programmatically?
5. Can it place or instantiate two identical fixed bases for different teams at controlled slot origins?
6. Can it apply a bounded declarative DefenseLayout after the fixed base exists?

### C. Rules and manual-control lockout

7. Which `Rules`/server mechanisms disable unit possession/manual control?
8. How can manual building and other player actions that affect a running Codefront match be prevented?
9. Can standard Mindustry game-over be disabled/replaced so Codefront owns result finalization?

### D. Logic/programming

10. Can processor code supplied as player data be loaded/configured without accepting player Java/JAR code?
11. Do processors execute normally in headless multiplayer?
12. What instructions/state expose wall-clock time, RNG, global sensing or other competitive hazards?
13. Can program/unit ownership be constrained to a player's intended scope, especially for future 2v2?

### E. Robots

14. Can existing units be spawned for controlled teams without normal resource production chains?
15. Can native logic control those units in headless multiplayer?
16. What unit caps/pathfinding/performance limits matter for 1v1 and future 2v2?

### F. Weapons without economy

17. For the candidate defensive blocks, which require items/liquids/power/coolant to function?
18. What is the cleanest ruleset/mod mechanism to normalize those requirements so the player does not build logistics?
19. Which weapons are unsuitable because removing their resource dependency materially breaks their identity/balance?

### G. Victory and reset

20. Can Codefront reliably detect destruction of the opposing Team Core?
21. Can it emit a single authoritative winner/result reason?
22. Can the entire arena/runtime state be reset to a known-clean baseline and a second match started **without restarting the server process**?

### H. Schematics and validation

23. Which schematic APIs are useful for fixed bases or tooling?
24. What exactly is serialized in schematic tile/config data for processors and other configurable blocks?
25. Which data must never be trusted without validation?

## Required evidence

For every answer provide:

- source path/class/method or official documentation reference;
- concise finding;
- Codefront implication;
- PASS / PARTIAL / FAIL;
- unresolved risk if any.

## Critical gates

The platform is **FAIL for Codefront MVP** if any of these cannot be achieved without patching/forking the Mindustry executable/server:

- two controlled team bases in one match;
- autonomous processor-controlled units;
- effective manual-control lockout;
- server-owned victory result;
- clean repeatable match reset.

A requirement that needs a trusted Java mod/server plugin is acceptable.

## Deliverable

Create `docs/audits/CF-MIND-AUD-001-RESULT.md` with:

1. executive status;
2. evidence table for all checks;
3. recommended mod/plugin split;
4. recommended path for resource-free weapons;
5. logic security/validation findings;
6. reset strategy;
7. exact scope for `CF-MIND-SPIKE-001`.
