package dev.mcmod.hdfv.neoforge.assembly;

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
 * An assembly room controller's menu (0.7): the drawings (eight part blueprints, a vehicle blueprint) and the player's
 * inventory; button 0 scans the room, 1 opens or closes its door, 2 starts building, 3 takes the finished vehicle out,
 * 4 takes a vehicle apart. Layout edits come as their own packets
 * (AssemblyNetwork); the report and layout reach the screen with the controller's block entity data.
 */
public final class RoomMenu extends AbstractContainerMenu {
    public static final int SCAN = 0, DOOR = 1, START = 2, LAUNCH = 3, DISMANTLE = 4;
    public static final int WIDTH = 400, HEIGHT = 238, DRAWING_X = 8, DRAWING_Y = 30, VEHICLE_Y = 116, PLAYER_Y = 156;
    public final BlockPos pos;
    private final RoomControllerBlockEntity controller;
    private final net.minecraft.world.inventory.ContainerData data;

    public RoomMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, null, buf.readBlockPos()); }

    RoomMenu(int id, Inventory inventory, RoomControllerBlockEntity be) { this(id, inventory, be, be.getBlockPos()); }

    private RoomMenu(int id, Inventory inventory, RoomControllerBlockEntity be, BlockPos pos) {
        super(HdfvAssembly.ROOM_MENU.get(), id);
        this.controller = be;
        this.pos = pos;
        this.data = be != null ? be.data : new net.minecraft.world.inventory.SimpleContainerData(3);
        addDataSlots(data);
        Container drawings = be != null ? be.drawings : new SimpleContainer(RoomControllerBlockEntity.DRAWINGS);
        for (int i = 0; i < RoomControllerBlockEntity.PART_DRAWINGS; i++) {
            int slot = i;
            addSlot(new Slot(drawings, i, DRAWING_X + 18 * (i % 2), DRAWING_Y + 18 * (i / 2)) {
                @Override public boolean mayPlace(ItemStack s) { return RoomControllerBlockEntity.fits(slot, s); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        addSlot(new Slot(drawings, RoomControllerBlockEntity.VEHICLE_DRAWING, DRAWING_X, VEHICLE_Y) {
            @Override public boolean mayPlace(ItemStack s) { return RoomControllerBlockEntity.fits(RoomControllerBlockEntity.VEHICLE_DRAWING, s); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, PLAYER_Y + r * 18)); } }
        for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, 8 + c * 18, PLAYER_Y + 58)); }
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (controller == null) { return false; }
        net.minecraft.network.chat.Component refused = switch (id) {
            case SCAN -> { controller.scan(); yield null; }
            case DOOR -> controller.toggleDoor() ? null : net.minecraft.network.chat.Component.translatable("hdfv.room.problem.no_door");
            case START -> controller.start();
            case LAUNCH -> controller.launch();
            case DISMANTLE -> controller.dismantle();
            default -> null;
        };
        if (refused != null) { player.displayClientMessage(refused, false); }
        return true;
    }

    /** The build as the screen sees it: 0 none, 1 building, 2 assembled. */
    public int buildState() { return data.get(0); }
    public int permille() { return data.get(1); }
    public int crew() { return data.get(2); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        int drawings = RoomControllerBlockEntity.DRAWINGS;
        if (index < drawings) {
            if (!moveItemStackTo(s, drawings, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (RoomControllerBlockEntity.fits(RoomControllerBlockEntity.VEHICLE_DRAWING, s)) {
            if (!moveItemStackTo(s, RoomControllerBlockEntity.VEHICLE_DRAWING, drawings, false)) { return ItemStack.EMPTY; }
        } else if (!RoomControllerBlockEntity.fits(0, s) || !moveItemStackTo(s, 0, RoomControllerBlockEntity.PART_DRAWINGS, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RoomControllerBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
