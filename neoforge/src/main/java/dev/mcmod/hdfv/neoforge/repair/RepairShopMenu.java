package dev.mcmod.hdfv.neoforge.repair;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The repair shop's screen (0.25): no slots (the shop takes its materials from the stores at the room, as the refit
 * shop does), the job's state as data. Picks and actions go by RepairNetwork.
 */
public final class RepairShopMenu extends AbstractContainerMenu {
    public static final int WIDTH = 360, HEIGHT = 222;
    public final BlockPos pos;
    private final net.minecraft.world.inventory.ContainerData data;

    public RepairShopMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, null, buf.readBlockPos()); }

    RepairShopMenu(int id, Inventory inventory, RepairShopBlockEntity be) { this(id, be, be.getBlockPos()); }

    private RepairShopMenu(int id, RepairShopBlockEntity be, BlockPos pos) {
        super(HdfvRepair.MENU.get(), id);
        this.pos = pos;
        this.data = be != null ? be.data : new net.minecraft.world.inventory.SimpleContainerData(4);
        addDataSlots(data);
    }

    /** The job as the screen sees it. */
    public boolean working() { return data.get(0) == 1; }
    public int permille() { return data.get(1); }
    public boolean starved() { return data.get(2) == 1; }
    public int energy() { return data.get(3); }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RepairShopBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
