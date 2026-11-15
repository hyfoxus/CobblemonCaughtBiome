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
    final Map<UUID, UUID> selected = new LinkedHashMap<>();
    final Map<UUID, List<String>> notifications = new LinkedHashMap<>();

    public static SoulLinkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "cobblemoncaughtbiome_soullink");
    }

    Run activeRun(UUID player) {
        UUID id = selected.get(player);
        Run run = id == null ? null : run(id);
        return run != null && run.contains(player) ? run : null;
    }

    List<Run> runsOf(UUID player) {
        return runs.stream().filter(run -> run.contains(player)).toList();
    }

    Run run(UUID id) {
        return runs.stream().filter(run -> run.id.equals(id)).findFirst().orElse(null);
    }

    Run createSolo(UUID player, String name) {
        Run run = new Run(UUID.randomUUID(), uniqueTitle("Solo"), player, name, player, name);
        return add(run);
    }

    Run createShared(UUID first, String firstName, UUID second, String secondName) {
        Run run = new Run(UUID.randomUUID(), uniqueTitle(firstName + " & " + secondName),
                first, firstName, second, secondName);
        return add(run);
    }

    private Run add(Run run) {
        runs.add(run);
        for (UUID member : run.members()) selected.put(member, run.id);
        setDirty();
        return run;
    }

    void select(UUID player, Run run) {
        if (!run.contains(player)) throw new IllegalArgumentException("Player is not a member of this run.");
        selected.put(player, run.id);
        setDirty();
    }

    void clearSelection(UUID player) {
        if (selected.remove(player) != null) setDirty();
    }

    void remove(Run run) {
        runs.remove(run);
        selected.values().removeIf(id -> id.equals(run.id));
        setDirty();
    }

    static UUID parseRunId(String text) {
        if (text == null) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String uniqueTitle(String base) {
        String title = base;
        for (int n = 2; hasTitle(title); n++) title = base + " #" + n;
        return title;
    }

    private boolean hasTitle(String title) {
        return runs.stream().anyMatch(run -> run.title.equals(title));
    }

    static SoulLinkData load(CompoundTag root, HolderLookup.Provider registries) {
        SoulLinkData data = new SoulLinkData();
        for (Tag entry : root.getList("runs", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) entry;
            UUID first = tag.getUUID("first");
            UUID second = tag.getUUID("second");
            String firstName = tag.getString("firstName");
            String secondName = tag.getString("secondName");
            UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
            String title = tag.contains("title", Tag.TAG_STRING) ? tag.getString("title")
                    : data.uniqueTitle(first.equals(second) ? "Solo" : firstName + " & " + secondName);
            Run run = new Run(id, title, first, firstName, second, secondName);
            run.paused = tag.getBoolean("paused");
            for (Tag item : tag.getList("captures", Tag.TAG_COMPOUND)) {
                CompoundTag capture = (CompoundTag) item;
                Set<String> aspects = new HashSet<>();
                capture.getList("aspects", Tag.TAG_STRING).forEach(aspect -> aspects.add(aspect.getAsString()));
                run.captures.add(new Capture(capture.getUUID("player"), capture.getUUID("pokemon"),
                        capture.getString("name"), capture.getString("biome"), capture.getString("species"), aspects));
            }
            for (Tag logEntry : tag.getList("log", Tag.TAG_STRING)) run.log.add(logEntry.getAsString());
            data.runs.add(run);
        }
        for (Tag entry : root.getList("selected", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) entry;
            UUID player = tag.getUUID("player");
            Run run = data.run(tag.getUUID("run"));
            if (run != null && run.contains(player)) data.selected.put(player, run.id);
        }
        // Old saves have no "selected" list: default everyone to their first run. New saves always write
        // the list (possibly empty), so an absent selection there means free play and must be kept.
        if (!root.contains("selected", Tag.TAG_LIST)) {
            for (Run run : data.runs) {
                for (UUID member : run.members()) data.selected.putIfAbsent(member, run.id);
            }
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
            tag.putUUID("id", run.id);
            tag.putString("title", run.title);
            tag.putBoolean("solo", run.solo());
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
            ListTag log = new ListTag();
            run.log.forEach(entry -> log.add(StringTag.valueOf(entry)));
            tag.put("log", log);
            savedRuns.add(tag);
        }
        root.put("runs", savedRuns);
        ListTag savedSelection = new ListTag();
        selected.forEach((player, runId) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putUUID("run", runId);
            savedSelection.add(tag);
        });
        root.put("selected", savedSelection);
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
        final UUID id;
        final String title;
        final UUID first;
        final UUID second;
        String firstName;
        String secondName;
        boolean paused;
        final List<Capture> captures = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        Run(UUID id, String title, UUID first, String firstName, UUID second, String secondName) {
            this.id = id;
            this.title = title;
            this.first = first;
            this.firstName = firstName;
            this.second = second;
            this.secondName = secondName;
        }

        boolean solo() { return first.equals(second); }
        List<UUID> members() { return solo() ? List.of(first) : List.of(first, second); }
        boolean contains(UUID player) { return first.equals(player) || second.equals(player); }
        UUID partner(UUID player) { return first.equals(player) ? second : first; }
        String name(UUID player) { return first.equals(player) ? firstName : secondName; }
        Capture capture(UUID player, String biome) {
            return captures.stream().filter(c -> c.player().equals(player) && c.biome().equals(biome))
                    .findFirst().orElse(null);
        }
    }
}
