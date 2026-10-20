package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * JEI (optional; loaded only when JEI is installed): blueprints, vehicle blueprints, parts and stock of different
 * designs or alloys are different entries; the alloy furnace's recipes have their own page, and (0.8) the refinery's and
 * chemical plant's conversions theirs; the design tools, the workshop's machines, the assembly rooms and (0.8) the power,
 * fluid, oil field, refinery and fuel blocks have information pages; (0.9) the multiblock machine tools, their members,
 * accessories and drives too, and the refining page says its yields are at nominal conditions. 0.17: a machining page a
 * head (MachiningCategory), the families' members, heads and the NC drive, the supplies and the air separation unit.
 */
@JeiPlugin
public final class HdfvJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "jei"); }

    private static void subtype(ISubtypeRegistration r, Item item, Function<ItemStack, String> key) {
        r.registerSubtypeInterpreter(item, new ISubtypeInterpreter<>() {
            @Override public Object getSubtypeData(ItemStack stack, UidContext context) { return key.apply(stack); }
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                String k = key.apply(stack);
                return k == null ? "" : Integer.toHexString(k.hashCode());
            }
        });
    }

    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        subtype(registration, HdfvRegistry.BLUEPRINT.get(), Blueprints::json);
        subtype(registration, HdfvAssembly.VEHICLE_BLUEPRINT.get(), s -> s.get(HdfvAssembly.VEHICLE_LAYOUT.get()));
        subtype(registration, HdfvMachines.PART.get(), s -> s.get(HdfvRegistry.DESIGN.get()));
        subtype(registration, HdfvMaterials.STOCK.get(), s -> Stock.alloy(s) == null ? null : Stock.alloy(s).id());
        subtype(registration, HdfvMaterials.STOCK_BIT.get(), s -> Stock.alloy(s) == null ? null : Stock.alloy(s).id());
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AlloyingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new RefiningCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new MachiningCategory(registration.getJeiHelpers().getGuiHelper()));
        // 0.23: machines assembled at the bench; the arc furnace and the electrolysis cell
        registration.addRecipeCategories(new AssemblingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new SmeltingCategory(registration.getJeiHelpers().getGuiHelper(), true));
        registration.addRecipeCategories(new SmeltingCategory(registration.getJeiHelpers().getGuiHelper(), false));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(AlloyingCategory.TYPE, HdfvMaterials.ALLOY_FURNACE_ITEM.get());
        registration.addRecipeCatalysts(AssemblingCategory.TYPE, HdfvMachines.MACHINES.get(Station.ASSEMBLY_BENCH).get());
        registration.addRecipeCatalysts(SmeltingCategory.ARC, dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting.ARC_FURNACE_ITEM.get());
        registration.addRecipeCatalysts(SmeltingCategory.CELL, dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting.ELECTROLYSIS_CELL_ITEM.get());
        registration.addRecipeCatalysts(RefiningCategory.TYPE, HdfvRefinery.COLUMN.get(), HdfvRefinery.UNIT.get());
        registration.addRecipeCatalysts(RefiningCategory.TYPE, dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.LIQUEFIER.get());
        for (String r : dev.mcmod.hdfv.structure.Structures.REACTORS) { registration.addRecipeCatalysts(RefiningCategory.TYPE, HdfvRefinery.MEMBERS.get(r).get()); }
        for (Station s : dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS) { if (s.family) { registration.addRecipeCatalysts(MachiningCategory.TYPE, HdfvMachines.MACHINES.get(s).get()); } }
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(AlloyingCategory.TYPE, level.getRecipeManager().getAllRecipesFor(HdfvMaterials.ALLOYING.get()).stream().map(RecipeHolder::value).toList());
            registration.addRecipes(AssemblingCategory.TYPE, level.getRecipeManager().getAllRecipesFor(HdfvMaterials.ASSEMBLING.get()).stream()
                    .sorted(java.util.Comparator.comparing(r -> r.id().toString())).map(RecipeHolder::value).toList());
            registration.addRecipes(SmeltingCategory.ARC, level.getRecipeManager().getAllRecipesFor(HdfvMaterials.ARC_FURNACE.get()).stream().map(RecipeHolder::value).toList());
            registration.addRecipes(SmeltingCategory.CELL, level.getRecipeManager().getAllRecipesFor(HdfvMaterials.ELECTROLYSIS.get()).stream().map(RecipeHolder::value).toList());
        }
        registration.addIngredientInfo(HdfvRegistry.DESIGN_TABLE_ITEM.get(), Component.translatable("hdfv.jei.design_table"));
        // 0.23: the standard components are made on the machines from their drawings (the design screen's standard component kinds)
        for (var e : dev.mcmod.hdfv.neoforge.material.HdfvComponents.COMPONENTS.entrySet()) {
            registration.addIngredientInfo(e.getValue().get(), Component.translatable("hdfv.jei.component"),
                    Component.translatable("hdfv.jei.component." + Character.toLowerCase(e.getKey().grade)));
        }
        registration.addIngredientInfo(HdfvRegistry.DESIGN_TABLET.get(), Component.translatable("hdfv.jei.design_tablet"));
        registration.addIngredientInfo(HdfvRegistry.BLANK_BLUEPRINT.get(), Component.translatable("hdfv.jei.blank_blueprint"));
        registration.addItemStackInfo(Designs.SAMPLES.stream().map(sample -> Blueprints.of(sample.design().get())).toList(), Component.translatable("hdfv.jei.blueprint"));
        for (var e : HdfvMachines.MACHINES.entrySet()) {
            Station s = e.getKey();
            String kind = s == Station.CNC ? "cnc" : s == Station.ASSEMBLY_BENCH ? "assembly_bench" : s == Station.WELDING_STATION ? "welding_station"
                    : s.manual ? "manual" : "forming";
            if (dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(s)) {
                // 0.9: the multiblock machine tools: their pattern and size, their drive
                registration.addIngredientInfo(e.getValue().get(), Component.translatable("hdfv.jei.machine." + kind),
                        Component.translatable("hdfv.jei.tool." + s.name().toLowerCase(Locale.ROOT)), Component.translatable("hdfv.jei.tool.power"));
            } else {
                registration.addIngredientInfo(e.getValue().get(), Component.translatable("hdfv.jei.machine." + kind));
            }
        }
        // the CNC's modules; the machine tools' members, accessories and drives (0.9)
        var members = dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.MEMBERS;
        List<net.minecraft.world.item.ItemStack> cnc = new java.util.ArrayList<>(), tool = new java.util.ArrayList<>(), heads = new java.util.ArrayList<>();
        for (var m : members.entrySet()) {
            var accessory = dev.mcmod.hdfv.manufacture.ToolConfig.Accessory.ofKind(m.getKey());
            if (accessory != null) { registration.addIngredientInfo(m.getValue().get(), Component.translatable("hdfv.jei.accessory." + m.getKey())); }
            else if (dev.mcmod.hdfv.manufacture.Head.ofKind(m.getKey()) != null) { heads.add(new net.minecraft.world.item.ItemStack(m.getValue().get())); }
            else if (TOOL_MEMBERS.contains(m.getKey())) { tool.add(new net.minecraft.world.item.ItemStack(m.getValue().get())); }
            else { cnc.add(new net.minecraft.world.item.ItemStack(m.getValue().get())); }
        }
        registration.addItemStackInfo(cnc, Component.translatable("hdfv.jei.cnc_modules"));
        registration.addItemStackInfo(tool, Component.translatable("hdfv.jei.tool_members"));
        // 0.17: the heads (their pages are machining's), the NC drive, the supplies and the air separation unit
        registration.addItemStackInfo(heads, Component.translatable("hdfv.jei.heads"));
        registration.addIngredientInfo(HdfvMachines.NC_DRIVE.get(), Component.translatable("hdfv.jei.drive.nc_drive"));
        registration.addItemStackInfo(dev.mcmod.hdfv.neoforge.machining.HdfvMachining.SUPPLIES.values().stream().map(i -> new net.minecraft.world.item.ItemStack(i.get())).toList(),
                Component.translatable("hdfv.jei.supplies"));
        registration.addIngredientInfo(dev.mcmod.hdfv.neoforge.machining.HdfvMachining.AIR_SEPARATOR.get(), Component.translatable("hdfv.jei.air_separation_unit"));
        registration.addRecipes(MachiningCategory.TYPE, List.of(dev.mcmod.hdfv.manufacture.Head.values()));
        for (var d : HdfvMachines.DRIVES.entrySet()) {
            registration.addIngredientInfo(d.getValue().get(), Component.translatable("hdfv.jei.drive." + (d.getKey() ? "motor_drive" : "pulley_drive")));
        }
        registration.addIngredientInfo(HdfvMachines.WORKPIECE.get(), Component.translatable("hdfv.jei.workpiece"));
        registration.addIngredientInfo(HdfvMachines.PART.get(), Component.translatable("hdfv.jei.part"));
        registration.addIngredientInfo(HdfvAssembly.VEHICLE_BLUEPRINT.get(), Component.translatable("hdfv.jei.vehicle_blueprint"));
        for (var c : HdfvAssembly.CONTROLLERS.entrySet()) {
            registration.addIngredientInfo(c.getValue().get(), Component.translatable("hdfv.jei.controller." + c.getKey().name().toLowerCase(Locale.ROOT)));
        }
        registration.addIngredientInfo(HdfvAssembly.ASSEMBLY_FLOOR.get(), Component.translatable("hdfv.jei.room_blocks"));
        registration.addIngredientInfo(HdfvAssembly.CRANE_RAIL.get(), Component.translatable("hdfv.jei.room_blocks"));
        registration.addIngredientInfo(HdfvAssembly.SLIPWAY.get(), Component.translatable("hdfv.jei.room_blocks"));
        for (var d : HdfvAssembly.DOORS.values()) { registration.addIngredientInfo(d.get(), Component.translatable("hdfv.jei.room_blocks")); }
        // 0.8: refining, power, fluids, oil fields, fuel
        registration.addRecipes(RefiningCategory.TYPE, dev.mcmod.hdfv.petroleum.Refining.all());
        // 0.10: the liquefier, shown as a conversion (hydrogen in, the liquid out, its electricity)
        registration.addRecipes(RefiningCategory.TYPE, List.of(new dev.mcmod.hdfv.petroleum.Refining.Yield("liquefier", Map.of("hydrogen", 1.0),
                Map.of("liquid_hydrogen", 1.0), 0, dev.mcmod.hdfv.fluid.HydrogenStorage.LIQUEFACTION_J_PER_KG)));
        for (var e : INFO.entrySet()) { registration.addIngredientInfo(e.getKey().get(), Component.translatable("hdfv.jei." + e.getValue())); }
    }

    /** The machine tools' members (0.9). */
    private static final java.util.Set<String> TOOL_MEMBERS = java.util.Set.of(dev.mcmod.hdfv.structure.Structures.LATHE_BED, dev.mcmod.hdfv.structure.Structures.MILL_TABLE,
            dev.mcmod.hdfv.structure.Structures.MILL_HEAD, dev.mcmod.hdfv.structure.Structures.PLANER_BED, dev.mcmod.hdfv.structure.Structures.PLANER_UPRIGHT,
            dev.mcmod.hdfv.structure.Structures.CROSS_RAIL, dev.mcmod.hdfv.structure.Structures.BORING_TABLE, dev.mcmod.hdfv.structure.Structures.BORING_COLUMN,
            dev.mcmod.hdfv.structure.Structures.BORING_HEAD, dev.mcmod.hdfv.structure.Structures.DRILL_TABLE, dev.mcmod.hdfv.structure.Structures.DRILL_COLUMN,
            dev.mcmod.hdfv.structure.Structures.DRILL_HEAD, dev.mcmod.hdfv.structure.Structures.DRILL_SPINDLE,
            // 0.17: the families' members
            dev.mcmod.hdfv.structure.Structures.GRINDER_BED, dev.mcmod.hdfv.structure.Structures.FINISHER_TABLE, dev.mcmod.hdfv.structure.Structures.FINISHER_COLUMN, dev.mcmod.hdfv.structure.Structures.FINISHER_TOP, dev.mcmod.hdfv.structure.Structures.GEAR_TABLE,
            dev.mcmod.hdfv.structure.Structures.GEAR_COLUMN, dev.mcmod.hdfv.structure.Structures.GEAR_TOP, dev.mcmod.hdfv.structure.Structures.RAM_BED, dev.mcmod.hdfv.structure.Structures.SAW_BED, dev.mcmod.hdfv.structure.Structures.EDM_TANK, dev.mcmod.hdfv.structure.Structures.CUTTING_SLATS,
            dev.mcmod.hdfv.structure.Structures.THREADER_BASE, dev.mcmod.hdfv.structure.Structures.RIVETER_FRAME);

    /** The 0.8 blocks and items with information pages: their key under hdfv.jei. */
    private static final Map<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>, String> INFO = new java.util.LinkedHashMap<>();
    static {
        for (var e : dev.mcmod.hdfv.neoforge.power.HdfvPower.POWER.entrySet()) { INFO.put(e.getValue(), "power." + e.getKey().name().toLowerCase(Locale.ROOT)); }
        INFO.put(dev.mcmod.hdfv.neoforge.power.HdfvPower.CABLE, "cable");
        INFO.put(dev.mcmod.hdfv.neoforge.power.HdfvPower.HEAVY_CABLE, "cable");
        INFO.put(dev.mcmod.hdfv.neoforge.power.HdfvPower.LINE_SHAFT, "line_shaft");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.PIPE, "fluid_pipe");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.TANK, "fluid_tank");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.PUMP, "pump");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.TANK_VALVE, "storage_tank");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.TANK_WALL, "storage_tank");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.TANK_ROOF, "storage_tank");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.JERRY_CAN, "jerry_can");
        INFO.put(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.OIL_SEEP, "oil_seep");
        INFO.put(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.GEOPHONE, "geophone");
        INFO.put(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.RIG, "drilling_rig");
        INFO.put(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.WELLHEAD, "wellhead");
        INFO.put(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.JACK, "pump_jack");
        INFO.put(HdfvRefinery.COLUMN, "distillation_column");
        INFO.put(HdfvRefinery.UNIT, "process_unit");
        INFO.put(HdfvRefinery.OUTLET, "unit_outlet");
        INFO.put(dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.DISPENSER, "fuel_dispenser");
        // 0.10
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.CRYOGENIC_TANK, "cryogenic_tank");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.HYDROGEN_CYLINDERS, "hydrogen_cylinders");
        INFO.put(dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.LIQUEFIER, "hydrogen_liquefier");
        INFO.put(dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.KNSB_GRAIN, "grain");
        INFO.put(dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.APCP_GRAIN, "grain");
        INFO.put(dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.REED_SET, "reed_set");
    }
}
