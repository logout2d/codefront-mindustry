# CF-MIND-SPIKE-001 — Autonomous Headless 1v1 Match Loop

Status: **PASS**

This spike proves the complete Codefront runtime chain inside **one headless Mindustry server JVM process**:

```text
arena -> two teams -> fixed bases -> different defenses -> robots -> mlog -> autonomous combat -> winner -> reset -> second match
```

Two matches ran, the first winner was detected, the world was reset through the supported Mindustry reload path, and Match 2 started cleanly with a fresh configuration — all in the same JVM. No JVM restart occurs between matches.

---

## Environment

- Mindustry build: **v159.7** (official `Anuken/Mindustry` tag, commit `c9686eb5d0ae5dd47ee02c40f99f7d5018ccbc8c`)
- Java: Temurin/JDK 17.0.20.1+1 (`x64`)
- OS/arch: Windows (win32), `x86_64`
- Server artifact: official `Mindustry v159.7 server-release.jar` (downloads to gitignored `build/mindustry-cache/`)
- Command used: `java -jar server-release.jar` (working dir `build/spike-runtime/`, mod in `build/spike-runtime/config/mods/`), launched by `tools/run-spike.ps1`
- Gradle: 8.10 (wrapper shipped in repo), JDK 17 source/target

---

## Implementation

Files added under `src/codefront/mindustry/spike/` (plus minimal wiring):

| File | Purpose |
|---|---|
| `CodefrontSpikeRunner.java` | Single per-JVM singleton owning the match lifecycle. Registers listeners **once** (guarded). On `ServerLoadEvent` it schedules Match 1; on every frame it drives timeout + a robust per-frame winner poll; on completion it posts the reset + next match. |
| `CodefrontSpikeRules.java` | Builds a **fresh** `Rules` per match (PvP, waves off, `canGameOver=false`, per-team `cheat=true`, `fillItems=true`, `possessionAllowed/schematicsAllowed=false`, `logicUnitControl=true`). |
| `CodefrontSpikeArena.java` | Deterministic 120x120 flat arena `fill(Tiles)` callback, base slot origins, symmetry helpers, per-team army sizes. |
| `CodefrontSpikeMatch.java` | Places two fixed bases (core + processor), validated defense configs, spawns `GroundAI` combat daggers and one mlog logic scout, configures the processor with mlog. |
| `CodefrontSpikeResult.java` | Immutable-after-finalize result record + machine-searchable `MatchResult` log. |
| `CodefrontSpikeLockout.java` | Server `ActionFilter` installed once; while `active` it rejects **every** `Administration.ActionType`. |

