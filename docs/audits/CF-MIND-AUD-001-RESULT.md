# CF-MIND-AUD-001 — Mindustry Platform Feasibility Audit (Result)

Status: **COMPLETE**

Type: bounded read-only / source-backed feasibility audit. No production Codefront features were implemented during this task.

## Pin

- Mindustry build: **v159.7**
- Primary source: `Anuken/Mindustry` git tag `v159.7`, commit `c9686eb5d0ae5dd47ee02c40f99f7d5018ccbc8c`
- Java: JDK 17, desktop/headless server `x86_64`
- Relationship to this repo: `codefront-mindustry` (only a bootstrap `CodefrontMod`; see `src/codefront/mindustry/CodefrontMod.java`)

Every finding below is tied to a concrete `class` / `method` / `field`. Source paths omit the leading `core/src/mindustry/` prefix (e.g. `core/World.java` means `core/src/mindustry/core/World.java`).

---

## 1. Executive Summary

**Overall status: VIABLE (with constraints). No hard platform blocker found for Codefront MVP. Proceed to `CF-MIND-SPIKE-001`.**

Mindustry v159.7 exposes every capability the accepted Codefront MVP requires, and the official source confirms them directly:

1. **Java mod + server plugin lifecycle** is first-class (`mindustry.mod.Mod`, `mindustry.mod.Plugin`; loaded by `Mods`). Trusted Java code runs on headless servers.
2. **Controlled arena load** is supported and is even the *default* mechanism for starting a game: `Control.playMap(map, rules)` → `logic.reset()` + `World.loadMap(map, rules)` (+ `Logic.play()`). A dedicated Codefront arena file can be loaded and reloaded in the same process.
3. **Team-aware fixed base placement** exists: `Schematics.place(schematic, x, y, team, overwrite)` places blocks for an arbitrary `Team`, and `Schematics.placeLoadout` additionally registers cores via `state.teams.registerCore`. Rotation is supported (`Schematics.rotate`); horizontal **mirroring is not a built-in primitive and must be implemented** (small, one-time).
4. **Resource-free weapons** have a native, engine-level mechanism: `TeamRule.cheat` (`Building.cheating()` = `team.rules().cheat`) makes every consumer free (items, liquids, power) in `Building.updateConsumption()`. This directly realizes the `weapon type + point cost` model. Codefront should still create its own weapon/block set so balance and identity stay under its control.
5. **Processor (mlog) execution** runs inside the normal server build update (`LogicBlock.LogicBuild.updateTile`); processor `code` survives schematic placement because it is stored in the block `config`. Ownership and unit control are team-scoped by the executor (`unit.team == exec.team`), unit binding is gated by `UnitType.logicControllable`, and player code is parsed as *non-privileged* so privileged instructions are discarded by the parser.
6. **Manual human influence can be disabled**: `Rules.possessionAllowed = false` blocks unit possession/control (`InputHandler`), and `NetServer.admins.addActionFilter(...)` can reject every interactive `ActionType` (`breakBlock, placeBlock, rotate, configure, withdrawItem, depositItem, control, buildSelect, command, commandUnits, commandBuilding, respawn, pickupBlock, dropPayload, removePlanned, pingLocation`) while a match is running.
7. **Winner detection** is reliable via `CoreChangeEvent` / core destruction plus `TeamData.cores`, and vanilla game-over can be overridden via `Rules.canGameOver = false`.
8. **Reset without process restart** is supported: `Logic.reset()` + `World.loadMap(...)` is the game's own "new game / restart" path.

### Biggest technical risks (not blockers)
- **Horizontal mirroring is not built in** and must be derived (negate X + relink processor connections). This affects base A/B symmetry and defense zone mirroring.
- **Cross-team information via logic is not fully closed**: `fetch` / `radar` / `sensor` can enumerate and read *enemy* units/buildings (needed for targeting), and processor-to-processor memory read/write is only gated by `readable`/`writable` team checks. A future `ProgramValidator` and Codefront-level ownership policy are required for competitive integrity, especially before 2v2.
- **`fetch` is not ownership-scoped**: a program can *enumerate* any team's units. It cannot *control* enemy units (`UnitControlI.checkLogicAI` requires same team), so the risk is information leakage / metadata, not takeover.
- **Determinism is unproven**: vanilla combat/movement uses frame-order and some per-instance RNG. The audit confirms the *tools* exist (rules freezing, `Rules` flags, global `Rand`) but does not assert bit-deterministic simulation. This is acceptable because Codefront's authoritative result is *recorded server-side*, not bit-replayed by clients (per ARCHITECTURE-v2 §3).
- **State leakage on reset** is the main implementation risk in the spike (`Logic.reset()` recreates `GameState` but Codefront must null its own match-runner refs and re-apply rules). See Section K.

---

## 2. Evidence Table

Columns: Area | Mindustry class/file/API | Finding | Codefront implication | Status

