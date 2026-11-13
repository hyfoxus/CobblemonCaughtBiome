package org.hyfoxus.cobblemoncaughtbiome.client;

import net.fabricmc.api.ClientModInitializer;
import org.hyfoxus.cobblemoncaughtbiome.client.journal.JournalClient;

public class CobblemoncaughtbiomeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        JournalClient.initialize();
    }
}
