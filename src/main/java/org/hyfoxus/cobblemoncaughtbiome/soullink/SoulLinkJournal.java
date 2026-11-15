package org.hyfoxus.cobblemoncaughtbiome.soullink;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.hyfoxus.cobblemoncaughtbiome.RunLabel;
import org.hyfoxus.cobblemoncaughtbiome.network.JournalPayload;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkLogPayload;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Capture;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Run;

final class SoulLinkJournal {
    private static final int ROWS_PER_CHUNK = 32;

    private SoulLinkJournal() { }

    static void initialize() {
        PayloadTypeRegistry.playS2C().register(JournalPayload.TYPE, JournalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoulLinkLogPayload.TYPE, SoulLinkLogPayload.CODEC);
    }

    static int openLogs(ServerPlayer player) {
        SoulLinkData data = SoulLinkData.get(player.server);
        Run run = data.activeRun(player.getUUID());
        if (run == null) { SoulLinkManager.message(player, "You are not in a Soul Link run."); return 0; }
        if (!ServerPlayNetworking.canSend(player, SoulLinkLogPayload.TYPE)) {
            SoulLinkManager.message(player, "Install the matching client mod to open the Soul Link log.");
            return 0;
        }
        UUID snapshot = UUID.randomUUID();
        int chunks = Math.max(1, (run.log.size() + ROWS_PER_CHUNK - 1) / ROWS_PER_CHUNK);
        for (int chunk = 0; chunk < chunks; chunk++) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("snapshot", snapshot);
            tag.putInt("chunk", chunk);
            tag.putInt("chunks", chunks);
            ListTag entries = new ListTag();
            for (int i = chunk * ROWS_PER_CHUNK; i < Math.min(run.log.size(), (chunk + 1) * ROWS_PER_CHUNK); i++) {
                entries.add(StringTag.valueOf(run.log.get(i)));
            }
            tag.put("entries", entries);
            ServerPlayNetworking.send(player, new SoulLinkLogPayload(tag));
        }
        return 1;
    }

    static int openMenu(ServerPlayer player) {
        return SoulLinkData.get(player.server).activeRun(player.getUUID()) != null
                ? open(player, "all", 1, true) : SoulLinkMenu.openOnline(player, true);
    }

    static int open(ServerPlayer player, String filter, int page, boolean forceOpen) {
        SoulLinkData data = SoulLinkData.get(player.server);
        Run run = data.activeRun(player.getUUID());
        if (run == null) { SoulLinkManager.message(player, "You are not in a Soul Link run."); return 0; }
        if (!ServerPlayNetworking.canSend(player, JournalPayload.TYPE)) {
            if (forceOpen) SoulLinkManager.message(player, "Install the matching client mod to open the biome journal.");
            return 0;
        }
        backfillPortraits(player, data, run);
        backfillRunLabels(player, run);
        List<ResourceLocation> biomes = new ArrayList<>(player.server.registryAccess().registryOrThrow(Registries.BIOME).keySet());
        for (Capture capture : run.captures) {
            ResourceLocation biome = ResourceLocation.tryParse(capture.biome());
            if (biome != null && !biomes.contains(biome)) biomes.add(biome);
        }
        biomes.sort(Comparator.comparing(ResourceLocation::toString));
        UUID snapshot = UUID.randomUUID();
        int chunks = Math.max(1, (biomes.size() + ROWS_PER_CHUNK - 1) / ROWS_PER_CHUNK);
        for (int chunk = 0; chunk < chunks; chunk++) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("snapshot", snapshot);
            tag.putInt("chunk", chunk);
            tag.putInt("chunks", chunks);
            tag.putString("firstName", run.firstName);
            tag.putString("secondName", run.solo() ? run.firstName : run.secondName);
            tag.putString("runId", run.id.toString());
            tag.putString("runTitle", run.title);
            tag.putBoolean("solo", run.solo());
            tag.putBoolean("paused", run.paused);
            tag.putString("filter", filter);
            tag.putInt("page", page);
            tag.putBoolean("forceOpen", forceOpen);
            ListTag rows = new ListTag();
            for (int i = chunk * ROWS_PER_CHUNK; i < Math.min(biomes.size(), (chunk + 1) * ROWS_PER_CHUNK); i++) {
                String biome = biomes.get(i).toString();
                CompoundTag row = new CompoundTag();
                row.putString("biome", biome);
                row.putString("theme", theme(player, biomes.get(i)));
                Capture first = run.capture(run.first, biome);
                if (first != null) row.put("first", first.toTag());
                if (!run.solo()) {
                    Capture second = run.capture(run.second, biome);
                    if (second != null) row.put("second", second.toTag());
                }
                rows.add(row);
            }
            tag.put("rows", rows);
            ServerPlayNetworking.send(player, new JournalPayload(tag));
        }
        return 1;
    }

    private static void backfillPortraits(ServerPlayer viewer, SoulLinkData data, Run run) {
        for (int i = 0; i < run.captures.size(); i++) {
            Capture capture = run.captures.get(i);
            if (!capture.species().isEmpty()) continue;
            var storage = Cobblemon.INSTANCE.getStorage();
            Pokemon pokemon = storage.getParty(capture.player(), viewer.server.registryAccess()).get(capture.pokemon());
            if (pokemon == null) pokemon = storage.getPC(capture.player(), viewer.server.registryAccess()).get(capture.pokemon());
            if (pokemon != null) {
                run.captures.set(i, capture.withAppearance(pokemon));
                data.setDirty();
            }
        }
    }

    private static void backfillRunLabels(ServerPlayer viewer, Run run) {
        var storage = Cobblemon.INSTANCE.getStorage();
        for (Capture capture : run.captures) {
            Pokemon pokemon = storage.getParty(capture.player(), viewer.server.registryAccess()).get(capture.pokemon());
            if (pokemon == null) pokemon = storage.getPC(capture.player(), viewer.server.registryAccess()).get(capture.pokemon());
            // persistentData changes alone do not mark the store dirty, so a new stamp needs onChange to be saved.
            if (pokemon != null && RunLabel.stampIfAbsent(pokemon.getPersistentData(), run.id, run.title))
                pokemon.onChange(null);
        }
    }

    private static String theme(ServerPlayer viewer, ResourceLocation biome) {
        var holder = viewer.server.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, biome));
        if (holder.isEmpty()) return "grassland";
        var value = holder.get();
        if (value.is(BiomeTags.IS_NETHER)) return "nether";
        if (value.is(BiomeTags.IS_END)) return "end";
        if (value.is(BiomeTags.IS_OCEAN) || value.is(BiomeTags.IS_RIVER)) return "ocean";
        if (value.value().getBaseTemperature() < 0.2F) return "snow";
        if (value.is(BiomeTags.IS_FOREST) || value.is(BiomeTags.IS_TAIGA) || value.is(BiomeTags.IS_JUNGLE)) return "forest";
        if ((!value.value().hasPrecipitation() && value.value().getBaseTemperature() > 1F)
                || value.is(BiomeTags.IS_BEACH) || value.is(BiomeTags.IS_BADLANDS)) return "desert";
        return "grassland";
    }
}
