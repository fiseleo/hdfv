package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.recipe.Intermediate;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The intermediate items (0.23): a stackable item for each standard component (made on the machines from its drawing,
 * core Component), each bench assembly and each metallurgy intermediate (core Intermediate). Heavy ones (10 kg and
 * more) stack to 16.
 */
public final class HdfvComponents {
    private HdfvComponents() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);

    public static final Map<Component, DeferredItem<Item>> COMPONENTS = new EnumMap<>(Component.class);
    public static final Map<Intermediate, DeferredItem<Item>> INTERMEDIATES = new EnumMap<>(Intermediate.class);

    static {
        for (Component c : Component.values()) { COMPONENTS.put(c, ITEMS.registerSimpleItem(c.id(), properties(c.kg))); }
        for (Intermediate i : Intermediate.values()) { INTERMEDIATES.put(i, ITEMS.registerSimpleItem(i.id(), properties(i.kg))); }
    }

    private static Item.Properties properties(double kg) { return new Item.Properties().stacksTo(kg >= 10 ? 16 : 64); }

    /** Some of a component's item. */
    public static ItemStack stack(Component c, int count) { return new ItemStack(COMPONENTS.get(c).get(), count); }

    /** Some of an intermediate. */
    public static ItemStack stack(Intermediate i, int count) { return new ItemStack(INTERMEDIATES.get(i).get(), count); }

    /** Everything of this class for the creative tab: components by grade, the assemblies, the metallurgy's. */
    public static void tab(CreativeModeTab.Output out) {
        for (DeferredItem<Item> i : COMPONENTS.values()) { out.accept(i.get()); }
        for (DeferredItem<Item> i : INTERMEDIATES.values()) { out.accept(i.get()); }
    }

    /** The ids of its items (for tests). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Component c : Component.values()) { out.add(c.id()); }
        for (Intermediate i : Intermediate.values()) { out.add(i.id()); }
        return out;
    }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }
}
