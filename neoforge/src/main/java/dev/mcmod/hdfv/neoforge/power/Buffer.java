package dev.mcmod.hdfv.neoforge.power;

import net.neoforged.neoforge.energy.EnergyStorage;

/** A block's Forge Energy buffer (0.8): what its capability lets others put in or take out, and what the block itself adds and spends. */
public class Buffer extends EnergyStorage {
    private final Runnable changed;

    public Buffer(int capacity, int maxReceive, int maxExtract, Runnable changed) {
        super(Math.max(capacity, 1), maxReceive, maxExtract);
        this.changed = changed;
    }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        int r = super.receiveEnergy(amount, simulate);
        if (r > 0 && !simulate) { changed.run(); }
        return r;
    }

    @Override public int extractEnergy(int amount, boolean simulate) {
        int r = super.extractEnergy(amount, simulate);
        if (r > 0 && !simulate) { changed.run(); }
        return r;
    }

    /** The block spends some (at most what it has); what it spent. */
    public int use(int fe) {
        int n = Math.clamp(fe, 0, energy);
        energy -= n;
        if (n > 0) { changed.run(); }
        return n;
    }

    /** The block makes some (at most the room); what went in. */
    public int add(int fe) {
        int n = Math.clamp(fe, 0, capacity - energy);
        energy += n;
        if (n > 0) { changed.run(); }
        return n;
    }

    public int room() { return capacity - energy; }

    public void set(int fe) { energy = Math.clamp(fe, 0, capacity); }
}
