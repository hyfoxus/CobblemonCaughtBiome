package org.hyfoxus.cobblemoncaughtbiome.soullink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SoulLinkData extends SavedData {
    private static final Factory<SoulLinkData> FACTORY = new Factory<>(SoulLinkData::new, SoulLinkData::load, null);
    final List<Run> runs = new ArrayList<>();
    final Map<UUID, List<String>> notifications = new LinkedHashMap<>();

    public static SoulLinkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "cobblemoncaughtbiome_soullink");
    }

    Run findRun(UUID player) {
        return runs.stream().filter(run -> run.contains(player)).findFirst().orElse(null);
    }

    private static SoulLinkData load(CompoundTag root, HolderLookup.Provider registries) {
        SoulLinkData data = new SoulLinkData();
        for (Tag entry : root.getList("runs", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) entry;
            Run run = new Run(tag.getUUID("first"), tag.getString("firstName"),
                    tag.getUUID("second"), tag.getString("secondName"));
            run.paused = tag.getBoolean("paused");
            for (Tag item : tag.getList("captures", Tag.TAG_COMPOUND)) {
                CompoundTag capture = (CompoundTag) item;
                Set<String> aspects = new HashSet<>();
                capture.getList("aspects", Tag.TAG_STRING).forEach(aspect -> aspects.add(aspect.getAsString()));
                run.captures.add(new Capture(capture.getUUID("player"), capture.getUUID("pokemon"),
                        capture.getString("name"), capture.getString("biome"), capture.getString("species"), aspects));
            }
            data.runs.add(run);
        }
        for (Tag entry : root.getList("notifications", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) entry;
            data.notifications.computeIfAbsent(tag.getUUID("player"), ignored -> new ArrayList<>())
                    .add(tag.getString("message"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag savedRuns = new ListTag();
        for (Run run : runs) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("first", run.first);
            tag.putString("firstName", run.firstName);
            tag.putUUID("second", run.second);
            tag.putString("secondName", run.secondName);
            tag.putBoolean("paused", run.paused);
            ListTag captures = new ListTag();
            for (Capture capture : run.captures) {
                captures.add(capture.toTag());
            }
            tag.put("captures", captures);
            savedRuns.add(tag);
        }
        root.put("runs", savedRuns);
        ListTag messages = new ListTag();
        notifications.forEach((player, pending) -> pending.forEach(message -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putString("message", message);
            messages.add(tag);
        }));
        root.put("notifications", messages);
        return root;
    }

    record Capture(UUID player, UUID pokemon, String name, String biome, String species, Set<String> aspects) {
        Capture { aspects = Set.copyOf(aspects); }

        Capture withAppearance(Pokemon value) {
            return new Capture(player, pokemon, name, biome, value.getSpecies().getResourceIdentifier().toString(), value.getAspects());
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putUUID("pokemon", pokemon);
            tag.putString("name", name);
            tag.putString("biome", biome);
            tag.putString("species", species);
            ListTag savedAspects = new ListTag();
            aspects.stream().sorted().forEach(aspect -> savedAspects.add(StringTag.valueOf(aspect)));
            tag.put("aspects", savedAspects);
            return tag;
        }
    }

    static final class Run {
        final UUID first;
        final UUID second;
        String firstName;
        String secondName;
        boolean paused;
        final List<Capture> captures = new ArrayList<>();

        Run(UUID first, String firstName, UUID second, String secondName) {
            this.first = first;
            this.firstName = firstName;
            this.second = second;
            this.secondName = secondName;
        }

        boolean contains(UUID player) { return first.equals(player) || second.equals(player); }
        UUID partner(UUID player) { return first.equals(player) ? second : first; }
        String name(UUID player) { return first.equals(player) ? firstName : secondName; }
        Capture capture(UUID player, String biome) {
            return captures.stream().filter(c -> c.player().equals(player) && c.biome().equals(biome))
                    .findFirst().orElse(null);
        }
    }
}
