package codefront.mindustry.spike;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.game.EventType.CoreChangeEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.Team;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import static mindustry.Vars.logic;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

/**
 * Owns the active match lifecycle for CF-MIND-SPIKE-001.
 *
 * A single instance is created per JVM; it registers its event listeners exactly
 * once (guarded by {@link #listenersRegistered}) so that reset never duplicates
 * callbacks. World mutation (reset / arena reload / setup) is always dispatched
 * through {@link arc.Core#app post} so it never runs mid-entity-update, while the
 * per-tick and core-change listeners only read state and schedule transitions.
 */
public final class CodefrontSpikeRunner{

    public static final int MAX_MATCHES = 2;
    /** Safety timeout per match in ticks (240s at 60 tps). */
    private static final long TIMEOUT_TICKS = 60L * 240L;

    public static final CodefrontSpikeRunner INSTANCE = new CodefrontSpikeRunner();

    private boolean listenersRegistered = false;
    private boolean started = false;

    private int matchNumber = 0;
    private State matchState = State.IDLE;
    private CodefrontSpikeResult currentResult;
    private long matchStartTick;
    private boolean finalized;

    // diagnostic counters for reset-leak verification
    private long coreChangeCalls;
    private long matchStartCalls;
    private long matchCompleteCalls;

    private enum State{
        IDLE, LOADING, RUNNING, COMPLETE
    }

    private CodefrontSpikeRunner(){
    }

