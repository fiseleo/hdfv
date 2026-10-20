package dev.mcmod.hdfv.neoforge.petroleum;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The screen of a petroleum block (0.9): which block (its position) and kind of screen; the refinery's product slots
 * (to take from) or the rig's casing slots (to fill), with the player's inventory below them; the block's view sent to
 * the player every half second while it is open. Settings come back by PetroleumNetwork.
 */
public final class PetroleumMenu extends AbstractContainerMenu {
    public enum Kind {
        COLUMN(4, false, 140), UNIT(4, false, 140), RIG(3, true, 112), JACK(0, false, 132), WELLHEAD(0, false, 100), DISPENSER(0, false, 104);
        public final int slots;
        public final boolean fill;
        /** Where its settings end (its slots are just above, the player's inventory below). */
        public final int top;
        Kind(int slots, boolean fill, int top) { this.slots = slots; this.fill = fill; this.top = top; }
    }

    public static final int WIDTH = 256;
    public final BlockPos pos;
    public final Kind kind;
    /** Where the player's inventory starts (none when the screen has no slots), and the screen's height. */
    public final int playerTop, height;
    private final Configurable target;
    private int clock;

    PetroleumMenu(int id, Inventory inventory, BlockPos pos, Kind kind, IItemHandler items, Configurable target) {
        super(HdfvPetroleum.MENU.get(), id);
        this.pos = pos;
        this.kind = kind;
        this.target = target;
        IItemHandler h = items != null ? items : new ItemStackHandler(Math.max(kind.slots, 1));
        for (int i = 0; i < kind.slots; i++) {
            addSlot(new SlotItemHandler(h, i, 8 + 18 * i, kind.top - 22) {
                @Override public boolean mayPlace(ItemStack s) { return kind.fill && super.mayPlace(s); }
            });
        }
        if (kind.slots > 0) {
            playerTop = kind.top + 14;
            int x = (WIDTH - 162) / 2 + 1;
            for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, x + c * 18, playerTop + r * 18)); } }
            for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, x + c * 18, playerTop + 58)); }
            height = playerTop + 82;
        } else {
            playerTop = -1;
            height = kind.top + 4;
        }
    }

    /** The client's menu, from the position and kind the server sent. */
    public PetroleumMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), Kind.values()[Math.floorMod(buf.readVarInt(), Kind.values().length)], null, null);
    }

    /** Opens a block's screen for a player (server). */
    public static void open(ServerPlayer player, BlockEntity be, Component title) {
        if (!(be instanceof Configurable c)) { return; }
        BlockPos pos = be.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PetroleumMenu(id, inv, pos, c.kind(), c.menuItems(), c), title), buf -> {
            buf.writeBlockPos(pos);
            buf.writeVarInt(c.kind().ordinal());
        });
        c.sendView();
    }

    /** The block it is the screen of (server). */
    Configurable target() { return target; }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (target != null && clock++ % 10 == 0) { target.sendView(); }
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof Configurable && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int own = kind.slots;
        if (index < own) {
            if (!moveItemStackTo(s, own, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (!kind.fill || !moveItemStackTo(s, 0, own, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.set(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }
}
