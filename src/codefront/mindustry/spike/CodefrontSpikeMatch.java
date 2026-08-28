package codefront.mindustry.spike;

import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Sets up one Codefront spike match inside the (already loaded) world: places the
 * two fixed bases, the per-team defense configuration, spawns the robot groups and
 * configures the mlog processors.
 *
 * <p>The fixed infrastructure (core + processor) is identical for both sides.
 * Defense configurations differ per team and are swapped between Match 1 and
 * Match 2 to prove a fresh configuration is applied after reset.
 */
public final class CodefrontSpikeMatch{

    /** Simple aggregate DP budget for the spike (not final balancing). */
    public static final int DP_LIMIT = 4;

    /**
     * Symmetric, non-privileged mlog program: bind the team's one {@code flare}
     * logic scout and move it toward the arena center. The scout is a different
     * unit type from the (GroundAI) combat daggers so the processor never hijacks
     * the whole army while still proving processor->unit control.
     */
    private static final String PROGRAM =
        "ubind @flare\n" +
        "set tx 60\n" +
        "set ty 60\n" +
        "ucontrol move tx ty 0 0 0\n";

    /** A single declarative defense placement. */
    private static final class Placement{
        final Block block;
        final int rx, ry, rotation, points;
        Placement(Block block, int rx, int ry, int rotation, int points){
            this.block = block;
            this.rx = rx;
            this.ry = ry;
            this.rotation = rotation;
            this.points = points;
        }
    }

    /** Defense configuration 0 (duo on the +X side of the core). */
    private static final Placement[] CONFIG_0 = {
        new Placement(Blocks.duo, 3, -3, 0, CodefrontSpikeRules.DUO_POINT_COST),
        new Placement(Blocks.copperWall, 4, -3, 0, 0),
    };

    /** Defense configuration 1 (duo on the -X side of the core, different rotation). */
    private static final Placement[] CONFIG_1 = {
        new Placement(Blocks.duo, -3, -3, 2, CodefrontSpikeRules.DUO_POINT_COST),
        new Placement(Blocks.copperWall, -4, -3, 0, 0),
    };

    private CodefrontSpikeMatch(){
    }

    /** Performs the full setup for both teams for the given match number. */
    public static void setup(int matchNumber, Rules rules){
        setupTeam(CodefrontSpikeRules.TEAM_A, true, matchNumber, rules);
        setupTeam(CodefrontSpikeRules.TEAM_B, false, matchNumber, rules);
    }

