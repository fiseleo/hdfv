package dev.mcmod.hdfv.neoforge.refit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The refit shop's screen (0.16): its two slots (blank blueprints for the refitted parts' designs, a vehicle blueprint
 * to take the refit) and the player's inventory, and the job's state. Edits go by RefitNetwork.
 */
public final class RefitShopMenu extends AbstractContainerMenu {
    public static final int WIDTH = 400, HEIGHT = 238, SLOT_X = 8, SLOT_Y = 30, PLAYER_Y = 156;
    public final BlockPos pos;
    private final net.minecraft.world.inventory.ContainerData data;

    public RefitShopMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, null, buf.readBlockPos()); }

    RefitShopMenu(int id, Inventory inventory, RefitShopBlockEntity be) { this(id, inventory, be, be.getBlockPos()); }

    private RefitShopMenu(int id, Inventory inventory, RefitShopBlockEntity be, BlockPos pos) {
        super(HdfvRefit.MENU.get(), id);
        this.pos = pos;
        this.data = be != null ? be.data : new net.minecraft.world.inventory.SimpleContainerData(4);
        addDataSlots(data);
        Container items = be != null ? be.items : new SimpleContainer(RefitShopBlockEntity.SLOTS);
        for (int i = 0; i < RefitShopBlockEntity.SLOTS; i++) {
            int slot = i;
            addSlot(new Slot(items, i, SLOT_X, SLOT_Y + 22 * i) {
                @Override public boolean mayPlace(ItemStack s) { return RefitShopBlockEntity.fits(slot, s); }
                @Override public int getMaxStackSize() { return slot == RefitShopBlockEntity.VEHICLE ? 1 : 64; }
            });
        }
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, PLAYER_Y + r * 18)); } }
        for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, 8 + c * 18, PLAYER_Y + 58)); }
    }

    /** The job as the screen sees it. */
    public boolean working() { return data.get(0) == 1; }
    public int permille() { return data.get(1); }
    public boolean starved() { return data.get(2) == 1; }
    public int energy() { return data.get(3); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int own = RefitShopBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(s, own, slots.size(), true)) { return ItemStack.EMPTY; }
        } else {
            boolean moved = false;
            for (int i = 0; i < own && !moved; i++) { if (RefitShopBlockEntity.fits(i, s)) { moved = moveItemStackTo(s, i, i + 1, false); } }
            if (!moved) { return ItemStack.EMPTY; }
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RefitShopBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
