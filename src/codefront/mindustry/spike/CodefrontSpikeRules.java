package codefront.mindustry.spike;

import mindustry.content.Blocks;
import mindustry.game.Rules;
import mindustry.game.Team;

/**
 * Builds a fresh, fully-specified Codefront spike {@link Rules} instance.
 *
 * A new instance is constructed for every match so reset never reuses a mutable
 * rules object carrying stale runtime state (see audit Section K).
 */
public final class CodefrontSpikeRules{

    /** Native team used for slot A. */
    public static final Team TEAM_A = Team.blue;
    /** Native team used for slot B. */
    public static final Team TEAM_B = Team.green;

    /** Simple aggregate point cost attributed to a duo turret for the DP sanity check. */
    public static final int DUO_POINT_COST = 1;

    private CodefrontSpikeRules(){
    }

    /** @return the fresh rules instance for a match. */
    public static Rules newRules(){
        Rules r = new Rules();

        // PvP: two controlled teams, no waves, no campaign.
        r.pvp = true;
        r.waves = false;
        r.waveTimer = false;
        r.waveSending = false;
        r.waitEnemies = false;
        r.attackMode = false;
        r.editor = false;

        // Codefront owns result detection; vanilla game-over must not fire.
        r.canGameOver = false;

        // Human lockout baseline (enforced live by the ActionFilter while running).
        r.possessionAllowed = false;
        r.schematicsAllowed = false;

        // Processors may drive units; no builder/deconstruct via logic.
        r.logicUnitControl = true;
        r.logicUnitBuild = false;
        r.logicUnitDeconstruct = false;

        // Dead (core-less) teams are cleaned up to derelict in PvP; cores cannot be captured.
        r.cleanupDeadTeams = true;
        r.coreCapture = false;
        r.coreIncinerates = true;

        // Combat / economy normalisation: resource-free operation for both teams.
        r.teams.get(TEAM_A).cheat = true;
        r.teams.get(TEAM_B).cheat = true;
        r.teams.get(TEAM_A).fillItems = true;
        r.teams.get(TEAM_B).fillItems = true;
        r.infiniteResources = false;

        // No economy gameplay: empty loadout and no spawns.
        r.loadout.clear();
        r.spawns.clear();

        // No weather, no fog, no unit cap.
        r.weather.clear();
        r.fog = false;
        r.disableUnitCap = true;
        r.unitCap = 0;

        r.defaultTeam = TEAM_A;
        r.waveTeam = Team.derelict;

        // Keep everything else in a controlled, predictable state.
        r.dragMultiplier = 1f;
        r.blockDamageMultiplier = 1f;
        r.unitDamageMultiplier = 1f;
        r.unitHealthMultiplier = 1f;
        r.blockHealthMultiplier = 1f;

        // No hidden campaign dependents.
        r.bannedBlocks.clear();
        r.bannedUnits.clear();

        // Reference used only to guarantee the block is available for the whitelist check.
        @SuppressWarnings("unused") var unused = Blocks.air;
        return r;
    }

    /** @return whether the given block is allowed in a Codefront spike defense zone. */
    public static boolean isAllowedDefenseBlock(mindustry.world.Block block){
        return block == Blocks.duo || block == Blocks.copperWall;
    }
}
