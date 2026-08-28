package codefront.mindustry.spike;

import arc.util.Log;
import mindustry.game.Team;

/**
 * Immutable-after-finalize record of one Codefront spike match result.
 */
public final class CodefrontSpikeResult{

    public final int matchNumber;
    public Team winner;
    public Team loser;
    public long durationTicks;
    /** Result reason, e.g. {@code CORE_DESTROYED} or {@code TIMEOUT}. */
    public String reason = "";

    public CodefrontSpikeResult(int matchNumber){
        this.matchNumber = matchNumber;
    }

    public boolean complete(){
        return winner != null;
    }

    /** Emits the machine-searchable result line. */
    public void log(){
        Log.info(
            "CodefrontSpike MatchResult Match=@ Winner=@ Loser=@ DurationTicks=@ DurationSeconds=@ Reason=@",
            matchNumber,
            winner == null ? "NONE" : winner.name,
            loser == null ? "NONE" : loser.name,
            durationTicks,
            durationTicks / 60.0,
            reason
        );
    }
}