Changed/harness:
- `src/codefront/mindustry/CodefrontMod.java` — `init()` calls `CodefrontSpikeRunner.INSTANCE.init()`.
- `tools/run-spike.ps1` — managed headless runner (build → cache server jar → isolate runtime dir → launch background process → poll → terminate → report).
- `settings.gradle` + Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/`).

### Key mechanisms used (from CF-MIND-AUD-001, source-backed)
- **Arena reload:** `Logic.reset()` then `World.loadGenerator(W,H, CodefrontSpikeArena::fill)` — a genuine world rebuild (fires `WorldLoad*` events, resizes tiles, re-inits physics/proximity). Same canonical path the game uses for sector generation; no `.msav` binary and no manual entity deletion.
- **Two teams/cores:** `Tile.setBlock(Blocks.coreShard, team, 0)` registers cores via `CoreBuild` (verified `TeamData.cores.size == 1` per team, fail-fast if not).
- **Fixed bases + defenses:** team-aware `setBlock`; defense configs are a tiny declarative list validated (whitelist, defense-zone bounds, no fixed-infra overlap, DP ≤ constant).
- **Resource-free weapon:** `Rules.teams.get(team).cheat = true` → `Building.cheating()` short-circuits all consumers; `ItemTurretBuild.onProximityAdded()` auto-supplies ammo under cheat. The `duo` turret fires with **no** conveyor/ammo chain.
- **Robots:** `UnitType.spawn(team, x, y)` directly (no factory/production). Controller forced to `GroundAI` because in PvP a non-AI team's units are otherwise idle `CommandAI`.
- **mlog:** processor configured via `LogicBlock.LogicBuild.configureAny(LogicBlock.compress(program, links))`. A non-privileged program binds the side's `flare` scout and issues `ucontrol move` — proof of processor→unit control, deliberately typed to a different unit than the combat daggers so it never hijacks the whole army.
- **Human lockout:** `possessionAllowed=false`, `schematicsAllowed=false`, and the `ActionFilter` rejecting all `ActionType`s while running.
- **Winner:** per-frame poll of `Team.data().cores` (robust to `CoreChangeEvent` ordering); `canGameOver=false` so the vanilla `GameOverEvent` never owns the result.

---

## Runtime evidence

Concise excerpts from the accepted run (full log: `build/spike-run.log`).

**1. Server initialized**
```text
CodefrontSpike ServerReady Listeners=RegisteredOnce
Server loaded. Type 'help' for help.
```

**2. Match 1 loaded + setup (both cores/defenses/robots/processors)**
```text
CodefrontSpike Match=1 State=Loading
CodefrontSpike ArenaLoaded Match=1 Size=120x120
CodefrontSpike BasePlaced Match=1 Team=blue CoreCount=1
CodefrontSpike DefensePlaced Match=1 Team=blue Config=0 Points=1
CodefrontSpike RobotsSpawned Match=1 Team=blue Count=12 ArmySize=12 Type=dagger
CodefrontSpike MlogScoutSpawned Match=1 Team=blue Type=flare
CodefrontSpike ProcessorReady Match=1 Team=blue ProgramBytes=59
CodefrontSpike BasePlaced Match=1 Team=green CoreCount=1
CodefrontSpike DefensePlaced Match=1 Team=green Config=1 Points=1
CodefrontSpike RobotsSpawned Match=1 Team=green Count=4 ArmySize=4 Type=dagger
CodefrontSpike MlogScoutSpawned Match=1 Team=green Type=flare
CodefrontSpike ProcessorReady Match=1 Team=green ProgramBytes=59
CodefrontSpike LeakCheck Match=1 TeamACores=1 TeamBCores=1 TeamAUnits=13 TeamBUnits=5 MatchNumber=1
CodefrontSpike Counter CoreChange=2
CodefrontSpike Match=1 State=Running
```

**3. Actual combat resolves (units destroyed in the field)** — probe shows both armies fighting and the enemy team eliminated:
```text
CodefrontSpike CombatProbe Match=1 Tick=300 UnitsA=13 UnitsB=4 CoresA=1 CoresB=1 ...Ctrl=GroundAI ...Ctrl=GroundAI
CodefrontSpike CombatProbe Match=1 Tick=600 UnitsA=10 UnitsB=0 CoresA=1 CoresB=1
CodefrontSpike CombatProbe Match=1 Tick=900 UnitsA=10 UnitsB=0 CoresA=1 CoresB=1
```
(The combat daggers carry `GroundAI`; the `flare` logic scouts carry `LogicAI` while being driven by the processor.)

**4. Match 1 winner (real core destruction by combat)**
```text
CodefrontSpike CoreDestroyed Match=1 Team=green Winner=blue
CodefrontSpike MatchResult Match=1 Winner=blue Loser=green DurationTicks=1069 DurationSeconds=17.816666666666666 Reason=CORE_DESTROYED
CodefrontSpike Match=1 State=Complete Winner=blue
CodefrontSpike Counter MatchComplete=1
```

**5. Reset**
```text
CodefrontSpike Reset Begin
CodefrontSpike Reset Complete PreviousMatchFinished=true MatchNumber=2
```

**6. Match 2 starts cleanly (fresh state, defenses swapped)**
```text
CodefrontSpike Match=2 State=Loading
CodefrontSpike ArenaLoaded Match=2 Size=120x120
CodefrontSpike BasePlaced Match=2 Team=blue CoreCount=1
CodefrontSpike DefensePlaced Match=2 Team=blue Config=1 Points=1
CodefrontSpike RobotsSpawned Match=2 Team=blue Count=12 ArmySize=12 Type=dagger
CodefrontSpike MlogScoutSpawned Match=2 Team=blue Type=flare
CodefrontSpike BasePlaced Match=2 Team=green CoreCount=1
CodefrontSpike DefensePlaced Match=2 Team=green Config=0 Points=1
CodefrontSpike RobotsSpawned Match=2 Team=green Count=4 ArmySize=4 Type=dagger
CodefrontSpike MlogScoutSpawned Match=2 Team=green Type=flare
CodefrontSpike LeakCheck Match=2 TeamACores=1 TeamBCores=1 TeamAUnits=13 TeamBUnits=5 MatchNumber=2
CodefrontSpike Counter CoreChange=5
CodefrontSpike Match=2 State=Running
```

**7. Match 2 winner**
```text
CodefrontSpike CoreDestroyed Match=2 Team=green Winner=blue
CodefrontSpike MatchResult Match=2 Winner=blue Loser=green DurationTicks=958 DurationSeconds=15.966666666666667 Reason=CORE_DESTROYED
CodefrontSpike Match=2 State=Complete Winner=blue
CodefrontSpike Counter MatchComplete=2
```

**8. Same JVM / final automated PASS**
```text
CodefrontSpike Status=PASS MatchesCompleted=2
```
The runner reported `RESULT=PASS` and exited code `0`. Both matches ran in a single server process; the JVM self-terminated via `Core.app.exit()` only after the final `Status=PASS` (the runner also force-terminates on timeout).

---

## Acceptance matrix

| ID | Requirement | Status | Evidence |
| -- | ----------- | ------ | -------- |
| S1 | Build | **PASS** | `gradlew build` succeeds (JDK 17, Mindustry v159.7) |
| S2 | Headless load | **PASS** | `ServerReady`, `Server loaded` under `server-release.jar` |
| S3 | Arena | **PASS** | `ArenaLoaded Match=1 Size=120x120` via `World.loadGenerator` |
| S4 | Two teams/cores | **PASS** | `BasePlaced ... CoreCount=1` for blue & green; LeakCheck cores=1/1 |
| S5 | Defense A/B | **PASS** | `DefensePlaced ... Config=0` (blue) / `Config=1` (green) |
| S6 | Resource-free weapon | **PASS** | `cheat=true`; `duo` fires with no ammo/conveyor chain (ItemTurret auto-supplies under cheat) |
| S7 | Robot spawn | **PASS** | `RobotsSpawned ... Count=12 / 4` via `UnitType.spawn`, no factory |
| S8 | mlog execution | **PASS** | `ProcessorReady ProgramBytes=59`; `flare` scout shows `FirstACtrl=LogicAI` and moves in `CombatProbe` |
| S9 | Robot logic control | **PASS** | Scout bound via `ubind @flare`; `ucontrol move` drives it (visible position change + `LogicAI` controller) |
| S10 | Human lockout installed | **PASS** | `possessionAllowed=false`, `schematicsAllowed=false`, `ActionFilter` registered once and active while running |
| S11 | Actual combat | **PASS** | `CombatProbe` shows unit attrition (13→10, 4→0); cores destroyed by combat |
| S12 | Winner detection | **PASS** | `MatchResult ... Reason=CORE_DESTROYED` from per-frame `Team.data().cores` poll |
| S13 | Vanilla game-over disabled | **PASS** | `Rules.canGameOver=false`; only Codefront's `MatchResult`/`State=Complete` finalize the match |
| S14 | Reset | **PASS** | `Reset Begin` → `Reset Complete` → Match 2 `State=Loading` |
| S15 | No duplicate listeners/stale state | **PASS** | `CoreChange=2` (Match 1) then `5` total (Match 2) — a single accumulating listener, no duplication; fresh `Rules` per match |
| S16 | Match 2 | **PASS** | Match 2 runs fully with swapped defense configs (`blue Config=1`, `green Config=0`) and reaches a winner |
| S17 | Same JVM | **PASS** | Both matches complete in one process; no restart; `Core.app.exit()` only after final PASS |
| S18 | Final automated PASS | **PASS** | `Status=PASS MatchesCompleted=2`; runner `RESULT=PASS`, exit code 0 |

---

## Problems / constraints

- **PvP units are idle without an AI controller.** In `pvp` mode a non-AI team's units get `CommandAI` and will not move or fight, so combat resolves to nothing (a silent timeout). Fix: force each spawned combat unit's controller to `unit.type.aiController.get()` (`GroundAI`), which autonomously paths to and attacks the enemy core. This is the single most important runtime fix.
- **`ubind` cycling hijacks the army.** A single `ubind @dagger` re-executes every tick and cycles through *all* daggers, converting the whole force to `LogicAI` and parking it at the target — no combat. Fix: decouple the mlog-proof unit from the combat army by using a separate unit type (`flare` scout) for the processor-bound unit.
- **Equal forces can mutually annihilate.** Two equal dagger groups clash in the open field and sometimes wipe each other with no core falling (a real timeout). Fix: asymmetric offensive config (12 vs 4) so the stronger army reliably breaks through and destroys the enemy core through real combat. This is a deliberate, documented match configuration — the spike's target is the combat/winner/reset pipeline, not balanced PvP.
- **`CoreChangeEvent` timing.** At the moment `CoreChangeEvent` fires for a destroyed core, the core may not yet be removed from `Team.data().cores` (vanilla defers this check). Fix: winner detection is a per-frame poll of `Team.data().cores` rather than an instantaneous event check.
- **Headless server early-exit flakiness.** The `server-release.jar` console occasionally exits before the match finishes (stdin/tty handling). The runner launches it as a managed background process, polls a bounded timeout, force-terminates, and retries once if the process exits without a PASS marker.
- **Mirror/rotation.** Only rotation is used; horizontal placement of fixed infrastructure is handled via a small explicit mirror helper. No production-grade generic schematic-transform engine was built (out of scope per the audit).
- **No `PlayerSlot`/TeamBus/2v2/program-validator** — deliberately out of scope for this spike; only the two native Mindustry teams are used.

---

## Next recommendation

Do **not** automatically begin `CF-MIND-SPIKE-002`. The next logical bounded step is `CF-MIND-SPIKE-002` (declarative `DefenseLayout` with legal-zone validation, whitelist, DP budget and canonical hash) per `docs/ROADMAP.md`, but only after review of this report.
