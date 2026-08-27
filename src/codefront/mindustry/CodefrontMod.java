package codefront.mindustry;

import arc.util.Log;
import mindustry.mod.Mod;

/**
 * Minimal bootstrap for platform-feasibility work.
 * Production Codefront systems are intentionally not implemented yet.
 */
public final class CodefrontMod extends Mod {
    public CodefrontMod() {
        Log.info("[Codefront] Mindustry bootstrap loaded.");
    }

    @Override
    public void loadContent() {
        Log.info("[Codefront] Content phase reached.");
    }
}