    private static void setupTeam(Team team, boolean teamA, int matchNumber, Rules rules){
        int cx = CodefrontSpikeArena.coreX(teamA);
        int cy = CodefrontSpikeArena.BASE_CORE_Y;

        // 1. Fixed base: core (id identical infra both sides).
        Tile coreTile = tile(cx, cy);
        if(coreTile == null) throw new IllegalStateException("CodefrontSpike: no tile for core at " + cx + "," + cy);
        coreTile.setBlock(Blocks.coreShard, team, 0);
        Block coreBlock = coreTile.build.block;
        if(!(coreBlock instanceof CoreBlock)){
            throw new IllegalStateException("CodefrontSpike: expected CoreBlock at placed core, got " + coreBlock);
        }
        int coreCount = team.data().cores.size;
        if(coreCount == 0){
            throw new IllegalStateException("CodefrontSpike: team " + team + " has no registered core after placement!");
        }
        Log.info("CodefrontSpike BasePlaced Match=@ Team=@ CoreCount=@", matchNumber, team, coreCount);

        // 2. Fixed base: processor (positioned on the core Y axis so it is mirror-invariant).
        Tile procTile = tile(cx, cy + 4);
        procTile.setBlock(Blocks.microProcessor, team, 0);
        if(procTile.build == null){
            throw new IllegalStateException("CodefrontSpike: processor failed to place for " + team);
        }
        LogicBlock.LogicBuild proc = (LogicBlock.LogicBuild)procTile.build;
        proc.configureAny(LogicBlock.compress(PROGRAM, new Seq<>()));

        // 3. Defense configuration (swapped between matches).
        Placements defense = defenseFor(teamA, matchNumber);
        validateAndPlace(team, cx, cy, defense, cryptoPositions(cx, cy));
        int points = sumPoints(defense);
        Log.info("CodefrontSpike DefensePlaced Match=@ Team=@ Config=@ Points=@", matchNumber, team, defense.label, points);

        // 4. Combat robots (GroundAI) and one mlog logic scout.
        spawnRobots(team, teamA, matchNumber);
        spawnMlogScout(team, teamA, matchNumber);

        // 5. Assert processor presence after configuration.
        Log.info("CodefrontSpike ProcessorReady Match=@ Team=@ ProgramBytes=@", matchNumber, team, PROGRAM.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    /** Small holder for a chosen defense configuration with a human label. */
    private static final class Placements{
        final Placement[] list;
        final String label;
        Placements(Placement[] list, String label){
            this.list = list;
            this.label = label;
        }
    }

    private static Placements defenseFor(boolean teamA, int matchNumber){
        boolean useCfg0 = matchNumber == 1 ? teamA : !teamA; // swap between matches
        return useCfg0 ? new Placements(CONFIG_0, "0") : new Placements(CONFIG_1, "1");
    }

    /** Validates the DP sanity rules and then places the defense placements. */
    private static void validateAndPlace(Team team, int cx, int cy, Placements defense, java.util.Set<Long> protectedTiles){
        int total = 0;
        for(Placement p : defense.list){
            if(!CodefrontSpikeRules.isAllowedDefenseBlock(p.block)){
                throw new IllegalStateException("CodefrontSpike: defense block " + p.block + " not in whitelist");
            }
            if(Math.abs(p.rx) > CodefrontSpikeArena.DEFENSE_ZONE_RADIUS || Math.abs(p.ry) > CodefrontSpikeArena.DEFENSE_ZONE_RADIUS){
                throw new IllegalStateException("CodefrontSpike: defense placement " + p.rx + "," + p.ry + " outside defense zone");
            }
            total += p.points;
        }
        if(total > DP_LIMIT){
            throw new IllegalStateException("CodefrontSpike: defense point total " + total + " exceeds " + DP_LIMIT);
        }
        // Place (with mirror for the right-hand team) and check overlap against fixed infrastructure.
        for(Placement p : defense.list){
            int wx = cx + p.rx, wy = cy + p.ry;
            if(protectedTiles.contains(pack(wx, wy))){
                throw new IllegalStateException("CodefrontSpike: defense overlaps fixed infrastructure at " + wx + "," + wy);
            }
            Tile t = tile(wx, wy);
            if(t == null) throw new IllegalStateException("CodefrontSpike: no tile for defense at " + wx + "," + wy);
            t.setBlock(p.block, team, p.rotation);
        }
    }

    private static int sumPoints(Placements defense){
        int s = 0;
        for(Placement p : defense.list) s += p.points;
        return s;
    }

    /** Fixed infrastructure tile positions: core's 3x3 footprint and the processor tile. */
    private static java.util.Set<Long> cryptoPositions(int cx, int cy){
        java.util.Set<Long> out = new java.util.HashSet<>();
        // core 3x3 footprint centered on the core tile (multiblock offset = -(size-1)/2 = -1)
        for(int dx = -1; dx <= 1; dx++){
            for(int dy = -1; dy <= 1; dy++){
                out.add(pack(cx + dx, cy + dy));
            }
        }
        out.add(pack(cx, cy + 4)); // processor
        return out;
    }

    private static long pack(int x, int y){
        return ((long)x) << 32 | (y & 0xffffffffL);
    }

    private static void spawnRobots(Team team, boolean teamA, int matchNumber){
        int count = 0;
        int armySize = teamA ? CodefrontSpikeArena.ROBOTS_TEAM_A : CodefrontSpikeArena.ROBOTS_TEAM_B;
        for(int i = 0; i < armySize; i++){
            int tx = teamA
                ? CodefrontSpikeArena.BASE_A_CORE_X + 5 + i
                : CodefrontSpikeArena.mirrorX(CodefrontSpikeArena.BASE_A_CORE_X + 5 + i);
            Tile t = tile(tx, CodefrontSpikeArena.BASE_CORE_Y);
            if(t == null) continue;
            mindustry.gen.Unit u = UnitTypes.dagger.spawn(team, t.worldx(), t.worldy());
            // In PvP mode a non-AI team's units are idle (CommandAI) unless commanded.
            // Force the standard ground-combat controller so they autonomously seek
            // and attack the enemy core through normal gameplay.
            u.controller(u.type.aiController.get());
            count++;
        }
        Log.info("CodefrontSpike RobotsSpawned Match=@ Team=@ Count=@ ArmySize=@ Type=dagger", matchNumber, team, count, armySize);
    }

    /** Spawns exactly one flying logic scout per side that the processor will bind and drive. */
    private static void spawnMlogScout(Team team, boolean teamA, int matchNumber){
        int tx = teamA
            ? CodefrontSpikeArena.BASE_A_CORE_X + 14
            : CodefrontSpikeArena.mirrorX(CodefrontSpikeArena.BASE_A_CORE_X + 14);
        Tile t = tile(tx, CodefrontSpikeArena.BASE_CORE_Y);
        if(t == null) return;
        UnitTypes.flare.spawn(team, t.worldx(), t.worldy());
        Log.info("CodefrontSpike MlogScoutSpawned Match=@ Team=@ Type=flare", matchNumber, team);
    }

    private static Tile tile(int x, int y){
        return Vars.world.tile(x, y);
    }
}