    /** Called once from {@code CodefrontMod.init()}. Registers all listeners. */
    public void init(){
        if(listenersRegistered) return;
        listenersRegistered = true;

        netServer.admins.addActionFilter(CodefrontSpikeLockout.INSTANCE);

        Events.on(ServerLoadEvent.class, e -> Core.app.post(this::startFirstMatch));
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                onTick();
            }
        });
        Events.on(CoreChangeEvent.class, e -> this.onCoreChange(e.core));

        Log.info("CodefrontSpike ServerReady Listeners=RegisteredOnce");
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    private void onTick(){
        if(matchState == State.RUNNING && !finalized){
            long elapsed = ((long)state.tick) - matchStartTick;
            // periodic combat probe to verify real unit/combat activity
            if(elapsed > 0 && elapsed % 300L == 0L){
                probeCombat();
            }
            // Robust, ordering-independent winner detection: poll the actual core
            // collections every frame instead of relying on CoreChangeEvent timing.
            checkWinCondition();
            if(!finalized && elapsed > TIMEOUT_TICKS){
                Log.info("CodefrontSpike Match=@ Status=FAIL Reason=TIMEOUT", matchNumber);
                finalizeMatch(null, "TIMEOUT");
            }
        }
    }

    /** Declares a winner whenever exactly one native team has no remaining core. */
    private void checkWinCondition(){
        boolean aDead = CodefrontSpikeRules.TEAM_A.data().cores.isEmpty();
        boolean bDead = CodefrontSpikeRules.TEAM_B.data().cores.isEmpty();
        if(!finalized && aDead && !bDead){
            Log.info("CodefrontSpike CoreDestroyed Match=@ Team=@ Winner=@", matchNumber, CodefrontSpikeRules.TEAM_A, CodefrontSpikeRules.TEAM_B);
            finalizeMatch(CodefrontSpikeRules.TEAM_B, "CORE_DESTROYED");
        }else if(!finalized && bDead && !aDead){
            Log.info("CodefrontSpike CoreDestroyed Match=@ Team=@ Winner=@", matchNumber, CodefrontSpikeRules.TEAM_B, CodefrontSpikeRules.TEAM_A);
            finalizeMatch(CodefrontSpikeRules.TEAM_A, "CORE_DESTROYED");
        }
    }

    private void probeCombat(){
        mindustry.gen.Unit a = firstUnit(CodefrontSpikeRules.TEAM_A);
        mindustry.gen.Unit b = firstUnit(CodefrontSpikeRules.TEAM_B);
        int unitsA = mindustry.gen.Groups.unit.count(u -> u.team == CodefrontSpikeRules.TEAM_A);
        int unitsB = mindustry.gen.Groups.unit.count(u -> u.team == CodefrontSpikeRules.TEAM_B);
        int coresA = CodefrontSpikeRules.TEAM_A.data().cores.size;
        int coresB = CodefrontSpikeRules.TEAM_B.data().cores.size;
        Log.info(
            "CodefrontSpike CombatProbe Match=@ Tick=@ UnitsA=@ UnitsB=@ CoresA=@ CoresB=@ " +
            "FirstAPos=(@,@) FirstACtrl=@ FirstBPos=(@,@) FirstBCtrl=@",
            matchNumber, (long)state.tick, unitsA, unitsB, coresA, coresB,
            a == null ? "none" : (int)a.x, a == null ? "none" : (int)a.y,
            a == null ? "none" : a.controller().getClass().getSimpleName(),
            b == null ? "none" : (int)b.x, b == null ? "none" : (int)b.y,
            b == null ? "none" : b.controller().getClass().getSimpleName()
        );
    }

    private static mindustry.gen.Unit firstUnit(Team team){
        for(mindustry.gen.Unit u : mindustry.gen.Groups.unit){
            if(u.team == team) return u;
        }
        return null;
    }

    private void onCoreChange(CoreBuild core){
        coreChangeCalls++;
        // Winner detection is performed by the per-frame checkWinCondition() so the
        // result never depends on the relative ordering of unregisterCore/event.
    }

    // ------------------------------------------------------------------
    // Match flow
    // ------------------------------------------------------------------

    private void startFirstMatch(){
        if(started) return;
        started = true;
        startMatchInternal(1);
    }

    private void startNextMatch(){
        // clear all Codefront match-local state so no stale references survive reset
        currentResult = null;
        finalized = false;
        Log.info("CodefrontSpike Reset Complete PreviousMatchFinished=true MatchNumber=@", matchNumber + 1);
        startMatchInternal(matchNumber + 1);
    }

    private void finalizeMatch(Team winner, String reason){
        if(finalized) return;
        finalized = true;

        CodefrontSpikeLockout.INSTANCE.active = false;
        currentResult.winner = winner;
        currentResult.loser = winner == null ? null
            : (winner == CodefrontSpikeRules.TEAM_A ? CodefrontSpikeRules.TEAM_B : CodefrontSpikeRules.TEAM_A);
        currentResult.durationTicks = Math.max(0L, (long)state.tick - matchStartTick);
        currentResult.reason = reason;
        currentResult.log();

        matchState = State.COMPLETE;
        Log.info("CodefrontSpike Match=@ State=Complete Winner=@", matchNumber, winner == null ? "NONE" : winner.name);
        matchCompleteCalls++;
        counter("MatchComplete", matchCompleteCalls);

        if(matchNumber >= MAX_MATCHES){
            Log.info("CodefrontSpike Status=PASS MatchesCompleted=@", matchNumber);
            // Cleanly terminate the headless server process once acceptance is reached.
            Core.app.exit();
            return;
        }

        Log.info("CodefrontSpike Reset Begin");
        Core.app.post(this::startNextMatch);
    }

    private void startMatchInternal(int n){
        matchNumber = n;
        finalized = false;
        currentResult = new CodefrontSpikeResult(n);
        matchState = State.LOADING;
        matchStartCalls++;
        Log.info("CodefrontSpike Match=@ State=Loading", n);
        counter("MatchStart", matchStartCalls);

        // Fresh rules every match, then a full reset + world rebuild through the
        // supported Mindustry reload path (Logic.reset + World.loadGenerator).
        var rules = CodefrontSpikeRules.newRules();
        logic.reset();
        state.rules = rules;
        world.loadGenerator(CodefrontSpikeArena.WIDTH, CodefrontSpikeArena.HEIGHT, CodefrontSpikeArena::fill);
        Log.info("CodefrontSpike ArenaLoaded Match=@ Size=@x@", n, CodefrontSpikeArena.WIDTH, CodefrontSpikeArena.HEIGHT);

        CodefrontSpikeMatch.setup(n, rules);

        logic.play();
        matchState = State.RUNNING;
        matchStartTick = (long)state.tick;
        CodefrontSpikeLockout.INSTANCE.active = true;
        logLeakChecks(n);
        Log.info("CodefrontSpike Match=@ State=Running", n);
    }

    /** Emits the reset-leak verification line required before Match 2 (and after setup of every match). */
    private void logLeakChecks(int n){
        int coreA = CodefrontSpikeRules.TEAM_A.data().cores.size;
        int coreB = CodefrontSpikeRules.TEAM_B.data().cores.size;
        int unitsA = mindustry.gen.Groups.unit.count(u -> u.team == CodefrontSpikeRules.TEAM_A);
        int unitsB = mindustry.gen.Groups.unit.count(u -> u.team == CodefrontSpikeRules.TEAM_B);
        Log.info(
            "CodefrontSpike LeakCheck Match=@ TeamACores=@ TeamBCores=@ TeamAUnits=@ TeamBUnits=@ MatchNumber=@",
            n, coreA, coreB, unitsA, unitsB, n
        );
        counter("CoreChange", coreChangeCalls);
    }

    private static void counter(String name, long value){
        Log.info("CodefrontSpike Counter @=@", name, value);
    }

    /** @return whether the active match is currently running (used by external checks/tests). */
    public boolean isMatchRunning(){
        return matchState == State.RUNNING;
    }
}
