package codefront.mindustry;

import arc.util.Log;
import codefront.mindustry.spike.CodefrontSpikeRunner;
import mindustry.mod.Mod;

/**
 * Codefront mod bootstrap.
 *
 * For CF-MIND-SPIKE-001 this only wires up the single-runner so that listeners are
 * registered exactly once per JVM. Production Codefront systems are intentionally
 * not implemented yet.
 */
public final class CodefrontMod extends Mod {
    public CodefrontMod() {
        Log.info("[Codefront] Mindustry bootstrap loaded.");
    }

    @Override
    public void loadContent() {
        Log.info("[Codefront] Content phase reached.");
    }

    @Override
    public void init() {
        // Single registration point for the spike runner (listeners are guarded).
        CodefrontSpikeRunner.INSTANCE.init();
    }
}
