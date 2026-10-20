package dev.mcmod.hdfv.neoforge.airport;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A control tower's screen (0.20): no slots; which tower (its controller's position) and where it was opened from (the
 * desk in the base or a console in the cab, 60 m up): it stays open within 8 blocks of that. Its view comes by
 * AirportNetwork every half second; commands go back the same way.
 */
public final class TowerMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final BlockPos from;

    TowerMenu(int id, BlockPos pos, BlockPos from) {
        super(HdfvAirports.TOWER_MENU.get(), id);
        this.pos = pos;
        this.from = from;
    }

    /** The client's menu, from the position the server sent. */
    public TowerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, buf.readBlockPos(), null); }

    /** Opens a tower's screen for a player at reach (from where the player stands: the desk or a console). */
    static void open(ServerPlayer player, TowerBlockEntity tower) {
        BlockPos pos = tower.getBlockPos(), from = player.blockPosition();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TowerMenu(id, pos, from), tower.getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(pos));
        AirportNetwork.sendView((net.minecraft.server.level.ServerLevel) player.level(), tower);
    }

    @Override public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(pos) instanceof TowerBlockEntity)) { return false; }
        BlockPos at = from == null ? pos : from;
        return player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