| Area | class/file/API | Finding | Codefront implication | Status |
|---|---|---|---|---|
| A. mod load | `mod/Mod.java` (`init`,`loadContent`,`registerServerCommands`) | Java mod entry, hooks for lifecycle | Trusted Codefront Java code entry point | PASS |
| A. plugin load | `mod/Plugin.java` (`extends Mod`, hidden) | Server-plugin = hidden mod | Same jar can serve desktop mod + headless plugin | PASS |
| A. loader | `mod/Mods.java` (`load`,`loadMod`,`loadContent`,`eachClass`) | Loads jars/zips/folders; calls `loadContent`/`init`; `Plugin` is hidden; java mods checked | Mod packaging understood; `Plugin` hides from UI | PASS |
| A. server init | `server/ServerLauncher.java` (`init`) | Headless: `content.init()`; `mods.eachClass(Mod::init)`; `Events.fire(ServerLoadEvent)` | Hook `ServerLoadEvent` for server/match startup | PASS |
| A. tick | `core/Logic.java` (`update`) / `EventType.Trigger.update` | Per-frame update; `state.tick`, `updateId` | Per-tick match runner hook | PASS |
| A. world load | `core/World.java` (`beginMapLoad`/`endMapLoad`) / `WorldLoadBegin/End/LoadEvent` | Tile load lifecycle events | Detect arena load / placement window | PASS |
| A. game over | `EventType.GameOverEvent` (winner) | Game-over payload carries winner | Server-owned result hook (or replace) | PASS |
| A. reset | `core/Logic.java` (`reset`) / `ResetEvent` | Clears Groups/Time, recreates GameState | Match reset baseline | PASS |
| B. map load | `core/Control.java` (`playMap`), `core/World.java` (`loadMap(map,rules)`), `io/MapIO.java`, `maps/Map.java` | Loads `.msav` map; pvp requires 2 cores | Load Codefront arena | PASS |
| B. rules | `game/Rules.java` (`pvp`, `attackMode`, `waves`, `infiniteResources`, `defaultTeam`, `waveTeam`) | Confipled match mode flags | No campaign/weather interference | PASS |
| B. no campaign | `core/World.java` (`loadMap`, sector handling) / `Map` | Map-based play has no `sector`; campaign guarded by `state.rules.sector` | Arena play is independent of campaign save economy | PASS WITH CONSTRAINTS |
| C. schema place | `game/Schematics.java` (`place`, `placeLoadout`, `toPlans`) | Team-aware block + config placement; core registration | Place fixed base for Team A / Team B | PASS |
| C. tile set | `world/Tile.java` (`setBlock(block, team, rot)`) via Schematics | Places multi-tile blocks per team | Programmatic base instantiation | PASS |
| C. rotation | `game/Schematics.java` (`rotate`) | 90° rotate primitive | Rotate base/defense layouts | PASS |
| C. mirror | `game/Schematics.java` (no mirror method) | No horizontal mirror primitive | Implement mirror manually (negate X, re-link processors) | PASS WITH CONSTRAINTS |
| D. layout | `game/Schematic.java` (`Stile{block,x,y,config,rotation}`) | Exactly the declarative list Codefront Option 2 needs | Recommend own-list, keep optional Schematic import | PASS (rec) |
| E. no economy | `entities/comp/BuildingComp.java` (`updateConsumption`, `cheating()`), `entities/comp/TeamComp.java` (`cheating`), `game/Rules.java` (`TeamRule.cheat`) | `cheating()` short-circuits all consumers (items/liquid/power) to full efficiency | Resource-free weapons: set `TeamRule.cheat = true` | PASS |
| E. ammo | `world/blocks/defense/turrets/Turret.java` (`useAmmo`→`cheating()`) | Cheat bypasses ammo consumption | Coordinate-free operation | PASS |
| F. spawn | `type/UnitType.java` (`create(team)`, `spawn(team,x,y,rot)`) | Direct unit spawn, no factory needed | RobotDeck instantiation | PASS |
| F. logic AI | `ai/types/LogicAI.java`, `logic/LExecutor.java` (`UnitControlI.checkLogicAI`) | Processor controls unit via `LogicAI` controller; existing pathfinding reused | Autonomous robot control | PASS |
| F. gating | `type/UnitType.java` (`logicControllable`) | Flag gates logic control | Whitelist + custom robot types | PASS |
| G. mlog | `world/blocks/logic/LogicBlock.java` (`LogicBuild.config()`/`readCompressed`/`updateTile`) | Code stored in block `config`; executes on server in build update | mlog as program layer; code survives schematic | PASS |
| G. speed | LogicBlock `instructionsPerTick`, `maxInstructionScale`, `ipt` | Per-tick instruction budget | Compute budget (CP) building block | PASS |
| G. no clock | `logic/GlobalVars.java` (`@time` from `state.tick`) | No wall-clock; time = game tick | Removes wall-clock hazard | PASS |
| G. rand | `logic/LogicOp.java` (`rand`→`GlobalVars.rand`), `logic/GlobalVars.java` | Shared `Rand` RNG | RNG exists; validator policy needed | PASS WITH CONSTRAINTS |
| G. ownership | `logic/LExecutor.java` (`UnitBindI`, `UnitControlI.checkLogicAI`; `unit.team==exec.team`) | Bind/control own-team only | Robot/team ownership at engine level | PASS |
| G. build ctrl | `logic/LExecutor.java` (`ControlI.validLink`), LogicBlock `validLink`/`readable` | Building control/read gated to linked same-team | Defense control scope | PASS |
| G. privileged | `logic/LParser.java` / `LAssembler` | Non-privileged parse discards privileged statements | ProgramValidator baseline | PASS |
| G. cross-team info | `logic/LExecutor.java` (`FetchI`, `RadarI`, `SenseI`) | Can enumerate/read enemy entities | Info leak risks; validator + Codefront policy | PASS WITH CONSTRAINTS |
| H. possession | `game/Rules.java` (`possessionAllowed`), `input/InputHandler.java` | Blocks unit control when false | Disable direct unit control | PASS |
| H. actions | `net/Administration.java` (`ActionType`, `addActionFilter`, `PlayerAction`) | All interactive server actions gated | Reject all action types during match | PASS |
| H. build | `net/Administration.java` (`placeBlock` etc.), `game/Rules.java` (`bannedBlocks`) | Build/break/configure gated | No construction during match | PASS |
| I. DP data | `world/Block.java` (`size`,`requirements`,`buildVisibility`,`health`), `Schematic.tiles` | Block identity/count/size/config available | Defense point computation | PASS |
| I. CP data | LogicBlock `instructionsPerTick`, `LExecutor.maxInstructions`, code length | Processor tier + program size available | Compute budget | PASS |
| I. RP data | `type/UnitType.java` (`weapons`,`abilities`), `content.units()` | Unit type identity/weapons available | Robot point computation | PASS |
| J. win | `world/blocks/storage/CoreBlock.java` (`CoreChangeEvent` on `onDestroyed`/`created`), `game/Teams.java` (`TeamData.cores`/`isAlive`) | Core add/remove events; team alive = has core | Winner = enemy `cores()` empty | PASS |
| J. override | `game/Rules.java` (`canGameOver`) | `false` disables vanilla game-over | Codefront own result finalization | PASS |
| K. reset | `core/Logic.java` (`reset`), `core/World.java` (`loadMap`), `core/Control.java` (`playMap`) | Supported reload path in one process | Match1 → reset → Match2 without JVM restart | PASS |
| L. teams | `game/Team.java` (`Team.all[256]`, `Team.get`), `game/Teams.java` (`TeamData`) | 256 native teams; per-team rules/data | Lower-level team = native; Codefront PlayerSlot above | PASS |
| L. 2v2 | native `Team` + `Rules.teams` | One shared core/team; separate player def zones via metadata | Feasible; validator must add ownership | PASS WITH CONSTRAINTS |
| L. TeamBus | `logic/GlobalVars.java`, memory blocks `world/blocks/logic/MemoryBlock.java` | No built-in bounded bus; build on own store | Future bounded TeamBus = Codefront feature | PASS WITH CONSTRAINTS |

