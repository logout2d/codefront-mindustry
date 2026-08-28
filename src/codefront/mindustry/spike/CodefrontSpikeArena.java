package codefront.mindustry.spike;

import mindustry.content.Blocks;
import mindustry.world.Tile;
import mindustry.world.Tiles;

/**
 * Deterministic, symmetric Codefront spike arena.
 *
 * The arena is <em>generated at runtime</em> and rebuilt each match through the
 * supported Mindustry world-load cycle ({@code World.loadGenerator(...)} ->
 * begin/end map load), which fires the same {@code WorldLoad*} event sequence as
 * {@code World.loadMap} and rebuilds the whole tile graph. No {@code .msav}
 * binary is required, and reset genuinely reloads the world rather than deleting
 * units manually.
 */
public final class CodefrontSpikeArena{

    public static final int WIDTH = 120;
    public static final int HEIGHT = 120;

    /** Horizontal symmetry axis (tile column at which the map mirrors). */
    public static final int CENTER_X = WIDTH / 2;

    /** Base slot core centers, in tile coordinates, mirrored across {@link #CENTER_X}. */
    public static final int BASE_A_CORE_X = 22;
    public static final int BASE_B_CORE_X = WIDTH - 1 - BASE_A_CORE_X; // 97
    public static final int BASE_CORE_Y = 60;

    /**
     * Ground robot counts per team. Deliberately asymmetric so the stronger army
     * reliably breaks through the enemy defense and destroys the enemy core
     * through real combat; with two equal forces the armies annihilate each other
     * in the open field and no core falls (spike target: the combat/winner
     * pipeline, not balanced PvP).
     */
    public static final int ROBOTS_TEAM_A = 12;
    public static final int ROBOTS_TEAM_B = 4;

    /** How many tiles a side may operate within its own half (defense zone bound). */
    public static final int DEFENSE_ZONE_RADIUS = 7;

    private CodefrontSpikeArena(){
    }

    /**
     * Fills the world tile array with a flat, open floor. Called from the
     * {@code World.loadGenerator} generator callback.
     */
    public static void fill(Tiles tiles){
        int floor = Blocks.stone.id;
        int air = Blocks.air.id;
        for(int x = 0; x < WIDTH; x++){
            for(int y = 0; y < HEIGHT; y++){
                tiles.set(x, y, new Tile(x, y, floor, air, air));
            }
        }
    }

    /** @return the mirrored X coordinate of {@code localX} across the map center. */
    public static int mirrorX(int localX){
        return (WIDTH - 1) - localX;
    }

    /** @return the core center tile X for the given native team slot. */
    public static int coreX(boolean teamA){
        return teamA ? BASE_A_CORE_X : BASE_B_CORE_X;
    }
}
