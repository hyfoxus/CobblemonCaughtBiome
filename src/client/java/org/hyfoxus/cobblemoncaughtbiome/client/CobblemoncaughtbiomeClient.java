package org.hyfoxus.cobblemoncaughtbiome.client;

import net.fabricmc.api.ClientModInitializer;
import org.hyfoxus.cobblemoncaughtbiome.client.journal.JournalClient;
import org.hyfoxus.cobblemoncaughtbiome.client.soullink.SoulLinkKeybind;

public class CobblemoncaughtbiomeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JournalClient.initialize();
        SoulLinkKeybind.initialize();
    }
}
