package org.hyfoxus.cobblemoncaughtbiome.soullink;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

final class SoulLinkMessages {
    static final int TEXT = SoulLinkColors.TEXT;
    static final int ACCENT = SoulLinkColors.GOLD;
    static final int TEAL = SoulLinkColors.TEAL;
    static final int WARNING = SoulLinkColors.WARNING;

    private SoulLinkMessages() { }

    static MutableComponent text(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(TEXT));
    }

    static MutableComponent highlight(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ACCENT));
    }

    static MutableComponent line(Component content) {
        return Component.literal("Soul Link  ›  ").withStyle(style -> style.withColor(TEAL))
                .append(content);
    }

    static MutableComponent button(String label, String command, int color, String tooltip) {
        return Component.literal("[ " + label + " ]").withStyle(style -> style.withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(tooltip))));
    }
}
