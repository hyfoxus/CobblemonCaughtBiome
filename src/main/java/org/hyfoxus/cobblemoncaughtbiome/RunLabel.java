package org.hyfoxus.cobblemoncaughtbiome;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class RunLabel {
    public static final String ID_KEY = "cobblemoncaughtbiome:run_id";
    public static final String TITLE_KEY = "cobblemoncaughtbiome:run_title";
    public static final int MAX_TITLE = 30;

    private RunLabel() {
    }

    public static void store(CompoundTag data, UUID runId, String title) {
        data.putString(ID_KEY, runId.toString());
        data.putString(TITLE_KEY, title);
    }

    public static boolean stampIfAbsent(CompoundTag data, UUID runId, String title) {
        if (read(data).isPresent()) return false;
        store(data, runId, title);
        return true;
    }

    public static Optional<String> read(CompoundTag data) {
        String title = data.getString(TITLE_KEY);
        return title.isBlank() ? Optional.empty() : Optional.of(title);
    }

    public static String shorten(String title) {
        if (title.length() <= MAX_TITLE) return title;
        return title.substring(0, MAX_TITLE - 1) + "…";
    }

    public static Optional<Component> display(CompoundTag data) {
        return read(data).map(title -> Component.translatableWithFallback(
                "cobblemoncaughtbiome.run", "Run: %s", shorten(title)));
    }
}
