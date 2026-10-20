package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.hdfv.neoforge.power.Buffer;
import dev.mcmod.hdfv.neoforge.power.EnergyNetworks;
import dev.mcmod.hdfv.neoforge.power.PowerBlockEntity;
import dev.mcmod.hdfv.structure.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A multiblock machine's controller (0.8: the drilling rig, the pump jack, the refinery's units): its pattern, its
 * Forge Energy buffer (filled by cables and from what touches it) and its tick while formed.
 */
public abstract class ControllerBlockEntity extends BlockEntity implements Multiblocks.Holder {
    protected final Multiblock multiblock;
    protected final Buffer energy;
    private double owedFe;

    protected ControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState s, Pattern pattern, double ratedW) {
        super(type, pos, s);
        this.multiblock = new Multiblock(pattern, pos);
        this.energy = new Buffer((int) Math.ceil(2 * PowerBlockEntity.fePerTick(ratedW)) + 10, Integer.MAX_VALUE, 0, this::setChanged);
    }

    @Override public Multiblock multiblock() { return multiblock; }
    public Buffer energy() { return energy; }

    /** A tick: looks at its pattern, then works if formed. */
    public final void tick() {
        if (!(level instanceof ServerLevel server)) { return; }
        multiblock.tick(server);
        work(server, multiblock.formed());
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, hum());
    }

    /** Its work this tick (formed or not). */
    protected abstract void work(ServerLevel level, boolean formed);

    /** What it sounds like after this tick's work (0.19; null: silent). */
    protected dev.mcmod.hdfv.sound.MachineSound.Hum hum() { return null; }

    /** Its status, shown when used. */
    public abstract Component status();

    /** Used with an item (or empty-handed); whether it took the item. */
    public boolean use(Player player, InteractionHand hand, ItemStack held) { return false; }

    /** What it drops when broken besides itself. */
    public List<ItemStack> drops() { return List.of(); }

    /** 0.20: its block was broken (not unloaded): forget it wherever it was registered. */
    protected void broken(ServerLevel level) {}

    /**
     * The share (0 to 1) of a power (W) its buffer pays for this tick, drawing it (fractions of an FE carried over),
     * after drawing from what touches it.
     */
    protected double draw(double watts) {
        lastW = 0;
        if (!(watts > 0)) { return 1; }
        if (level != null) { EnergyNetworks.pull(level, worldPosition, energy); }
        double need = PowerBlockEntity.fePerTick(watts), owed = need + owedFe;
        if (energy.getEnergyStored() >= Math.ceil(owed - 1e-9)) {
            int whole = (int) Math.floor(owed);
            energy.use(whole);
            owedFe = owed - whole;
            lastW = watts;
            return 1;
        }
        int have = energy.getEnergyStored();
        energy.use(have);
        owedFe = 0;
        lastW = watts * have / need;
        return have / need;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putDouble("OwedFe", owedFe);
        save(tag, registries);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(tag.getInt("Energy"));
        owedFe = tag.getDouble("OwedFe");
        load(tag, registries);
    }

    // ---------------------------------------------------------------- the screen's view (0.9)

    private CompoundTag shown = new CompoundTag();
    /** The power it drew last tick, W (for its screen). */
    protected double lastW;

    /** What its screen shows (empty: no screen); sent to the watching clients by sendView. */
    public CompoundTag view() { return new CompoundTag(); }

    /** On a client: the last view the server sent. */
    public CompoundTag shown() { return shown; }

    /** Sends its view to the clients watching it. */
    public void sendView() {
        if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS); }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.put("View", view());
        return t;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { shown = tag.getCompound("View"); }

    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    protected void save(CompoundTag tag, HolderLookup.Provider registries) {}
    protected void load(CompoundTag tag, HolderLookup.Provider registries) {}
}