---

## 3. Detailed Findings (A–L)

### A. Mod and server lifecycle

- **Java mod load**: `mindustry.mod.Mod` (`core/src/mindustry/mod/Mod.java`) is the base. Hooks: `init()`, `loadContent()`, `registerServerCommands`, `registerClientCommands`, `packSprites`. `mindustry.mod.Plugin extends Mod` and is "always hidden" (`Plugin.java:3`); `Mods.loadMod` special-cases it to `meta.hidden = true` (`Mods.java:1181-1183`). Loader enumerates `modDirectory` jars/zips/folders with `mod.json`/`mod.hjson`/`plugin.json`/`plugin.hjson` (`Mods.java:34,516`). Java class mods are created reflectively via `main.getDeclaredConstructor().newInstance()` (`Mods.java:1175`).
- **Server plugin load**: `server/ServerLauncher.java` sets `headless=true`, calls `Vars.init()`, `content.createBaseContent()`, `mods.loadScripts()`, `content.createModContent()`, `content.init()`, then `mods.eachClass(Mod::init)` then `Events.fire(new ServerLoadEvent())` (`ServerLauncher.java:80-82`). So a single jar can act as both desktop mod (`mod.hjson` + `main:`) and headless plugin. `Plugin` subclasses are always hidden and are the cleanest server-side container.
- **Suitable lifecycle events** (`game/EventType.java`):
  - server startup → `ServerLoadEvent` (server) / `ClientLoadEvent` (desktop client).
  - world/map load → `WorldLoadBeginEvent` → `WorldLoadEndEvent` → `WorldLoadEvent` (`World.java:208-238`).
  - match start → `PlayEvent` (fired by `Logic.play()`, `Logic.java:274`) and `Trigger.update` per tick (`Logic.java:501`).
  - update/tick → `Trigger.update` / `Trigger.beforeGameUpdate` / `Trigger.afterGameUpdate`; plus `GameState.tick` & `updateId` (`GameState.java:23`).
  - core destruction → `CoreChangeEvent` (fire on `CoreBuild.created/changeTeam/onDestroyed`, `CoreBlock.java:536,549,656`) and `BlockDestroyEvent`.
  - game over → `GameOverEvent(winner)` (`EventType.java:366-372`), fired from `Logic.checkGameState` (`Logic.java:331-373`).
  - world reset → `ResetEvent` (`Logic.reset()`, `Logic.java:302`).
- **Client UI vs authoritative logic separation**: clean. Headless `ServerLauncher` runs the full `Logic` (`Core.app.addListener(logic = new Logic())`, `ServerLauncher.java:75`) with no UI. Client rendering (`Renderer`, `UI`) is absent in headless. None of the authoritative match logic depends on client UI. The vanilla code already branches `headless` throughout. Conclusion: separation is natural.

### B. Arena loading

- `World.loadMap(Map map, Rules rules)` (`core/World.java:344`) loads a `.msav` arena via `SaveIO.load(map.file, ...)`; it fires `WorldLoadBegin/End/LoadEvent`. `Control.playMap(Map, Rules)` (`core/Control.java:396-425`) is the canonical "start a new game": `logic.reset(); world.loadMap(map, rules); state.rules = rules; logic.play();`.
- Teams/known-state: pvp maps require ≥2 cores (`World.java:372-374`); headless also checks a core exists (`World.java:384-388`). Codefront assigns teams by placing cores for the two opposing `Team`s at known origins (Section C).
- Preventing campaign/economy interference: `Map`-based play has `state.rules.sector = null` set by `Control.playMap` (`Control.java:408`). The campaign path is explicitly `state.isCampaign()` guarded throughout `Logic.java` and `World.loadSector` is separate (`World.java:261`). Setting `Rules.waves=false`, `Rules.pvp=true` (or `attackMode=true`), `Rules.infiniteResources`/team `cheat`, and `Rules.canGameOver=false` keeps the arena free of wave/economy logic. Weather can be cleared via `Rules.weather.clear()`.
- A dedicated Codefront arena is loadable as a shipped `.msav` mod asset wrapped in a `Map` (`Map.java` constructors at 44-64) — no campaign progression involved.

