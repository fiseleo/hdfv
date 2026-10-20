package dev.mcmod.hdfv.airport;

/**
 * Wake turbulence categories by maximum mass (ICAO Doc 4444, 4.9.1): light up to 7 000 kg, medium below 136 000 kg,
 * heavy from 136 000 kg. A tower spaces a lighter aircraft farther behind a heavier one (Separation).
 */
public enum WakeCategory {
    LIGHT, MEDIUM, HEAVY;

    public static WakeCategory of(double massKg) {
        if (!(massKg > 7_000)) { return LIGHT; }
        return massKg < 136_000 ? MEDIUM : HEAVY;
    }
}
