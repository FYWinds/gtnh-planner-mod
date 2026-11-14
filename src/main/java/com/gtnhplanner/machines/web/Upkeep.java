package com.gtnhplanner.machines.web;

/**
 * A resource a machine adds to its recipe's ({@link MachineTable.Behaviour#upkeep}): a fluid it burns while it runs, an
 * item each run uses up, or something it gives off. The card shows it as a port of its own, wired and totalled like the
 * recipe's (machines/game UpkeepPorts).
 *
 * @param id     {@code item:<mod>:<name>} with {@code @<meta>} when the meta is not 0, or {@code fluid:<name>}
 * @param amount units a second of running, or a run when {@code perRun}
 * @param perRun whether {@code amount} is a run's rather than a second's
 * @param input  whether the machine takes it rather than gives it off
 */
public record Upkeep(String id, double amount, boolean perRun, boolean input) {

    /** An input it takes so much of every second it runs. */
    public static Upkeep perSecond(final String id, final double amount) {
        return new Upkeep(id, amount, false, true);
    }

    /** An input each run uses up. */
    public static Upkeep perRun(final String id, final double amount) {
        return new Upkeep(id, amount, true, true);
    }

    /** What one craft carries: a run's amount (a second's over the run's seconds) shared by its crafts. */
    public double perCraft(final double runTicks, final double craftsPerRun) {
        final double run = perRun ? amount : amount * runTicks / 20;
        return craftsPerRun > 0 ? run / craftsPerRun : 0;
    }
}