### C. Fixed symmetric bases

- `Schematics.place(Schematic schem, int x, int y, Team team, boolean overwrite)` (`game/Schematics.java:520-533`) is team-aware: it calls `tile.setBlock(st.block, team, st.rotation)` and `tile.build.configureAny(config)`. This places a known base for **Team A at origin A** and a copy for **Team B at origin B**.
- `Schematics.placeLoadout(schem, x, y, team, check)` (`Schematics.java:479-514`) additionally calls `state.teams.registerCore(cb)` on each core, which makes the team "active" and is what the pvp validation + winner detection rely on.
- `Schematics.rotate(input, times)` (`Schematics.java:712`) provides 90° rotation (counter/clockwise). **There is no horizontal-mirror primitive**; mirroring must be implemented by negating the X coordinate of each `Stile`, adjusting multiblock offsets, and remapping processor link coordinates (which `LogicBuild.pointConfig` / `readCompressed(relative)` can re-bias; see `LogicBlock.java:186,304`). This is a small, one-time helper and does not reconstruct a saved world.
- Schematic **config preservation**: `Stile.config` is serialized via `TypeIO` (`Schematics.java:692`); on placement `configureAny(config)` restores per-block config. Processors store their mlog program as `config` (compressed bytes), so processor code inside a base/defense schematic is preserved across placement (Section G).
- Conceptual flow confirmed: `load arena -> Schematics.place(base, Ax, Ay, teamA) -> Schematics.place(base, Bx, By, teamB)`; plus `registerCore` via loadout path if using cores. No world reconstruction required.

### D. Defense layout layer

Compare the two options:

**Option 1 — restricted `Schematic`:**
- Pros: reuses the entire vanilla authoring/IO/validation ecosystem (`Schematics.read/write/readBase64`), the block list is exactly `block + x/y + rotation + config` (`Schematic.Stile`), and `Schematics.place` applies it team-aware. Processor configs ride along.
- Cons: The `.mschem`/base64 format is engine-defined; version tolerance (`read` rejects newer versions, `Schematics.java:579`), and it is not a Codefront-controlled contract. Arbitrary `config` `Object`s (including content refs, positions, colors) must still be validated. Not naturally "relative to slot origin" (it is absolute in tile grid from placement origin).

**Option 2 — own list (`block type + relative x/y + rotation + validated config`):**
- This is *exactly* what `Schematic.Stile` already is. Codefront declares its own schema (e.g. JSON), validated by the Codefront validator pipeline (ARCHITECTURE-v2 §10), canonicalized, hashed.
- Pros: Codefront controls format/versioning/compat; clean "relative to slot origin"; direct DP cost accumulation from `Block.identity/size/count`; trivial restriction to defense zones (validate coordinates are inside regions); deterministic canonical representation for hashing.
- Cons: must write own import/export; may optionally round-trip into a `Schematic` for rendering/tooling.

**Recommendation for MVP: Option 2 (own declarative list), with an optional conversion to/from `Schematic.Stile` for tooling.** Validation, security, determinism, and point-cost calculation are all cleaner under Codefront's own schema; it does not require the engine's schematic serialization to become a competitive contract. Zones are enforced by the Codefront validator (coordinates), not by engine placement rules. (Schematics are still used for the *fixed base*, which is engine/placeholder-owned and outlet-of-player control.)

### E. Resource-free weapons and systems

Vanilla, per-block requirements:
- Item-ammo turrets: `world/blocks/defense/turrets/ItemTurret.java` (consume items as ammo).
- Liquid turrets: `LiquidTurret.java`, `ContinuousLiquidTurret.java` (consume liquids).
- Power turrets: `PowerTurret.java`, `ContinuousTurret.java`, `LaserTurret.java` (consume power).
- All turrets: consume power + optional coolant (`BaseTurret.coolant`, `Turret.liquidCapacity=20`) as applicable.

The **engine-native mechanism to make them resource-free** is `Rules.TeamRule.cheat`:
- `Building.cheating()` = `team.rules().cheat` (`entities/comp/TeamComp.java:17-18`).
- `Building.updateConsumption()` short-circuits: `if(!block.hasConsumers || cheating()){ potentialEfficiency = enabled && productionValid() ? 1f : 0f; ... shouldConsumePower = true; }` (`entities/comp/BuildingComp.java:1949-1957`) — no consumer (item/liquid/power/coolant) is enforced; efficiency is 1.
- Turret ammo: `TurretBuild.useAmmo()` → `if(cheating()) return peekAmmo();` (`Turret.java:677-678`); `hasAmmo()` allows infinite when cheating (`Turret.java:706-708`).

So `Rules.teams.get(teamA).cheat = true` and `Rules.teams.get(teamB).cheat = true` make all equipped weapons/defenses run without logistics. `Rules.infiniteResources` (global) also exists but affects build costs; the recommended route for "weapon always supplied" is per-team `cheat`. `fillItems` only fills cores (items), not power — not sufficient alone for power-consuming turrets. `cheat` is the complete answer.

