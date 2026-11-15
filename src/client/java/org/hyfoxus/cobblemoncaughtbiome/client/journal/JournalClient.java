package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.hyfoxus.cobblemoncaughtbiome.network.JournalPayload;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkInvitePayload;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkLogPayload;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkOnlinePayload;

public final class JournalClient {
    private static UUID snapshot;
    private static int nextChunk;
    private static final List<CompoundTag> rows = new ArrayList<>();
    private static UUID logSnapshot;
    private static int logNextChunk;
    private static final List<String> logEntries = new ArrayList<>();

    private JournalClient() { }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            BiomeJournalScreen.clearPendingLogs();
        });
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
                        boolean forceOpen = data.getBoolean("forceOpen");
                        var screen = context.client().screen;
                        // Live (non-forced) pushes only refresh an open biome view; they never pull the
                        // player out of the Runs view. Forced payloads open or replace the screen.
                        if (screen instanceof BiomeJournalScreen bjs && bjs.isBiomeMode()) {
                            bjs.update(data, List.copyOf(rows));
                        } else if (forceOpen) {
                            context.client().setScreen(new BiomeJournalScreen(data, List.copyOf(rows)));
                        }
                        reset();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SoulLinkOnlinePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().getConnection() == null) return;
                    CompoundTag data = payload.data();
                    boolean forceOpen = data.getBoolean("forceOpen");
                    var screen = context.client().screen;
                    // Live (non-forced) pushes only refresh an open Runs view; they never pull the
                    // player out of the biome view. Forced payloads open or replace the screen.
                    if (screen instanceof BiomeJournalScreen bjs && bjs.isRunsMode()) {
                        bjs.updateOnline(data);
                    } else if (forceOpen || (screen instanceof BiomeJournalScreen
                            && !data.contains("selected", Tag.TAG_STRING))) {
                        // A biome view with no selected run is never valid (its run was ended): show the Runs view.
                        if (!data.contains("selected", Tag.TAG_STRING)) BiomeJournalScreen.clearPendingLogs();
                        context.client().setScreen(new BiomeJournalScreen(data));
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SoulLinkLogPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().getConnection() == null) return;
                    CompoundTag data = payload.data();
                    int chunk = data.getInt("chunk");
                    if (chunk == 0) {
                        logSnapshot = data.getUUID("snapshot");
                        logEntries.clear();
                        logNextChunk = 0;
                    }
                    if (logSnapshot == null || !logSnapshot.equals(data.getUUID("snapshot")) || chunk != logNextChunk) return;
                    for (Tag entry : data.getList("entries", Tag.TAG_STRING)) logEntries.add(entry.getAsString());
                    logNextChunk++;
                    if (logNextChunk == data.getInt("chunks")) {
                        if (context.client().screen instanceof BiomeJournalScreen screen && screen.isBiomeMode()) {
                            screen.updateLogs(List.copyOf(logEntries));
                        }
                        logSnapshot = null;
                        logNextChunk = 0;
                        logEntries.clear();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SoulLinkInvitePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().getConnection() == null) return;
                    var screen = context.client().screen;
                    if (screen == null || screen instanceof InvitePopupScreen) {
                        CompoundTag data = payload.data();
                        context.client().setScreen(new InvitePopupScreen(data.getString("inviter"),
                                data.getString("token"), data.getInt("ticksRemaining")));
                    }
                }));
    }

    private static void reset() {
        snapshot = null;
        nextChunk = 0;
        rows.clear();
        logSnapshot = null;
        logNextChunk = 0;
        logEntries.clear();
    }
}
