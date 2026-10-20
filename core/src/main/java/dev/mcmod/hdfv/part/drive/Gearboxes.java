package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;

/** The kinds of gearbox hdfv designs, and the selector positions its automatic boxes read. */
public final class Gearboxes {
    private Gearboxes() {}

    /** Gearbox kinds and the hardware each is. */
    public enum Kind {
        MANUAL(TransmissionPart.Kind.MANUAL), AUTOMATIC(TransmissionPart.Kind.AUTOMATIC), DUAL_CLUTCH(TransmissionPart.Kind.DUAL_CLUTCH), CVT(TransmissionPart.Kind.CVT);

        public final TransmissionPart.Kind hardware;

        Kind(TransmissionPart.Kind hardware) { this.hardware = hardware; }
    }

    /** The "selector" input's positions. */
    public static final int PARK = -2, REVERSE = -1, NEUTRAL = 0, DRIVE = 1, MANUAL_MODE = 2;
}