**Recommended implementation approach:** Do **not** rely on raw per-block subclassing for the *resource* problem. Subclass/create Codefront weapon `Block`s so that (a) identity is controlled, (b) DP costs/whitelisting are explicit, and (c) post-spike balance can diverge; then run them under `team cheat=true` so resource/power/coolant are irrelevant. Do **not** invisibly inject/replenish resources per tick — that is more fragile than the supported `cheat` flag. (Modifying core vanilla block behavior is unnecessary.) This yields the `weapon type + point cost` model while keeping an authoritative, robust base.

### F. Robot model

- `type/UnitType.java` provides `create(Team)` (`:554`) and `spawn(Team, x, y, rotation)` (`:607`) — direct team-bound spawning with **no factory/resources/production chain required**. This is the RobotDeck spawn path.
- Reuse: units already have movement (`Units`, `MoveComp`), pathfinding (AIController/`Pathfinder`), combat (`Weapons`/`mounts`), targeting, destruction (`Unit`/`Hitboxc`), and team ownership (`Teamc`). `UnitType.logicControllable` (`UnitType.java:183`) and `playerControllable` gate control.
- Logic AI: `ai/types/LogicAI.java` is a vanilla `AIController` that moves/aims units under processor direction, reusing `controlPath.getPathPosition` and `pathfind` (`LogicAI.java:77,102`). `UnitControlI.checkLogicAI` (`logic/LExecutor.java:320-340`) attaches the `LogicAI` controller and requires `unit.team == exec.team` and `controller().isLogicControllable()`.
- **Recommendation for MVP: reuse existing `UnitType`s from a whitelist** (spawned directly via `UnitType.spawn`), exactly as the concept prefers. Custom robot types (`subclass UnitType` or new content) are only needed later for distinct Codefront robot identities — not required to prove autonomous combat. `Rules.unitWhitelist` / `bannedUnits` (`Rules.java:147,189`) can constrain which units may be used.

### G. mlog / processor execution

- **Code survives schematic/config placement**: the mlog program lives in the block `config` as compressed bytes (`LogicBlock.LogicBuild.config()` → `compress(code, links)`, `LogicBlock.java:649`); `config(byte[].class, ...)` handler runs `readCompressed` (`LogicBlock.java:65-69`). Since `Schematic.Stile.config` preserves the `Object config` and `Schematics.place` calls `configureAny(config)`, processor code is placed with the base/defense.
- **Executes on headless server**: `LogicBlock.LogicBuild.updateTile()` (`LogicBlock.java:483-576`) runs `executor.runOnce()` on an accumulator; it is part of `Groups.build.update()` (`Logic.java:482`) in the normal server build update, with no client required.
- **Speed constrained**: `instructionsPerTick` / `maxInstructionScale` / `ipt` (`LogicBlock.java:45-48,265,554`) cap instructions per tick; `LExecutor.maxInstructions = 1000` (`LExecutor.java:41`). This is the compute-budget primitive for CP.
- **World state sensing**: `SenseI` (`LExecutor.java:668`) can sense any `Senseable` (own or enemy, "remote units/buildings can be sensed as well"). Useful and necessary for targeting; a hazard for hidden-information rules (Section D of ProgramValidator).
- **Control owned units**: `UnitBindI` (`:166`) requires `u.team == exec.team` and `UnitType.logicControllable`; `UnitControlI` (`:304`) likewise. `ControlI` for buildings is gated by `validLink` (same team, in range) (`:574`).
- **Server authoritative**: processors execute server-side per tick; `@time` is derived from `state.tick` (`GlobalVars.java:189-192`) — **no wall-clock**. `GlobalVars.rand` is a shared `Rand` (`GlobalVars.java:28`); `LogicOp.rand` uses it (`LogicOp.java:49`).
- **Dangerous/unwanted instructions for a future `ProgramValidator`** (confirmed in source):
  - `unit bind/control`: already gated to own team + `logicControllable` + `Rules.logicUnitControl`. Validator may further restrict which units can be coded.
  - `building control`: `ControlI` gated to linked same-team buildings. Cross-team building read blocked by `readable` (`LogicBlock.java:598-600`).
  - `fetch` (`FetchI`, `:1345`): enumerates units/buildings/cores/players of **any team** and reads counts. Information leak; cannot control enemy units. Validator should restrict which teams/`FetchType`s are allowed, and Codefront-level ownership is needed in 2v2.
  - `radar`/`sensor` (`RadarI`, `SenseI`): can observe enemies. Keep (needed for AI), but policy for hidden-info rules later.
  - `memory`: MemoryBlock read/write gated by `readable`/`writable` (`LogicBlock.java:634-638`) to same team. `GlobalVars`/cross-processor not available to players except via linked memory.
  - `time`: only `@time/@tick/@second/@minute/@wave*` — deterministic, server tick. No wall-clock.
  - `random`: `rand` op on shared `GlobalVars.rand`. Exists; validator may seed/replace for determinism.
  - `links`: `@links`/`getlink` limited to valid same-team links.
  - **cross-team access**: engine blocks *control/read* of alien teams, but *observation* (fetch/radar/sense) crosses teams. This is the main gap for 2v2 and must be closed by the validator + Codefront ownership (not by engine rules).
  - privileged-only instructions (e.g. `setrule`, `spawnunit`, `spawnbullet`, `query`, `setweather`, `applyeffect`, `cutscene`, `message`, `sync`) are **already discarded** when parsing non-privileged player code: `LParser` drops `st.privileged()` statements for `!privileged` (`LParser.java:149-166`). Player processors run as non-privileged, so the dangerous global/cheat instructions are unavailable by default.

