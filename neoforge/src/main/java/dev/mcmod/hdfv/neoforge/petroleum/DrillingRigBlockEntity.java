package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.aerocore.api.petroleum.Lift;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.petroleum.OilFields;
import dev.mcmod.hdfv.petroleum.Oilwell;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A drilling rig's drawworks (0.8; structure.Structures.DRILLING_RIG): started by hand, it drills down at the rotary
 * table at Teale's rate for the field's rock on Oilwell.RIG_W of power, running steel casing as it goes (steel or
 * alloy steel stock put in it, Oilwell.CASING_KG_PER_M). Reaching a field it sets a wellhead where the rotary table
 * was; with no field under it, it gives up at DRY_DEPTH_M. 0.9: its screen sets it going and its target depth (100 to
 * 3000 m, DRY_DEPTH_M at first): a field deeper than that is not reached (a dry hole there; set it deeper and it
 * drills on).
 */
public final class DrillingRigBlockEntity extends ControllerBlockEntity implements Configurable {
    public enum State { NOT_FORMED, IDLE, NO_POWER, NO_CASING, DRILLING, STRUCK_OIL, DRY_HOLE }

    public static final double DRY_DEPTH_M = 1000, DEFAULT_ROCK_PA = 80e6;
    private final ItemStackHandler casing = new ItemStackHandler(3) {
        @Override public boolean isItemValid(int slot, ItemStack s) { Alloy a = Stock.alloy(s); return a == Alloy.STEEL || a == Alloy.ALLOY_STEEL; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private boolean running;
    private double drilledM, casingKg, targetM = DRY_DEPTH_M;
    public static final double MIN_TARGET_M = 100, MAX_TARGET_M = 3000;
    private State state = State.NOT_FORMED;

    public DrillingRigBlockEntity(BlockPos pos, BlockState s) { super(HdfvPetroleum.RIG_ENTITY.get(), pos, s, Structures.DRILLING_RIG, Oilwell.RIG_W); }

    public ItemStackHandler casing() { return casing; }
    public State state() { return state; }
    public double drilledM() { return drilledM; }
    public void setRunning(boolean on) { running = on; setChanged(); }

    /** The rotary table's place (where the well goes), or null. */
    public BlockPos rotary() {
        Pattern.Match m = multiblock.match();
        if (m == null) { return null; }
        for (Pattern.Cell c : m.cells()) { if (Structures.RIG_ROTARY.equals(c.kind())) { return new BlockPos(c.x(), c.y(), c.z()); } }
        return null;
    }

    public double targetM() { return targetM; }

    @Override protected dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return state == State.DRILLING ? dev.mcmod.hdfv.sound.MachineSound.drillingRig(Oilwell.RIG_W, true) : null; }

    @Override protected void work(ServerLevel level, boolean formed) {
        if (state == State.STRUCK_OIL) { return; }
        if (state == State.DRY_HOLE) {
            if (drilledM >= targetM - 1e-9) { return; }
            state = State.IDLE;
        }
        if (!formed) { state = State.NOT_FORMED; return; }
        if (!running) { state = State.IDLE; return; }
        BlockPos well = rotary();
        OilFields.Field field = OilFieldData.of(level).field(level, well);
        double fieldDepth = field != null ? field.depthM() : Double.POSITIVE_INFINITY;
        double target = Math.min(fieldDepth, targetM), rock = field != null ? field.rockStrengthPa() : DEFAULT_ROCK_PA;
        double metres = Lift.penetrationMPerS(Oilwell.RIG_W, Oilwell.BIT_M, rock) * HdfvConfig.workSecondsPerTick();
        // casing first: what this tick's metres need
        while (casingKg < metres * Oilwell.CASING_KG_PER_M && takeSteel()) { }
        if (casingKg < metres * Oilwell.CASING_KG_PER_M) { state = State.NO_CASING; return; }
        double share = draw(Oilwell.RIG_W);
        if (share <= 0) { state = State.NO_POWER; return; }
        metres *= share;
        casingKg -= metres * Oilwell.CASING_KG_PER_M;
        drilledM = Math.min(target, drilledM + metres);
        state = State.DRILLING;
        if (drilledM >= target - 1e-9) {
            if (field != null && drilledM >= fieldDepth - 1e-9) {
                level.setBlock(well, HdfvPetroleum.WELLHEAD.get().defaultBlockState(), Block.UPDATE_ALL);
                dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, net.minecraft.world.phys.Vec3.atCenterOf(well), dev.mcmod.hdfv.sound.MachineSound.Event.GUSHER, 0);
                state = State.STRUCK_OIL;
            } else {
                state = State.DRY_HOLE;
            }
            running = false;
        }
        setChanged();
    }

