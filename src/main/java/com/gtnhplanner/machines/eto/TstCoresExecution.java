package com.gtnhplanner.machines.eto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * TwistSpaceTechnology 0.8.1's execution-core machines (MultiExecutionCoreMachineBase: the DTPF Prototype MK-II and the
 * Large Neutron Oscillator) running one recipe whose inputs never run short, simulated tick by tick as the controller
 * and its Execution Core hatches run it, until the state repeats; the card takes the repeating stretch's average.
 * <ul>
 * <li>The hatches' budget is {@code (long) (0.975 x getMaxInputEu())} (checkMachine, :146-147).</li>
 * <li>The controller is itself a core. Idle cores are checked when one finishes (forceCheckProcessing) and every
 * 64-tick base cycle: Advanced cores first, each at the whole spare budget; then the plain cores and the controller,
 * each at the spare budget over the idle ones left (checkProcessingForNormalExecutionCore, :764-790), 1 A, each capped
 * at 1 + parallels / (cores + 1) (getParallelOfEveryNormalExecutionCore, :233-240). A failed check leaves the rest
 * idle.</li>
 * <li>Each tick the spare budget boosts the working cores in turn (boostExecutionCoreProcessing, :567-616): a
 * perfect overclock type buys a tick per core EU/t, the others 2^n - 1 ticks for (4^n - 1) x core EU/t.</li>
 * <li>A plain core finishes when its progress reaches its duration less one (ExecutionCoreBase :156-186); the
 * controller one tick later (:280-309). An Advanced core pays its EU/t x duration from the wireless network when it
 * starts (AdvExecutionCore.done) and never from the hatches.</li>
 * </ul>
 * Pure: no game classes.
 */
final class TstCoresExecution {

    private TstCoresExecution() {}

    /** The base run cycle (progressingTick[5], index 5 by default): the controller re-checks idle cores this often. */
    static final int BASE_CYCLE = 64;

    /** What the machine is built with and runs. */
    record Setup(long hatchEu, long recipeEut, int recipeDuration, double euModifier, double speedBonus,
        int timeReduction, int powerIncrease, int totalParallels, int cores, int advancedCores) {

        boolean perfectBoost() {
            return timeReduction >= powerIncrease;
        }
    }

    /**
     * The steady run: {@code parallels} the cores hold at once, recipes a tick, the hatches' EU/t and the network's
     * EU/t, all averaged over the repeating stretch.
     */
    record Result(int parallels, double recipesPerTick, double hatchEuT, double networkEuT) {

        /** The ticks {@code parallels} recipes take on average. */
        double duration() {
            return parallels / recipesPerTick;
        }
    }

    private static final double LOG2 = Math.log(2), LOG4 = Math.log(4);
    private static final int MAX_TICKS = 4_000_000;

    private static final Map<Setup, Result> CACHE = new LinkedHashMap<>(64, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(final Map.Entry<Setup, Result> eldest) {
            return size() > 256;
        }
    };

    /** The steady run, or null when no core can ever start (the first share cannot pay one parallel). */
    @Nullable
    static Result run(final Setup setup) {
        synchronized (CACHE) {
            if (CACHE.containsKey(setup)) return CACHE.get(setup);
        }
        final Result result = new Simulation(setup).run();
        synchronized (CACHE) {
            CACHE.put(setup, result);
        }
        return result;
    }

    /** getParallelOfEveryNormalExecutionCore: the cap each core checks with. */
    static int parallelPerCore(final int totalParallels, final int executionCores) {
        if (executionCores <= 0) return totalParallels;
        final int coreAmount = executionCores + 1;
        if (coreAmount >= totalParallels) return 1;
        return 1 + totalParallels / coreAmount;
    }

    private static final class Core {

        final boolean main, advanced;
        int max, progress, parallels;
        long eut;

        Core(final boolean main, final boolean advanced) {
            this.main = main;
            this.advanced = advanced;
        }

        boolean working() {
            return max > 0;
        }

        void reset() {
            max = 0;
            progress = 0;
            parallels = 0;
            eut = 0;
        }
    }

    private static final class Simulation {

        final Setup s;
        final long budget;
        final Core main = new Core(true, false);
        final List<Core> plain = new ArrayList<>(), advanced = new ArrayList<>();
        final TstCoresGt.Setup logic;
        /** The controller's lEUt: minus the EU/t its plain cores hold. */
        long used;
        boolean needCheck = true;
        long recipes, hatchEu, networkEu;
        boolean everStarted;