### H. Disable human control

`Rules.possessionAllowed = false` alone is **not** sufficient — it blocks unit possession/control (`InputHandler.java:745,775` uses it for `buildSelect`/`control`) but does not by itself stop other interactions. The robust combination:

1. `state.rules.possessionAllowed = false` — no unit possession (`DesktopInput.java:423`, `MobileInput.java:718-721`, `InputHandler.java:775`).
2. Add an `ActionFilter` via `NetServer.admins.addActionFilter(...)` (`net/Administration.java:163-165`, interface `ActionFilter`/`PlayerAction` at `:674-762`) that returns `false` for **every** `ActionType` while `MATCH_RUNNING`. The complete enum (`Administration.java:764-766`): `breakBlock, placeBlock, rotate, configure, withdrawItem, depositItem, control, buildSelect, command, removePlanned, commandUnits, commandBuilding, respawn, pickupBlock, dropPayload, pingLocation`. This is confirmed to gate the server-side build/break placement flow (`NetServer.java:792`).
3. Configure `Rules` so manual combat-affecting channels are closed: `bannedBlocks`/`bannedUnits` (or whitelist), `Rules.schematicsAllowed=false`, disable wave sending (`waveSending=false`), and disable build AI / RTS AI (`buildAi=false`, `rtsAi=false`) so AI doesn't act as a hidden control path.
4. Player unit firing: since `possessionAllowed=false` blocks the `control` ActionType, and the match server need not grant players a combat unit, connected players can be treated as observers. `UnitControlI` and turret logic-control (`TurretBuild.updateTile`) already separate player/`.isPlayer()` from logic control. Additional guard: `CoreBlock.allowSpawn=false` prevents respawn shielding at cores (`CoreBlock.java:51,587`).

Enforcement statement achieved: with (1)+(2)+(3), while `MATCH_RUNNING`, `netServer.admins.allowAction(...)` rejects every client-origin world mutation, so human actions cannot influence combat; the authoritative logic in `Logic` + processors continues untouched.

### I. Point budgets and validation

- **Defense (DP)**: `Block` exposes `size` (`world/Block.java:215`), `requirements` (`:356`), `buildVisibility` (`:362`), `health`, `solid`. `Schematic/Stile`/Codefront list gives per-tile identity, count, position, rotation, and config. All data for whitelist + count + size + point arithmetic is available and stable (content IDs are enum/`Content` refs).
- **Compute (CP)**: `LogicBlock.instructionsPerTick` / `ipt` (`LogicBlock.java:46,265`), `LExecutor.maxInstructions` (`LExecutor.java:41`), and the actual mlog program length are accessible for program-size/capability budgeting. Processor type (`message`, `memory` cells, `LogicDisplay`) known via block config.
- **Robots (RP)**: `UnitType` identity, `weapons` (`UnitType.java:288`), `abilities` (`:286`), health/speed fields, `Units.getCap`/unit cap (`Rules.unitCap`). Whitelist via `Rules.bannedUnits`/`unitWhitelist` and Codefront validator.
- Conclusion: **all required data is accessible and stable enough** for point-budget validation; exact numbers remain ruleset/TBD as designed.

### J. Win condition

- **Primary proposed condition (enemy Team Core destroyed)** is directly detectable:
  - `TeamData.cores` (`game/Teams.java:288`) and `TeamData.isAlive()` = `hasCore()` = `cores.size > 0` (`:455-461`).
  - `CoreChangeEvent` fires when a core is created/removed/destroyed (`CoreBlock.java:536,549,656`). Codefront can listen and, when `enemy.data().cores().isEmpty()`, finalize victory.
  - `BlockDestroyEvent` on a `CoreBuild` is an alternative trigger.
- **Vanilla game-over**: `Logic.checkGameState` (`Logic.java:327-374`) fires `GameOverEvent(winner)` for pvp/attack modes, but only when `runStateCheck` is true, which requires `state.rules.canGameOver` (`Logic.java:509`).
- **Recommendation**: set `Rules.canGameOver = false`, then run Codefront's own winner detector, so the vanilla game-over/restart UI and `sectorCapture` paths do not interfere. Emit the authoritative result record (winner/loser/duration/rulesetVersion/loadout hashes per ARCHITECTURE-v2 §3) — persistence deferred.

### K. Match reset (critical)

One process, `Match 1 -> reset -> Match 2`, is supported:

- **Strategy A (reload arena/map) — preferred.** The game's own "new game" path, `Control.playMap(map, rules)` (`core/Control.java:396-425`), does exactly:
  - `logic.reset()` (`Logic.java:299-313`): `Groups.clear()`, `Time.clear()`, fires `ResetEvent`, `world.tiles = new Tiles(0,0)`, `state.data.unload()`, recreates `state = new GameState()` (a fresh `Teams`, `Rules`, timers), fires `StateChangeEvent(prev, menu)`.
  - `world.loadMap(map, rules)` (`World.java:344`): reloads tiles + entities from the arena file, firing `WorldLoad*Events`.
  - `logic.play()` (`Logic.java:269`): sets `State.playing`, fires `PlayEvent`, heals cores, applies loadout.
