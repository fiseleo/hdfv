package dev.mcmod.hdfv.neoforge.tutorial;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.client.ClientAccess;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The new-player tutorial (0.24; the user: 「加入新手教學」, choosing advancements, a handbook of hdfv's own, a hint on
 * first joining and JEI pages): the handbook item, the hdfv:milestone criterion, the player's tutorial data, the
 * welcome on first joining, /hdfv handbook, and the list of tutorial advancements a player has (the handbook ticks
 * its entries with it). The advancements are data (data/hdfv/advancement/tutorial, written by tools/tutorial.py); the
 * handbook's pages are assets (assets/hdfv/handbook/&lt;language&gt;).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class HdfvTutorial {
    private HdfvTutorial() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Hdfv.MOD_ID);

    public static final DeferredItem<HandbookItem> HANDBOOK = ITEMS.registerItem("handbook", HandbookItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<CriterionTrigger<?>, Milestone> MILESTONE = TRIGGERS.register("milestone", Milestone::new);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TutorialData>> DATA = ATTACHMENTS.register("tutorial",
            () -> AttachmentType.builder(() -> TutorialData.EMPTY).serialize(TutorialData.CODEC).copyOnDeath().build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TRIGGERS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(HdfvTutorial::payloads);
    }

    public static void tab(CreativeModeTab.Output output) { output.accept(HANDBOOK.get()); }

    /** The handbook: opens it (on the client). */
    public static final class HandbookItem extends Item {
        public HandbookItem(Properties properties) { super(properties); }

        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) { ClientAccess.openHandbook(""); }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
    }

    // ---------------------------------------------------------------------------------------------------- milestones

    private static final Set<String> ENGINES = Set.of(PistonEngineDesign.TYPE, JetEngineDesign.TYPE, TurbofanDesign.TYPE, ShaftTurbineDesign.TURBOPROP,
            ShaftTurbineDesign.TURBOSHAFT, dev.mcmod.hdfv.part.engine.RamjetDesign.TYPE, dev.mcmod.hdfv.part.engine.ScramjetDesign.TYPE,
            dev.mcmod.hdfv.part.engine.PulsejetDesign.TYPE, dev.mcmod.hdfv.part.engine.SolidRocketDesign.TYPE, dev.mcmod.hdfv.part.engine.TurboRamjetDesign.TYPE);

    /** The family of a part design type, the kind of a design_saved milestone: engine, wing, body, chassis or other. */
    public static String family(String type) {
        if (ENGINES.contains(type)) { return "engine"; }
        if (type.equals(WingDesign.TYPE) || type.equals(AirPropellerDesign.TYPE) || type.equals(RotorDesign.TYPE)) { return "wing"; }
        if (type.equals(dev.mcmod.hdfv.part.body.BodyDesign.TYPE)) { return "body"; }
        if (type.equals(dev.mcmod.hdfv.part.chassis.ChassisDesign.TYPE)) { return "chassis"; }
        return "other";
    }

    /** The kind of a vehicle's milestones by the room it is built in: car, aircraft or boat. */
    public static String kind(dev.mcmod.hdfv.assembly.Room room) {
        return switch (room) {
            case CAR_FACTORY -> "car";
            case HANGAR -> "aircraft";
            case SHIPYARD -> "boat";
            case HEAVY_YARD -> "car";   // 0.26: a machine counts as a car on the road (it is driven)
        };
    }

    // ---------------------------------------------------------------------------------------------------- first join

    @SubscribeEvent static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        TutorialData data = player.getData(DATA);
        if (!data.handbookGiven()) {
            if (HdfvConfig.giveHandbook()) {
                ItemStack book = new ItemStack(HANDBOOK.get());
                if (!player.getInventory().add(book)) { player.drop(book, false); }
            }
            player.setData(DATA, data.withHandbook());
            if (HdfvConfig.welcome() && player.connection != null) { welcome(player); }
        }
        sendProgress(player);
    }

    /** The welcome on first joining: the handbook (a link that opens it), the advancements tab and a few keys as bound now. */
    static void welcome(ServerPlayer player) {
        Component open = Component.translatable("hdfv.welcome.open").withStyle(s -> s.withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hdfv handbook"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("hdfv.welcome.open.hover"))));
        player.sendSystemMessage(Component.translatable("hdfv.welcome.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("hdfv.welcome.handbook", open));
        player.sendSystemMessage(Component.translatable("hdfv.welcome.advancements", key("key.advancements")));
        player.sendSystemMessage(Component.translatable("hdfv.welcome.keys", key("key.hdfv.shift_up"), key("key.hdfv.shift_down"),
                key("key.hdfv.handbrake"), key("key.hdfv.couple"), key("key.hdfv.atc_call")));
    }

    private static Component key(String mapping) { return Component.keybind(mapping).withStyle(ChatFormatting.YELLOW); }

    // ---------------------------------------------------------------------------------------------------- progress for the handbook

    @SubscribeEvent static void earned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAdvancement().id().getNamespace().equals(Hdfv.MOD_ID)) { sendProgress(player); }
    }

    /** The hdfv advancements a player has done (their ids), for the handbook's ticks. */
    static List<String> done(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (AdvancementHolder h : player.server.getAdvancements().getAllAdvancements()) {
            if (h.id().getNamespace().equals(Hdfv.MOD_ID) && player.getAdvancements().getOrStartProgress(h).isDone()) { out.add(h.id().toString()); }
        }
        return out;
    }

    static void sendProgress(ServerPlayer player) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer || player.connection == null) { return; }
        if (player.connection.hasChannel(ProgressPayload.TYPE)) { PacketDistributor.sendToPlayer(player, new ProgressPayload(done(player))); }
    }

    // ---------------------------------------------------------------------------------------------------- network and command

    /** The server asks the client to open the handbook (at an entry, or where it was: ""). */
    public record OpenPayload(String entry) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "open_handbook"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), OpenPayload::entry, OpenPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The hdfv advancements the player has done. */
    public record ProgressPayload(List<String> done) implements CustomPacketPayload {
        public static final Type<ProgressPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "tutorial_progress"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(1024)), ProgressPayload::done, ProgressPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, context) -> ClientAccess.openHandbook(payload.entry()));
        registrar.playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC, (payload, context) -> ClientAccess.handbookProgress(payload.done()));
    }

    /** /hdfv handbook [entry]: opens the handbook (the welcome's link runs it). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("handbook")
                .executes(c -> open(c.getSource(), ""))
                .then(Commands.argument("entry", StringArgumentType.word()).executes(c -> open(c.getSource(), StringArgumentType.getString(c, "entry"))));
    }

    private static int open(CommandSourceStack source, String entry) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new OpenPayload(entry));
        return 1;
    }
}