        Simulation(final Setup s) {
            this.s = s;
            budget = (long) (0.975d * s.hatchEu);
            for (int i = 0; i < s.cores; i++) plain.add(new Core(false, false));
            for (int i = 0; i < s.advancedCores; i++) advanced.add(new Core(false, true));
            logic = new TstCoresGt.Setup().amperage(1)
                .maxParallel(parallelPerCore(s.totalParallels, s.cores))
                .euModifier(s.euModifier)
                .speedBoost(s.speedBonus)
                .overclock(s.timeReduction, s.powerIncrease);
        }

        long spare() {
            return budget - used;
        }

        @Nullable
        Result run() {
            final Map<Fingerprint, long[]> seen = new HashMap<>();
            final long[] half = { -1, 0, 0, 0 };
            boolean boostedLast = true;
            for (long tick = 0; tick < MAX_TICKS; tick++) {
                if (!boostedLast && !anyIdle()) {
                    // Nothing boosts and nothing ends before the next finish: let the cores run on to it, short of
                    // the base cycle's next check.
                    final long skip = Math.min(ticksToNextFinish() - 1, BASE_CYCLE - 1 - tick % BASE_CYCLE);
                    if (skip > 0) {
                        for (final Core c : plain) c.progress += (int) skip;
                        for (final Core c : advanced) c.progress += (int) skip;
                        main.progress += (int) skip;
                        hatchEu += skip * used;
                        tick += skip;
                    }
                }
                if (half[0] < 0 && tick >= MAX_TICKS / 2) {
                    half[0] = tick;
                    half[1] = recipes;
                    half[2] = hatchEu;
                    half[3] = networkEu;
                }
                // The hatches' cores (ExecutionCoreBase.runExecutionCoreTick).
                for (final Core c : plain) hatchTick(c);
                for (final Core c : advanced) hatchTick(c);
                // The controller's onPreTick: its own core, then the check when one finished.
                if (main.working()) {
                    if (main.progress < main.max) main.progress++;
                    else finish(main);
                }
                Result repeat = null;
                if (needCheck) {
                    check();
                    if (!everStarted) return null;
                    repeat = snapshot(seen, tick, 0);
                }
                // The controller's onRunningTick: the spare EU/t boosts, the hatches pay base and boost.
                final long boost = boost();
                boostedLast = boost > 0;
                hatchEu += used + boost;
                // The end of the base cycle: checkProcessingMM checks the idle cores again.
                if (repeat == null && tick % BASE_CYCLE == BASE_CYCLE - 1 && anyIdle()) {
                    check();
                    repeat = snapshot(seen, tick, 1);
                }
                if (repeat != null) return repeat;
            }
            final double ticks = MAX_TICKS - half[0];
            return result((recipes - half[1]) / ticks, (hatchEu - half[2]) / ticks, (networkEu - half[3]) / ticks);
        }

        /** The average since this state was last seen at the same point of a tick, or null the first time. */
        @Nullable
        Result snapshot(final Map<Fingerprint, long[]> seen, final long tick, final long point) {
            final Fingerprint key = fingerprint(tick, point);
            final long[] then = seen.get(key);
            if (then != null) {
                final double ticks = tick - then[0];
                return result((recipes - then[1]) / ticks, (hatchEu - then[2]) / ticks, (networkEu - then[3]) / ticks);
            }
            if (seen.size() < 1 << 18) seen.put(key, new long[] { tick, recipes, hatchEu, networkEu });
            return null;
        }

        Result result(final double recipesPerTick, final double hatchEuT, final double networkEuT) {
            long held = main.parallels;
            for (final Core c : plain) held += c.parallels;
            for (final Core c : advanced) held += c.parallels;
            final int parallels = (int) Math.max(1, Math.min(Integer.MAX_VALUE, held));
            return new Result(parallels, recipesPerTick, hatchEuT, networkEuT);
        }

        boolean anyIdle() {
            if (!main.working()) return true;
            for (final Core c : plain) if (!c.working()) return true;
            for (final Core c : advanced) if (!c.working()) return true;
            return false;
        }