- This clears world, entities (via `Groups.clear`), buildings/units, teams (`state.teams` is fresh), processors (buildings removed; processor executors re-created on load), timers/swaps (`state.tick`, `updateId` reset), and game state (new `GameState`).
- **Codefront-specific cleanup required** (not done by vanilla): null out Codefront match-runner singletons, re-register its event listeners once (listeners are `Events.on` registered at init — they persist and must not be double-added), reset action-filter state, and re-apply the Codefront `Rules` for match 2 (since `state.rules` is recreated). Network/client state: on a dedicated match server, keep the same players connected across the reload; `NetServer`/`NetConnection` survive `logic.reset()` (vanilla restart keeps them). `GlobalVars.unitTimeouts` is already cleared on `ResetEvent` (`LExecutor.java:70-72`).
- **Strategy B (manual entity delete/recreate)** is more fragile and not recommended.
- Risks: listener duplication (mitigate with singleton registration), stale custom caches, and `globalVars` state — all manageable. The engine's own restart flow proves the pattern.

### L. Future 2v2 feasibility

- Native `Team` (`game/Team.java`) provides 256 team ids (`Team.all[256]`, `Team.get(id)` at `:28,58`) with per-team data (`Team.data()` → `Teams.TeamData`, `Team.rules()` → `Rules.teams`). Per-team rules (`Rules.TeamRule`) support independent `cheat`/multipliers.
- Required model — one shared team objective/core with separate per-player `PlayerSlot` ownership metadata — is feasible: native `Team` is the lower-level game team (single `core`, shared `TeamData`), and Codefront keeps its own `PlayerSlot -> PlayerLoadout` map and per-slot defense zones/programs above it. This is the preferred architecture and no source blocker was found.
- Ownership isolation: engine already prevents a processor from *controlling* another team's units (`UnitControlI.checkLogicAI`: `unit.team == exec.team`). For 2v2, *teammate* units are same native team, so the engine cannot distinguish A1-owned from A2-owned; **Codefront must enforce PlayerSlot ownership** via a `ProgramValidator` + not exposing cross-slot binds (validator could reject binds to units not carrying the slot's ownership flag or requiring a Codefront-stamped `unit.flag`).
- **TeamBus**: no vanilla bounded bus exists. It will be a Codefront-owned feature (a small codefront message block / shared ownership store), not a vanilla primitive.
- Conclusion: architecture is feasible; the extra work is Codefront-level ownership + validator, not an engine conflict.

---

## 4. Feasibility Matrix

Exactly one status per item. Statuses are justified by the source evidence in §3.

| # | Item | Status |
|---|---|---|
| 1 | Java mod lifecycle | **PASS** (`Mod`, `Mods`) |
| 2 | Server plugin lifecycle | **PASS** (`Plugin`, `ServerLauncher`, `ServerLoadEvent`) |
| 3 | Controlled arena load | **PASS** (`Control.playMap`, `World.loadMap`) |
| 4 | Two-team setup | **PASS** (`Schematics.placeLoadout`→`registerCore`, `Team`) |
| 5 | Fixed base placement | **PASS** (`Schematics.place`/`placeLoadout`, team-aware) |
| 6 | Restricted defense placement | **PASS** (validator coordinates; `ActionFilter`; `Rules.bannedBlocks`) |
| 7 | Resource-free Codefront weapon model | **PASS** (`TeamRule.cheat`→`Building.cheating()`→`updateConsumption`) |
| 8 | Existing robot reuse | **PASS** (`UnitType.spawn`/`create`, `logicControllable`) |
| 9 | mlog execution | **PASS** (`LogicBlock.LogicBuild.updateTile`) |
| 10 | Processor control of robots | **PASS** (`UnitControlI.checkLogicAI`, `LogicAI`) |
| 11 | Disabling human intervention | **PASS WITH CONSTRAINTS** (ActionFilter + `possessionAllowed`; requires applying all surfaces in §H) |
| 12 | Point-budget validation | **PASS** (`Block`/`UnitType`/`LogicBlock`+`LExecutor` data) |
| 13 | Core-based winner detection | **PASS** (`CoreChangeEvent`, `TeamData.cores`, `Rules.canGameOver`) |
| 14 | Authoritative server result | **PASS** (all match logic server-side; headless `Logic`) |
| 15 | Match reset without process restart | **PASS** (`logic.reset()` + `world.loadMap`; `Control.playMap`) |
| 16 | Future 2v2 | **PASS WITH CONSTRAINTS** (native `Team` OK; Codefront ownership + validator required) |
| 17 | Future restricted TeamBus | **PASS WITH CONSTRAINTS** (no vanilla primitive; Codefront feature; no engine blocker) |

---

## 5. Hard Blockers

None of the hard-blocker candidates were found.

- Cannot run fully autonomous battle? — **No.** `Logic` runs headless; units are logic/AI-controlled.
- Cannot prevent manual player influence? — **No.** `possessionAllowed` + `ActionFilter` over all `ActionType`s.
- Cannot place bases reliably? — **No.** `Schematics.place/placeLoadout` are team-aware; mirroring is a small helper.
- Cannot run processor code on headless server? — **No.** `LogicBlock.LogicBuild` runs in the server build update.
- Cannot control robots with processor code? — **No.** `UnitControlI`/`LogicAI`.
- Cannot identify a winner reliably? — **No.** `CoreChangeEvent` + `TeamData.cores`.
- Cannot reset matches without restarting the server? — **No.** `logic.reset()` + `World.loadMap`.
- Cannot isolate untrusted player data from trusted Java code? — **No.** Player payloads are declarative (`DefenseLayout`/`RobotDeck`/mlog/metadata); no player Java/JAR is ever loaded; mlog runs as non-privileged and only acts through the engine's controlled surface.
- Future 2v2 fundamentally conflicts with Mindustry teams? — **No.** Native `Team` suffices as the lower-level team; Codefront `PlayerSlot` sits above.

