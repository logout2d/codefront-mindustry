package codefront.mindustry.spike;

import arc.util.Log;
import mindustry.net.Administration.ActionFilter;
import mindustry.net.Administration.PlayerAction;

/**
 * Server-side lockout shutting down every player interaction surface while a
 * Codefront match is running.
 *
 * The filter rejects <strong>every</strong> {@code Administration.ActionType}
 * while {@link #active} is true, so the server (authoritative logic + mlog) keeps
 * running but no connected client can mutate or command the active match.
 *
 * The filter is registered exactly once per JVM; only the {@link #active} flag is
 * toggled between matches.
 */
public final class CodefrontSpikeLockout implements ActionFilter{

    public static final CodefrontSpikeLockout INSTANCE = new CodefrontSpikeLockout();

    /** When true, every player action is rejected. */
    public volatile boolean active = false;

    private CodefrontSpikeLockout(){
    }

    @Override
    public boolean allow(PlayerAction action){
        if(active){
            Log.info("CodefrontSpike LockoutEnabled RejectedType=@", action.type);
            return false;
        }
        return true;
    }
}