        /** Ticks until the first core's finishing tick, counting this one. */
        long ticksToNextFinish() {
            long next = (long) main.max - main.progress + 1;
            for (final Core c : plain) next = Math.min(next, (long) c.max - 1 - c.progress + 1);
            for (final Core c : advanced) next = Math.min(next, (long) c.max - 1 - c.progress + 1);
            return next;
        }

        void hatchTick(final Core c) {
            if (!c.working()) return;
            if (c.progress < c.max - 1) c.progress++;
            else finish(c);
        }

        void finish(final Core c) {
            recipes += c.parallels;
            if (!c.advanced && used >= c.eut) used -= c.eut;
            c.reset();
            needCheck = true;
        }

        /** doCheckRecipeForExecutionCores, Advanced cores then the plain ones and the controller. */
        void check() {
            needCheck = false;
            for (final Core c : advanced) {
                if (c.working()) continue;
                if (!start(c, spare())) break;
            }
            final List<Core> idle = new ArrayList<>();
            for (final Core c : plain) if (!c.working()) idle.add(c);
            if (!main.working()) idle.add(main);
            int idleAmount = idle.size();
            for (final Core c : idle) {
                final long share = spare() / idleAmount;
                idleAmount--;
                if (!start(c, share)) break;
            }
        }

        boolean start(final Core c, final long voltage) {
            logic.voltage(voltage);
            final TstCoresGt.Run r = TstCoresGt.process(logic, s.recipeEut, s.recipeDuration);
            if (r == null) return false;
            everStarted = true;
            c.max = r.duration();
            c.progress = 0;
            c.eut = r.eut();
            c.parallels = r.parallels();
            if (c.advanced) networkEu += r.eut() * r.duration();
            else if (spare() >= r.eut()) used += r.eut();
            return true;
        }

        /** boostExecutionCoreProcessing: the EU/t spent boosting this tick. */
        long boost() {
            final List<Core> working = new ArrayList<>();
            if (main.working()) working.add(main);
            for (final Core c : plain) if (c.working()) working.add(c);
            for (final Core c : advanced) if (c.working()) working.add(c);
            int waiting = working.size();
            if (waiting == 0) return 0;
            long canUse = spare();
            if (canUse < waiting) return 0;
            long spent = 0;
            for (final Core c : working) {
                final long average = canUse / waiting;
                waiting--;
                final int need = c.max - c.progress;
                if (need < 1 || c.eut <= 0) continue;
                final long extra = average / c.eut;
                final int ticks;
                final long cost;
                if (s.perfectBoost()) {
                    ticks = extra > need ? need : (int) extra;
                    cost = ticks * c.eut;
                } else {
                    final int times = (int) Math.min(Math.log(extra + 1) / LOG4, Math.log(need) / LOG2);
                    ticks = (int) Math.pow(2, times) - 1;
                    cost = (long) ((Math.pow(4, times) - 1) * c.eut);
                }
                canUse -= cost;
                spent += cost;
                c.progress += ticks;
            }
            return spent;
        }

        /**
         * Everything the run's future depends on at this point of a tick, hashed twice (128 bits): the base cycle's
         * phase, the controller's held EU/t, and every core's duration, progress, EU/t and parallels in order.
         */
        Fingerprint fingerprint(final long tick, final long point) {
            final long[] h = { 0x9E3779B97F4A7C15L, 0xC2B2AE3D27D4EB4FL };
            mix(h, tick % BASE_CYCLE);
            mix(h, point);
            mix(h, used);
            mix(h, main);
            for (final Core c : plain) mix(h, c);
            for (final Core c : advanced) mix(h, c);
            return new Fingerprint(h[0], h[1]);
        }

        private static void mix(final long[] h, final Core c) {
            mix(h, c.max);
            mix(h, c.progress);
            mix(h, c.eut);
            mix(h, c.parallels);
        }

        private static void mix(final long[] h, final long v) {
            h[0] = Long.rotateLeft(h[0] ^ v * 0xBF58476D1CE4E5B9L, 31) * 0x94D049BB133111EBL;
            h[1] = Long.rotateLeft(h[1] + v * 0xFF51AFD7ED558CCDL, 27) * 0xC4CEB9FE1A85EC53L + 0x52DCE729L;
        }
    }

    /** A state's 128-bit hash. */
    private record Fingerprint(long a, long b) {}
}
