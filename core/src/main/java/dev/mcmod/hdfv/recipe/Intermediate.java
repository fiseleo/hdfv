package dev.mcmod.hdfv.recipe;

import java.util.Locale;

/**
 * The intermediate items that are not standard components (0.23): assemblies put together at the assembly bench from
 * components (an electric motor, a transformer, a control board...), and the metallurgy's intermediates (alumina,
 * ferroalloys, titanium slag and sponge...). Their masses (kg) are what the game-scale rules (GameScale) count.
 *
 * <ul>
 * <li>Assemblies weigh what goes into them (tools/recipes.py writes their recipes; RecipeRulesTest checks the sum).</li>
 * <li>Each metallurgy intermediate is 10 kg like an ingot, but for magnesium chloride (the chloride of one ingot of
 *     magnesium: 39 kg) and the carbon anode (the 4.2 kg of carbon a 10 kg ingot of aluminium burns, AeroCore
 *     Electrolysis.anodeCarbonKgPerKg times 1.25, rounded up to 5 kg).</li>
 * </ul>
 */
public enum Intermediate {
    ELECTRIC_MOTOR_UNIT(Group.ASSEMBLY, 33), TRANSFORMER(Group.ASSEMBLY, 31), CONTROL_BOARD(Group.ASSEMBLY, 1.4),
    SERVO_MOTOR(Group.ASSEMBLY, 35.4), PUMP_UNIT(Group.ASSEMBLY, 24), HYDRAULIC_CYLINDER(Group.ASSEMBLY, 25), GEAR_TRAIN(Group.ASSEMBLY, 31),
    HEAT_EXCHANGER(Group.ASSEMBLY, 56),
    ALUMINA(Group.METALLURGY, 10), CHROMIUM_OXIDE(Group.METALLURGY, 10), MAGNESIA(Group.METALLURGY, 10), FERROSILICON(Group.METALLURGY, 10),
    FERROCHROME(Group.METALLURGY, 10), TITANIUM_SLAG(Group.METALLURGY, 10), TITANIUM_SPONGE(Group.METALLURGY, 10),
    MAGNESIUM_CHLORIDE(Group.METALLURGY, 39), CARBON_ANODE(Group.METALLURGY, 5);

    /** Assembled from components at the bench, or made in a furnace or a cell. */
    public enum Group { ASSEMBLY, METALLURGY }

    public final Group group;
    /** Mass, kg. */
    public final double kg;

    Intermediate(Group group, double kg) {
        this.group = group;
        this.kg = kg;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
}
