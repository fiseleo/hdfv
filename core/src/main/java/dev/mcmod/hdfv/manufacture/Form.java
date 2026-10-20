package dev.mcmod.hdfv.manufacture;

/**
 * What a workpiece starts from (0.7): a casting poured from ingots, a forging from a billet, bar, plate or sheet
 * rolled at the press, wire, a laminate laid up from fibre and resin, rubber for a tyre, timber.
 */
public enum Form {
    CASTING, FORGING, BAR, PLATE, SHEET, WIRE, LAMINATE, RUBBER, TIMBER, TUBE;

    /** Share of the stock that ends up in the part (the rest is chips, flash or trimmings), the planners' starting point. */
    public double yield() {
        return switch (this) {
            case CASTING -> 0.92;      // a few mm of machining allowance over the casting
            case FORGING -> 0.75;      // flash and a forging's allowance
            case BAR -> 0.5;           // turned from round bar
            case PLATE -> 0.2;         // milled from plate: most goes to chips (aerospace "buy-to-fly" 5-20)
            case SHEET -> 0.9;         // trimmed sheet
            case WIRE, TIMBER -> 0.8;
            case LAMINATE -> 0.9;
            case RUBBER -> 0.95;
            case TUBE -> 0.85;         // 0.15: cut and mitred from drawn tube (chassis tubes and rails)
        };
    }
}
