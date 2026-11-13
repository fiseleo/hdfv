package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.hdfv.storage.CargoMass;
import dev.mcmod.hdfv.storage.StorageKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * A store's menu (0.25): its slots nine across, as many rows as it has up to six a page (StorageKind.rows; more slots
 * are further pages, turned on the client: a slot off the page shown is inactive), and the player's inventory under
 * them; shift-click moves between the two. Its footer (slots used, the mass of what is in it by ItemMass, and a limit
 * when there is one) goes to the screen in five data slots, counted on the server once a tick while it is open.
 *
 * <p>Not tied to a block: it works on any Container (a storage block's, later a vehicle's hold) with a filter, a rule
 * of who may keep it open and an optional mass limit. Open it with
 * {@code player.openMenu(provider, buf -> StorageMenu.write(buf, container.getContainerSize(), filter))}.
 */
public class StorageMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, SLOT_X = 8, SLOT_Y = 18;
    /** The data slots: slots used, the mass and the limit in tenths of a kilogram (two 16 bit halves each; a limit of 0 is none). */
    public static final int DATA = 5;

    /** What a store takes. */
    public enum Filter {
        ANY, PARTS;

        public boolean test(ItemStack s) { return this == ANY || s.is(HdfvStorage.PARTS_RACK); }
    }

    /** The store's slots (the menu's first), rows a page shows, pages. */
    public final int size, rows, pages;
    public final Filter filter;
    /** Where the player's inventory starts, and the panel's height. */
    public final int playerTop, height;
    private final Container container;
    private final Predicate<Player> valid;
    private final DoubleSupplier limitKg;
    private final ContainerData data;
    private final boolean server;
    private int used, massTenths, limitTenths;
    /** The page shown (the client's; the server's stays 0). */
    private int page;

    /** The client's copy (what write put in the buffer). */
    public StorageMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(Math.clamp(buf.readVarInt(), 1, 1024)), Filter.values()[Math.clamp(buf.readVarInt(), 0, Filter.values().length - 1)],
                p -> true, null, false);
    }

    /**
     * The server's: a store's container, what it takes, who may keep it open (asked every tick) and its mass limit in
     * kg (null or 0: none; shown in the footer, not enforced here).
     */
    public StorageMenu(int id, Inventory inventory, Container container, Filter filter, Predicate<Player> valid, DoubleSupplier limitKg) {
        this(id, inventory, container, filter, valid, limitKg, true);
    }

    private StorageMenu(int id, Inventory inventory, Container container, Filter filter, Predicate<Player> valid, DoubleSupplier limitKg, boolean server) {
        super(HdfvStorage.MENU.get(), id);
        this.container = container;
        this.filter = filter;
        this.valid = valid;
        this.limitKg = limitKg;
        this.server = server;
        this.size = container.getContainerSize();
        this.rows = StorageKind.rows(size);
        this.pages = StorageKind.pages(size);
        int perPage = rows * StorageKind.COLUMNS;
        container.startOpen(inventory.player);
        for (int i = 0; i < size; i++) {
            int at = i % perPage;
            addSlot(new StoreSlot(container, i, SLOT_X + 18 * (at % StorageKind.COLUMNS), SLOT_Y + 18 * (at / StorageKind.COLUMNS), i / perPage));
        }
        playerTop = SLOT_Y + rows * 18 + 13;
        height = playerTop + 82;
        for (int r = 0; r < 3; r++) { for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * 18, playerTop + r * 18)); } }
        for (int c = 0; c < 9; c++) { addSlot(new Slot(inventory, c, 8 + c * 18, playerTop + 58)); }
        this.data = server ? new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> used; case 1 -> CargoMass.low(massTenths); case 2 -> CargoMass.high(massTenths);
                    case 3 -> CargoMass.low(limitTenths); default -> CargoMass.high(limitTenths);
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA; }
        } : new SimpleContainerData(DATA);
        if (server) { count(); }
        addDataSlots(data);
    }

    /** What a provider writes for the client's copy when it opens the menu. */
    public static void write(RegistryFriendlyByteBuf buf, int slots, Filter filter) {
        buf.writeVarInt(slots);
        buf.writeVarInt(filter.ordinal());
    }

    /** A slot of the store: on its page, taking what the store takes. */
    private final class StoreSlot extends Slot {
        private final int onPage;

        StoreSlot(Container container, int index, int x, int y, int onPage) { super(container, index, x, y); this.onPage = onPage; }

        @Override public boolean mayPlace(ItemStack s) { return filter.test(s) && container.canPlaceItem(getContainerSlot(), s); }
        @Override public boolean isActive() { return onPage == page; }
    }

    /** Counts the footer from the container (the server's, once a tick while the menu is open). */
    private void count() {
        int n = 0;
        double kg = 0;
        for (int i = 0; i < size; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) { continue; }
            n++;
            kg += ItemMass.kg(s);
        }
        used = n;
        massTenths = CargoMass.tenths(kg);
        limitTenths = limitKg == null ? 0 : CargoMass.tenths(limitKg.getAsDouble());
    }

    @Override public void broadcastChanges() {
        if (server) { count(); }
        super.broadcastChanges();
    }

    /** The footer: slots holding something, the mass of what is in the store, and its limit (0: none), kg. */
    public int usedSlots() { return data.get(0); }
    public double massKg() { return CargoMass.join(data.get(1), data.get(2)) / 10.0; }
    public double limitKg() { return CargoMass.join(data.get(3), data.get(4)) / 10.0; }

    /** The page shown, from 0 (the client's view; clicks reach only its slots). */
    public int page() { return page; }
    public void page(int to) { page = Math.clamp(to, 0, pages - 1); }

    /** The store's container (the client's is a copy the server fills). */
    public Container container() { return container; }

    @Override public boolean stillValid(Player player) { return valid.test(player); }

    @Override public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem()) { return ItemStack.EMPTY; }
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index < size) {
            if (!moveItemStackTo(s, size, slots.size(), true)) { return ItemStack.EMPTY; }
        } else if (!filter.test(s) || !moveItemStackTo(s, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (s.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        return copy;
    }
}
