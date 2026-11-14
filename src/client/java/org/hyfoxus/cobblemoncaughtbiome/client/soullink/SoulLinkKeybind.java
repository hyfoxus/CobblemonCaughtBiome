package org.hyfoxus.cobblemoncaughtbiome.client.soullink;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class SoulLinkKeybind {
    private static KeyMapping key;

    private SoulLinkKeybind() { }

    public static void initialize() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemoncaughtbiome.soullink_menu",
                GLFW.GLFW_KEY_J, "key.categories.cobblemoncaughtbiome"));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.screen != null || minecraft.player == null) return;
            while (key.consumeClick()) minecraft.player.connection.sendCommand("soullink menu");
        });
    }
}
