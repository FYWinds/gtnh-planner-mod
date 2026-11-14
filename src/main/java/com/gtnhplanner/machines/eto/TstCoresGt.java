package com.gtnhplanner.machines.eto;

import javax.annotation.Nullable;

/**
 * GregTech 5.09.54's ProcessingLogic for one recipe whose inputs never run short, as the TwistSpaceTechnology 0.8.1
 * machines that pass their own voltage, amperage and parallel cap call it: GTCM_ParallelHelper.determineParallel (the
 * power's parallels, the sub-tick bonus; unlike GregTech's own helper it has no one-tier-above check) and
 * OverclockCalculator.calculateOverclock, with the same integer and floating-point steps, so a run comes out to the
 * game's tick and EU/t. Pure: no game classes.
 */
final class TstCoresGt {

    private TstCoresGt() {}

    /** One check's result: the parallels that run, the EU/t the run draws and its ticks. */
    record Run(int parallels, long eut, int duration) {}

    /** What the machine hands ProcessingLogic: its power, parallel cap, modifiers and overclock factors. */
    static final class Setup {

        long voltage;
        long amperage = 1;
        /** ProcessingLogic.amperageOC, true unless the machine turns it off. */
        boolean amperageOC = true;
        int maxParallel = 1;
        /** setEuModifier: the machine's float, widened. */
        double euModifier = 1;
        /** setSpeedBonus: the duration multiplier, the machine's float widened. */
        double speedBoost = 1;
        /** OverclockType: duration divided per step, EU/t multiplied per step (2/4 normal, 4/4 perfect). */
        double timeReduction = 2, powerIncrease = 4;
        /** OverclockCalculator.ofNoOverclock: the recipe's own EU/t and duration, no modifiers. */
        boolean noOverclock;

        Setup voltage(final long v) {
            voltage = v;
            return this;
        }

        Setup amperage(final long a) {
            amperage = a;
            return this;
        }

        Setup amperageOC(final boolean on) {
            amperageOC = on;
            return this;
        }

        Setup maxParallel(final int p) {
            maxParallel = p;
            return this;
        }

        Setup euModifier(final double m) {
            euModifier = m;
            return this;
        }

        Setup speedBoost(final double s) {
            speedBoost = s;
            return this;
        }

        Setup overclock(final double timeReduction, final double powerIncrease) {
            this.timeReduction = timeReduction;
            this.powerIncrease = powerIncrease;
            return this;
        }

        Setup noOverclock() {
            noOverclock = true;
            return this;
        }
    }

    /**
     * ProcessingLogic.validateAndCalculateRecipe for a recipe of {@code recipeEut} EU/t and {@code recipeDuration}
     * ticks; null where GTCM_ParallelHelper refuses it (the power cannot pay one parallel, :86-90).
     */
    @Nullable
    static Run process(final Setup s, final long recipeEut, final int recipeDuration) {
        if (s.maxParallel <= 0) return null;
        // ofNoOverclock: the calculator runs at the recipe's own EU/t, without the machine's modifiers.
        final double eutModifier = s.noOverclock ? 1 : s.euModifier;
        final double durationModifier = s.noOverclock ? 1 : s.speedBoost;
        final long available = multiply(s.voltage, s.amperage);
        // GTCM_ParallelHelper.determineParallel (:84-90): the helper bills the machine's own modifier.
        final long billed = (long) Math.ceil(recipeEut * s.euModifier);
        if (available < billed) return null;
        final int original = s.maxParallel;
        final int maxParallel = safeInt(
            (long) (original * underOneTick(s, eutModifier, durationModifier, recipeEut, recipeDuration, original)));
        final int parallels = billed > 0 ? (int) Math.min(maxParallel, available / billed) : maxParallel;
        if (parallels <= 0) return null;
        // OverclockCalculator.setCurrentParallel: the overclock bills at most the original cap.
        final int paid = Math.min(original, parallels);
        final double recipePower = recipeEut * paid * eutModifier;
        double duration = recipeDuration * durationModifier;
        if (s.noOverclock) return new Run(parallels, (long) Math.ceil(recipePower), (int) Math.ceil(duration));
        final double machinePower = (double) (s.voltage * (s.amperageOC ? s.amperage : Math.min(s.amperage, paid)));
        int overclocks = tiersAbove(machinePower, recipePower);
        if (!s.amperageOC) overclocks = Math
            .min(overclocks, Math.max(log4ceil(s.voltage / 8), 1) - Math.max(log4ceil(recipeEut / 8), 1));
        overclocks = Math.max(overclocks, 0);
        final long eut = (long) Math.ceil(recipePower * Math.pow(s.powerIncrease, overclocks));
        duration /= Math.pow(s.timeReduction, overclocks);
        return new Run(parallels, eut, (int) Math.max(duration, 1));
    }

