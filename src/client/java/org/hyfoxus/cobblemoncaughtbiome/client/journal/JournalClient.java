package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.hyfoxus.cobblemoncaughtbiome.network.JournalPayload;

public final class JournalClient {
    private static UUID snapshot;
    private static int nextChunk;
    private static final List<CompoundTag> rows = new ArrayList<>();

    private JournalClient() { }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(JournalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().getConnection() == null) return;
                    CompoundTag data = payload.data();
                    int chunk = data.getInt("chunk");
                    if (chunk == 0) {
                        reset();
                        snapshot = data.getUUID("snapshot");
                    }
                    if (snapshot == null || !snapshot.equals(data.getUUID("snapshot")) || chunk != nextChunk) return;
                    for (Tag row : data.getList("rows", Tag.TAG_COMPOUND)) rows.add(((CompoundTag) row).copy());
                    nextChunk++;
                    if (nextChunk == data.getInt("chunks")) {
                        if (context.client().screen instanceof BiomeJournalScreen screen) {
                            screen.update(data, List.copyOf(rows));
                        } else {
                            context.client().setScreen(new BiomeJournalScreen(data, List.copyOf(rows)));
                        }
                        reset();
                    }
                }));
    }

    private static void reset() {
        snapshot = null;
        nextChunk = 0;
        rows.clear();
    }
}
