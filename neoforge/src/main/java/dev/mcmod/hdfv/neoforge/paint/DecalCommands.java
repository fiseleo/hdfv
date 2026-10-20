package dev.mcmod.hdfv.neoforge.paint;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Operators' commands for the stickers' pictures (0.13): /hdfv decal list [player], remove &lt;name&gt; (the SHA-256 or
 * a prefix of at least 8 of its digits naming one picture), purge &lt;player&gt; (everything they uploaded, where nobody
 * else did too). A removed picture is told to every client: stickers showing it go blank.
 */
public final class DecalCommands {
    private DecalCommands() {}

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("decal").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c, null))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> list(c, GameProfileArgument.getGameProfiles(c, "player")))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(DecalCommands::remove)))
                .then(Commands.literal("purge").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(DecalCommands::purge)));
    }

    private static int list(CommandContext<CommandSourceStack> c, Collection<GameProfile> players) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        List<UUID> who = players == null ? null : players.stream().map(GameProfile::getId).toList();
        List<DecalIndex.Entry> entries = DecalStore.list(s.getServer(), null).stream().filter(e -> who == null || e.users().stream().anyMatch(who::contains)).toList();
        s.sendSuccess(() -> Component.translatableWithFallback("hdfv.decal.command.count", "%s sticker pictures", entries.size()), false);
        for (DecalIndex.Entry e : entries) {
            s.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s  %dx%d  %.0f KB  %s  %s%s", e.sha().substring(0, 12), e.width(), e.height(), e.bytes() / 1024.0,
                    e.firstName(), WHEN.format(Instant.ofEpochMilli(e.time())), e.users().size() > 1 ? "  (+" + (e.users().size() - 1) + ")" : "")), false);
        }
        return entries.size();
    }

    private static int remove(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        List<DecalIndex.Entry> matches = name.length() >= 8 && name.matches("[0-9a-f]+") ? DecalIndex.of(s.getServer()).starting(name) : List.of();
        if (matches.size() != 1) {
            s.sendFailure(Component.translatableWithFallback("hdfv.decal.command.which", "%s pictures match %s; give at least 8 digits of one", matches.size(), name));
            return 0;
        }
        String sha = matches.get(0).sha();
        DecalStore.remove(s.getServer(), sha);
        DecalNetwork.gone(sha);
        s.sendSuccess(() -> Component.translatableWithFallback("hdfv.decal.command.removed", "Removed sticker picture %s", sha.substring(0, 12)), true);
        return 1;
    }

    private static int purge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        int total = 0;
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            List<String> before = DecalStore.list(s.getServer(), null).stream().map(DecalIndex.Entry::sha).toList();
            int n = DecalStore.purge(s.getServer(), p.getId());
            for (String sha : before) { if (DecalIndex.of(s.getServer()).get(sha) == null) { DecalNetwork.gone(sha); } }
            total += n;
            s.sendSuccess(() -> Component.translatableWithFallback("hdfv.decal.command.purged", "Took %s off %s sticker pictures", p.getName(), n), true);
        }
        return total;
    }
}
