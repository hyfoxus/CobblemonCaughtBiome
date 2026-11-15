package org.hyfoxus.cobblemoncaughtbiome.soullink;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.hyfoxus.cobblemoncaughtbiome.BiomeNames;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Capture;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Run;

final class SoulLinkCommands {

    private SoulLinkCommands() { }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("soullink")
                        .executes(context -> help(context.getSource()))
                        .then(Commands.literal("accept").executes(context ->
                                SoulLinkManager.accept(context.getSource().getPlayerOrException()) ? 1 : 0)
                                .then(Commands.argument("invitation", StringArgumentType.word()).executes(context ->
                                        SoulLinkManager.accept(context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "invitation")) ? 1 : 0)))
                        .then(Commands.literal("decline").executes(context ->
                                SoulLinkManager.decline(context.getSource().getPlayerOrException(), null) ? 1 : 0)
                                .then(Commands.argument("invitation", StringArgumentType.word()).executes(context ->
                                        SoulLinkManager.decline(context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "invitation")) ? 1 : 0)))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("stop").executes(context ->
                                SoulLinkManager.setPaused(context.getSource().getPlayerOrException(), true) ? 1 : 0))
                        .then(Commands.literal("resume").executes(context ->
                                SoulLinkManager.setPaused(context.getSource().getPlayerOrException(), false) ? 1 : 0))
                        .then(biomeCommand())
                        .then(Commands.literal("online").executes(context ->
                                SoulLinkMenu.openOnline(context.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("logs").executes(context ->
                                SoulLinkJournal.openLogs(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("menu").executes(context ->
                                SoulLinkJournal.openMenu(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("end")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> SoulLinkMessages.line(SoulLinkMessages.text(
                                            "Ending clears both players' pair tracking and biome progress. Your Pokémon are kept. ")
                                            .append(SoulLinkMessages.button("End run", "/soullink end confirm",
                                                    SoulLinkMessages.WARNING, "Permanently end this run"))), false);
                                    return 1;
                                })
                                .then(Commands.literal("confirm").executes(context -> {
                                    SoulLinkManager.end(context.getSource().getPlayerOrException());
                                    return 1;
                                })))
                        .then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getServer().getPlayerList().getPlayers().stream()
                                                .filter(player -> player != context.getSource().getPlayer())
                                                .map(player -> player.getGameProfile().getName()), builder))
                                .executes(context -> {
                                    ServerPlayer inviter = context.getSource().getPlayerOrException();
                                    ServerPlayer target = context.getSource().getServer().getPlayerList()
                                            .getPlayerByName(StringArgumentType.getString(context, "player"));
                                    if (target == null) {
                                        SoulLinkManager.message(inviter, "Choose an online player by name.");
                                        return 0;
                                    }
                                    return SoulLinkManager.invite(inviter, target) ? 1 : 0;
                                })))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> SoulLinkMessages.line(SoulLinkMessages.text(
                "/soullink invite <player>, accept, decline, status, online, biomes [all|available|waiting|linked] [page], logs, stop, resume, end")), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Run run = SoulLinkData.get(source.getServer()).findRun(player.getUUID());
        if (run == null) { SoulLinkManager.message(player, "You are not in a Soul Link run."); return 0; }
        SoulLinkManager.message(player, (run.paused ? "Paused" : "Active") + " — Partner: "
                + run.name(run.partner(player.getUUID())));
        int pairs = 0;
        for (Capture capture : run.captures) {
            if (!capture.player().equals(run.first)) continue;
            Capture other = run.capture(run.second, capture.biome());
            if (other == null) continue;
            pairs++;
            player.sendSystemMessage(SoulLinkMessages.text(run.firstName + "'s " + capture.name() + " ↔ "
                    + run.secondName + "'s " + other.name() + " — ").append(BiomeNames.display(capture.biome())));
        }
        SoulLinkManager.message(player, pairs + " linked pair(s). Use /soullink biomes for waiting catches.");
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> biomeCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("biomes")
                .executes(context -> biomes(context.getSource(), 1, BiomeFilter.ALL))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> biomes(context.getSource(), IntegerArgumentType.getInteger(context, "page"), BiomeFilter.ALL)));
        for (BiomeFilter filter : BiomeFilter.values()) {
            command.then(Commands.literal(filter.name().toLowerCase(Locale.ROOT))
                    .executes(context -> biomes(context.getSource(), 1, filter))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> biomes(context.getSource(), IntegerArgumentType.getInteger(context, "page"), filter))));
        }
        return command;
    }

    private static int biomes(CommandSourceStack source, int page, BiomeFilter filter) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return SoulLinkJournal.open(source.getPlayerOrException(), filter.name().toLowerCase(Locale.ROOT), page, true);
    }

    private enum BiomeFilter { ALL, AVAILABLE, WAITING, LINKED }
}
