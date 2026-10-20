package dev.mcmod.hdfv.neoforge.design;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blueprint items: a part design's JSON in the hdfv:design component, and saving a design from the design screen onto one. */
public final class Blueprints {
    private Blueprints() {}

    /** A written blueprint of a design. */
    public static ItemStack of(PartDesign design) {
        ItemStack stack = new ItemStack(HdfvRegistry.BLUEPRINT.get());
        stack.set(HdfvRegistry.DESIGN.get(), Designs.write(design));
        return stack;
    }

    /** The design JSON a stack carries, or null (not a written blueprint). */
    public static String json(ItemStack stack) {
        return stack.is(HdfvRegistry.BLUEPRINT.get()) ? stack.get(HdfvRegistry.DESIGN.get()) : null;
    }

    /** What a blueprint holds: its design (or why it cannot be read) and how many issues of each kind the design has. */
    public record Info(PartDesign design, String error, int invalid, int extrapolation) {}

    private static final Map<String, Info> INFO = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Info> eldest) { return size() > 64; }
    });

    /** The blueprint's design and issues, read once per JSON (names and tooltips ask every frame). */
    public static Info info(String json) {
        Info cached = INFO.get(json);
        if (cached != null) { return cached; }
        Info info;
        try {
            PartDesign d = Designs.read(json);
            List<DesignIssue> issues = d.check();
            int invalid = (int) issues.stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).count();
            info = new Info(d, null, invalid, issues.size() - invalid);
        } catch (RuntimeException e) {
            info = new Info(null, String.valueOf(e.getMessage()), 0, 0);
        }
        INFO.put(json, info);
        return info;
    }

    /** Where to save: onto the written blueprint in a hand, or onto a new one (a blank blueprint from the inventory). */
    public enum Target { NEW, MAIN_HAND, OFF_HAND }

    public enum Result { SAVED, OVERWRITTEN, NEEDS_BLANK, NOT_A_BLUEPRINT, UNREADABLE }

    /**
     * Saves a design sent by a client (server side): the JSON is read back as a design of a known type within the size
     * limit and stored as the design writes itself. A design with issues is saved too (it is a drawing; machining checks
     * it). A new blueprint uses up a blank blueprint, except in creative mode.
     */
    public static Result save(Player player, String json, Target target) {
        String canonical;
        try { canonical = Designs.write(Designs.read(json)); }
        catch (RuntimeException e) { return Result.UNREADABLE; }
        if (target != Target.NEW) {
            ItemStack held = player.getItemInHand(target == Target.MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (!held.is(HdfvRegistry.BLUEPRINT.get())) { return Result.NOT_A_BLUEPRINT; }
            held.set(HdfvRegistry.DESIGN.get(), canonical);
            return Result.OVERWRITTEN;
        }
        if (!player.hasInfiniteMaterials()) {
            Inventory inventory = player.getInventory();
            int slot = -1;
            for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
                if (inventory.getItem(i).is(HdfvRegistry.BLANK_BLUEPRINT.get())) { slot = i; }
            }
            if (slot < 0) { return Result.NEEDS_BLANK; }
            inventory.getItem(slot).shrink(1);
        }
        ItemStack written = new ItemStack(HdfvRegistry.BLUEPRINT.get());
        written.set(HdfvRegistry.DESIGN.get(), canonical);
        if (!player.getInventory().add(written)) { player.drop(written, false); }
        return Result.SAVED;
    }

    public static Component message(Result result) {
        return Component.translatable("hdfv.save." + result.name().toLowerCase(java.util.Locale.ROOT));
    }
}