**No hard platform blocker found for Codefront MVP.**

---

## 6. Architecture Corrections

No accepted game rule or architecture decision required a redesign. Source evidence only refines implementation details consistent with the accepted design:

- **No change to the accepted concept or ARCHITECTURE-v2.** The trust boundaries (trusted mod/plugin vs untrusted declarative loadouts) are confirmed valid; mlog is non-privileged by default which matches the "trusted/untrusted" split.
- **Notes that refine implementation (not redesign):**
  1. Fixed-base and defense *mirroring* must be implemented as a Codefront helper; `Schematics` has rotation but no mirror primitive. Minor.
  2. Resource-free weapons are best achieved with per-team `TeamRule.cheat = true` plus a Codefront-owned weapon `Block` set — not per-tick injection. This is an implementation mechanism, not a rule change; it preserves "weapon type + point cost".
  3. Programs can *observe* (fetch/radar/sense) enemy state even though they cannot *control* it. This strengthens the case for the planned `ProgramValidator` and Codefront-owned ownership policy (needed before 2v2); it is consistent with the accepted "competitive validation may later restrict" wording (GAME-DESIGN-v2 §8). Not a redesign.
  4. The completed audit result was later persisted separately as `docs/audits/CF-MIND-AUD-001-RESULT.md`; the questionnaire `CF-MIND-AUD-001.md` was restored to `origin/main` unchanged.

---

## 7. CF-MIND-SPIKE-001 Proposal

Bounded scope — smallest useful proof. No workshop UI, matchmaking, accounts, ratings, custom robot art, TeamBus, or 2v2.

### Scope (MatchRunner stub)
1. Ship/load **one official Codefront arena** (`.msav` asset wrapping a `Map`; `world.loadMap`).
2. Create a fresh `Rules` (`pvp=true`, `waves=false`, `canGameOver=false`, `schematicsAllowed=false`, `possessionAllowed=false`, clear weather, `unitWhitelist`), keep default teams disabled; assign two opposing `Team`s.
3. Place **two fixed mirrored bases** via `Schematics.place`/`placeLoadout` (Team A at origin A, mirrored copy for Team B at origin B; implement mirror+relink helper).
4. Apply **one different defense configuration per side** (Codefront loadout list; DP sanity check only).
5. Spawn a **small robot group per side** via `UnitType.spawn(team, x, y, rot)`.
6. Configure **one small processor program per side** (non-privileged mlog in base processors).
7. **Disable human match influence**: set `possessionAllowed=false` and install an `ActionFilter` rejecting all `ActionType`s while running.
8. **Start automatically** (server tick → `Logic.play()` state).
9. Let combat resolve; **destroy one side** through normal combat.
10. **Detect winner**: listen `CoreChangeEvent`; when a side's `TeamData.cores()` is empty and `Rules.canGameOver=false`, set Codefront winner.
11. **Log result** (winner team, loser team, duration, ruleset version; loadout hashes later).
12. **Reset**: `logic.reset()` + `world.loadMap(arena)` and Codefront re-init; 
13. **Run a second match** in the same server process, with reversed or alternate defense config; assert a winner and that both matches completed independently.

### Acceptance criteria (all must pass)
- B1. A single headless process runs **two consecutive matches** that both reach a winner, without restarting the JVM.
- B2. Both teams have a placed core and active `TeamData`; pvp validation (`World` requires ≥2 cores) satisfied.
- B3. Processor programs execute and move/attack robots for their own side (observed movement/kills).
- B4. During a running match, all client interactive actions are rejected (ActionFilter) and unit possession is disabled (`possessionAllowed=false`).
- B5. Winner is the side whose opponent's cores are empty; vanilla game-over does not fire (`canGameOver=false`).
- B6. Codefront state is clean between matches (no duplicate listeners, no stale match-runner refs).

### Out of scope (explicit)
Point-balance tuning; ProgramValidator; full DP/RP/CP budgets; persistence; TeamBus; 2v2; content art; UI/spectator HUD.

---

## 8. Open Questions

Only items that genuinely need runtime experiments or further source inspection. These do not block the spike.

1. **Mirroring/processor-link remap**: confirm the exact multiblock offset + `pointConfig`-based link re-bias for a mirrored base (rotation path is `Schematics.rotate`; mirror is custom) — needs a runtime check in the spike.
2. **Power under `cheat`**: confirm power-only turrets run at full fire rate without any power graph when `TeamRule.cheat = true` (source indicates yes via `updateConsumption`, but a live check on `PowerTurret` closes the loop).
3. **Reset leakage**: measure that no Codefront-held references survive `logic.reset()` + reload (listener count, `globalVars`, custom caches) across the two-match spike.
4. **Determinism envelope**: quantify per-match variation in outcomes given the shared `GlobalVars.rand` and frame-order updates — to decide later policy for rated results and whether a deterministic RNG seed/spec is needed for the ProgramValidator.
5. **Client-connect behavior under `possessionAllowed=false` + ActionFilter**: verify a real client connecting mid-match cannot shoot a possessed unit or otherwise act (unit possession is blocked, but confirm no residual shoot path on the server's snapshot/sync for observer players).
6. **`fetch`/`radar` tie-breaking**: for 2v2, confirm the cleanest way to stamp PlayerSlot ownership onto units/buildings so the validator can authorize binds (candidate: Codefront-set `unit.flag` / a Codefront memory-ownership store) — full design deferred to Phase 4.