    private boolean takeSteel() {
        for (int i = 0; i < casing.getSlots(); i++) {
            ItemStack s = casing.getStackInSlot(i);
            if (s.isEmpty()) { continue; }
            double kg = Stock.kg(s.copyWithCount(1));
            casing.extractItem(i, 1, false);
            casingKg += kg;
            return true;
        }
        return false;
    }

    @Override public boolean use(Player player, InteractionHand hand, ItemStack held) {
        if (!held.isEmpty()) {
            if (!casing.isItemValid(0, held)) { return false; }
            ItemStack left = held.copy();
            for (int i = 0; i < casing.getSlots() && !left.isEmpty(); i++) { left = casing.insertItem(i, left, false); }
            player.setItemInHand(hand, left);
            return true;
        }
        if (state != State.STRUCK_OIL && state != State.DRY_HOLE) { running = !running; }
        setChanged();
        return true;
    }

    @Override public PetroleumMenu.Kind kind() { return PetroleumMenu.Kind.RIG; }
    @Override public net.neoforged.neoforge.items.IItemHandler menuItems() { return casing; }

    @Override public Component set(String key, double value) {
        switch (key) {
            case "running" -> {
                if (state == State.STRUCK_OIL) { return Component.translatable("hdfv.rig.state.struck_oil"); }
                running = value > 0.5;
            }
            case "target" -> {
                if (!(value >= MIN_TARGET_M && value <= MAX_TARGET_M)) { return Configurable.outOfRange(key, MIN_TARGET_M, MAX_TARGET_M); }
                if (value < drilledM) { return Component.translatable("hdfv.gui.rig.shallower"); }
                targetM = value;
                if (state == State.DRY_HOLE && targetM > drilledM + 1e-9) { state = State.IDLE; }
            }
            default -> { return Component.translatable("hdfv.gui.no_setting"); }
        }
        setChanged();
        return null;
    }

    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putString("State", state.name().toLowerCase(Locale.ROOT));
        t.putBoolean("Running", running);
        t.putDouble("Drilled", drilledM);
        t.putDouble("Target", targetM);
        double steel = casingKg;
        for (int i = 0; i < casing.getSlots(); i++) { steel += Stock.kg(casing.getStackInSlot(i)); }
        t.putDouble("Steel", steel);
        t.putDouble("PowerW", lastW);
        return t;
    }

    @Override public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < casing.getSlots(); i++) { if (!casing.getStackInSlot(i).isEmpty()) { out.add(casing.getStackInSlot(i)); } }
        return out;
    }

    @Override public Component status() {
        int steel = 0;
        for (int i = 0; i < casing.getSlots(); i++) { steel += (int) Math.round(Stock.kg(casing.getStackInSlot(i))); }
        return Component.translatable("hdfv.rig.status", Component.translatable("hdfv.rig.state." + state.name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.0f", drilledM), steel);
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Casing", casing.serializeNBT(registries));
        tag.putBoolean("Running", running);
        tag.putDouble("Drilled", drilledM);
        tag.putDouble("CasingKg", casingKg);
        tag.putString("State", state.name());
        tag.putDouble("Target", targetM);
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        targetM = tag.contains("Target") ? tag.getDouble("Target") : DRY_DEPTH_M;
        if (tag.contains("Casing")) { casing.deserializeNBT(registries, tag.getCompound("Casing")); }
        running = tag.getBoolean("Running");
        drilledM = tag.getDouble("Drilled");
        casingKg = tag.getDouble("CasingKg");
        try { state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException e) { state = State.NOT_FORMED; }
    }
}