    /** OverclockCalculator.calculateMultiplierUnderOneTick, no heat: the parallels a sub-tick run banks. */
    private static double underOneTick(final Setup s, final double eutModifier, final double durationModifier,
        final long recipeEut, final int recipeDuration, final int parallel) {
        if (s.noOverclock) return 1;
        final double duration = recipeDuration * durationModifier;
        final double recipePower = recipeEut * parallel * eutModifier;
        final double machinePower = (double) (s.voltage * (s.amperageOC ? s.amperage : Math.min(s.amperage, parallel)));
        final int voltageTierRecipe = Math.max(log4ceil(recipeEut / 8), 1);
        final int voltageTierMachine = Math.max(log4ceil(s.voltage / 8), 1);
        final int overclocks = Math.min(
            s.amperageOC ? tiersAbove(machinePower, recipePower) : voltageTierMachine - voltageTierRecipe,
            Integer.MAX_VALUE);
        final int heat = Math.min(0, overclocks);
        final int regular = overclocks - heat;
        final double afterHeat = duration / Math.pow(4, heat);
        final int neededHeat = (int) Math.ceil(Math.log(duration) / Math.log(4));
        final int neededRegular = (int) Math.ceil(Math.log(afterHeat) / Math.log(s.timeReduction));
        final double heatMultiplier = Math.pow(4, Math.max(heat - neededHeat, 0));
        final double regularMultiplier = Math.pow(s.timeReduction, Math.max(regular - neededRegular, 0));
        final double correction;
        if (heat >= neededHeat) correction = Math.pow(4, neededHeat) / duration;
        else if (regular >= neededRegular) correction = Math.pow(s.timeReduction, neededRegular) / afterHeat;
        else return 1.0;
        return Math.ceil(heatMultiplier * regularMultiplier * correction);
    }

    /** OverclockCalculator.getTiersAbove: whole powers of four of {@code power} over {@code base} (32 at least). */
    static int tiersAbove(final double power, final double base) {
        if (power < base) return -1;
        if (power < Long.MAX_VALUE / 100) {
            final long scaledPower = Math.round(power * 100);
            final long scaledBase = Math.round(base * 100);
            return (int) log4(scaledPower / Math.max(scaledBase, 32L * 100));
        }
        return (int) log4((long) (power / Math.max(base, 32.0)));
    }

    /** GTUtility.log4(long). */
    static long log4(final long a) {
        if (a <= 1) return 0;
        return 63 - Long.numberOfLeadingZeros(a) >> 1;
    }

    /** GTUtility.log4ceil(long). */
    static int log4ceil(final long a) {
        if (a <= 1) return 0;
        return 65 - Long.numberOfLeadingZeros(a - 1) >> 1;
    }

    /** GTUtility.safeInt(number, 0). */
    private static int safeInt(final long number) {
        return number > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) number;
    }

    /** a x b, held at Long.MAX_VALUE. */
    static long multiply(final long a, final long b) {
        if (a == 0 || b == 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
