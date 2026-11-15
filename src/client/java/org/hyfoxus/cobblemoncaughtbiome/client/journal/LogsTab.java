package org.hyfoxus.cobblemoncaughtbiome.client.journal;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkColors;

final class LogsTab {
    private static final int ROW_HEIGHT = 16;
    private static final int GAP = 4;
    private static final int PARCHMENT = 0xFF2B2117;

    private Font font;
    private List<String> entries = List.of();
    private int left, panelWidth, contentTop, contentBottom;
    private double scroll;

    void setFont(Font font) { this.font = font; }

    void setLayout(int left, int panelWidth, int contentTop, int contentBottom) {
        this.left = left;
        this.panelWidth = panelWidth;
        this.contentTop = contentTop;
        this.contentBottom = contentBottom;
    }

    void setEntries(List<String> entries) {
        this.entries = entries;
        scroll = Math.min(scroll, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * (ROW_HEIGHT + GAP) - GAP - (contentBottom - contentTop));
    }

    boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseY >= contentTop && mouseY <= contentBottom && mouseX >= left && mouseX <= left + panelWidth) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * 20));
            return true;
        }
        return false;
    }

    void renderBackgrounds(GuiGraphics graphics) {
        if (entries.isEmpty()) return;
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = contentTop + i * (ROW_HEIGHT + GAP) - (int) scroll;
            if (y + ROW_HEIGHT < contentTop || y > contentBottom) continue;
            graphics.fill(left + 14, y, left + panelWidth - 14, y + ROW_HEIGHT, PARCHMENT);
        }
        graphics.disableScissor();
    }

    void renderLabels(GuiGraphics graphics) {
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, "No entries yet.", left + panelWidth / 2, contentTop + 10, SoulLinkColors.TEXT);
            return;
        }
        graphics.enableScissor(left + 12, contentTop, left + panelWidth - 9, contentBottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = contentTop + i * (ROW_HEIGHT + GAP) - (int) scroll;
            if (y + ROW_HEIGHT < contentTop || y > contentBottom) continue;
            Component text = Component.literal(fit(entries.get(i), panelWidth - 36)).setStyle(Style.EMPTY.withItalic(true));
            graphics.drawString(font, text, left + 20, y + 4, SoulLinkColors.TEXT);
        }
        graphics.disableScissor();
    }

    void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) return;
        int x = left + panelWidth - 7;
        int track = contentBottom - contentTop;
        int thumb = Math.max(16, track * track / (track + maxScroll()));
        int y = contentTop + (int) ((track - thumb) * scroll / maxScroll());
        graphics.fill(x, contentTop, x + 2, contentBottom, 0xFF293A46);
        graphics.fill(x, y, x + 2, y + thumb, 0xFF000000 | SoulLinkColors.TEAL);
    }

    private String fit(String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }
}
